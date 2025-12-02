package com.enterprise.ai.data.service;

import com.enterprise.ai.data.entity.RoleScenarioMap;
import com.enterprise.ai.data.repository.RoleScenarioMapRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service for managing RBAC role-scenario mappings.
 * Critical for security - controls which roles can access which scenarios.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RbacManagementService {

    private final RoleScenarioMapRepository repository;

    /**
     * Get all role-scenario mappings (cached).
     */
    @Cacheable(value = "rbacMappings", key = "'all'")
    public List<RoleScenarioMap> getAllMappings() {
        log.debug("Fetching all RBAC mappings from database");
        return repository.findAll();
    }

    /**
     * Get scenarios accessible by a role (cached).
     */
    @Cacheable(value = "rbacMappings", key = "'role:' + #roleName")
    public List<String> getScenariosByRole(String roleName) {
        log.debug("Fetching scenarios for role: {}", roleName);
        return repository.findByRoleName(roleName)
                .stream()
                .map(RoleScenarioMap::getScenarioCode)
                .collect(Collectors.toList());
    }

    /**
     * Get roles that can access a scenario (cached).
     */
    @Cacheable(value = "rbacMappings", key = "'scenario:' + #scenarioCode")
    public List<String> getRolesByScenario(String scenarioCode) {
        log.debug("Fetching roles for scenario: {}", scenarioCode);
        return repository.findByScenarioCode(scenarioCode)
                .stream()
                .map(RoleScenarioMap::getRoleName)
                .collect(Collectors.toList());
    }

    /**
     * Get distinct role names.
     */
    public List<String> getDistinctRoles() {
        return repository.findDistinctRoleNames();
    }

    /**
     * Get distinct scenario codes.
     */
    public List<String> getDistinctScenarios() {
        return repository.findDistinctScenarioCodes();
    }

    /**
     * Grant role access to scenario (evicts cache).
     */
    @Transactional
    @CacheEvict(value = "rbacMappings", allEntries = true)
    public RoleScenarioMap grantAccess(String roleName, String scenarioCode) {
        log.info("Granting access: role={}, scenario={}", roleName, scenarioCode);

        // Check if mapping already exists
        Optional<RoleScenarioMap> existing = repository.findByRoleNameAndScenarioCode(roleName, scenarioCode);
        if (existing.isPresent()) {
            log.warn("RBAC mapping already exists: role={}, scenario={}", roleName, scenarioCode);
            return existing.get();
        }

        RoleScenarioMap mapping = RoleScenarioMap.builder()
                .roleName(roleName)
                .scenarioCode(scenarioCode)
                .build();

        return repository.save(mapping);
    }

    /**
     * Revoke role access to scenario (evicts cache).
     */
    @Transactional
    @CacheEvict(value = "rbacMappings", allEntries = true)
    public void revokeAccess(Long id) {
        log.info("Revoking access: id={}", id);
        repository.deleteById(id);
    }

    /**
     * Revoke access by role and scenario (evicts cache).
     */
    @Transactional
    @CacheEvict(value = "rbacMappings", allEntries = true)
    public void revokeAccessByRoleAndScenario(String roleName, String scenarioCode) {
        log.info("Revoking access: role={}, scenario={}", roleName, scenarioCode);
        repository.findByRoleNameAndScenarioCode(roleName, scenarioCode)
                .ifPresent(mapping -> repository.deleteById(mapping.getId()));
    }

    /**
     * Bulk assign scenarios to a role (evicts cache).
     */
    @Transactional
    @CacheEvict(value = "rbacMappings", allEntries = true)
    public List<RoleScenarioMap> bulkAssignScenarios(String roleName, List<String> scenarioCodes) {
        log.info("Bulk assigning {} scenarios to role: {}", scenarioCodes.size(), roleName);

        return scenarioCodes.stream()
                .filter(scenarioCode -> repository.findByRoleNameAndScenarioCode(roleName, scenarioCode).isEmpty())
                .map(scenarioCode -> RoleScenarioMap.builder()
                        .roleName(roleName)
                        .scenarioCode(scenarioCode)
                        .build())
                .map(repository::save)
                .collect(Collectors.toList());
    }

    /**
     * Bulk revoke all scenarios from a role (evicts cache).
     */
    @Transactional
    @CacheEvict(value = "rbacMappings", allEntries = true)
    public void bulkRevokeAllFromRole(String roleName) {
        log.info("Bulk revoking all scenarios from role: {}", roleName);
        repository.deleteByRoleName(roleName);
    }

    /**
     * Bulk revoke all roles from a scenario (evicts cache).
     */
    @Transactional
    @CacheEvict(value = "rbacMappings", allEntries = true)
    public void bulkRevokeAllFromScenario(String scenarioCode) {
        log.info("Bulk revoking all roles from scenario: {}", scenarioCode);
        repository.deleteByScenarioCode(scenarioCode);
    }

    /**
     * Check if role has access to scenario (cached via getRolesByScenario).
     */
    public boolean hasAccess(String roleName, String scenarioCode) {
        return repository.findByRoleNameAndScenarioCode(roleName, scenarioCode).isPresent();
    }

    /**
     * Refresh cache manually.
     */
    @CacheEvict(value = "rbacMappings", allEntries = true)
    public void refreshCache() {
        log.info("RBAC mappings cache refreshed");
    }

    /**
     * Get mapping by ID.
     */
    public Optional<RoleScenarioMap> getMappingById(Long id) {
        return repository.findById(id);
    }
}

