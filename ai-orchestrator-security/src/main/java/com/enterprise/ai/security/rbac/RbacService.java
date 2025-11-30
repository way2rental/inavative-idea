package com.enterprise.ai.security.rbac;

import com.enterprise.ai.data.entity.RoleScenarioMap;
import com.enterprise.ai.data.repository.RoleScenarioMapRepository;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Service for Role-Based Access Control.
 * Loads role-scenario mappings from database for dynamic configuration via Admin Panel.
 * Implements caching for performance with scheduled refresh.
 */
@Slf4j
@Service
public class RbacService {

    private final RoleScenarioMapRepository roleScenarioMapRepository;

    // In-memory cache for performance (refreshed periodically)
    private final Map<String, Set<String>> roleScenarioCache = new ConcurrentHashMap<>();

    private volatile boolean initialized = false;

    public RbacService(RoleScenarioMapRepository roleScenarioMapRepository) {
        this.roleScenarioMapRepository = roleScenarioMapRepository;
    }

    /**
     * Initialize the cache from database on startup.
     */
    @PostConstruct
    public void init() {
        try {
            loadRoleMappingsFromDatabase();
            log.info("RBAC Service initialized with {} roles from database", roleScenarioCache.size());
        } catch (Exception e) {
            log.error("Failed to initialize RBAC mappings from database, using empty cache", e);
            loadFallbackMappings();
        }
    }

    /**
     * Load role-scenario mappings from database.
     */
    public void loadRoleMappingsFromDatabase() {
        log.debug("Loading role-scenario mappings from database");

        List<RoleScenarioMap> mappings = roleScenarioMapRepository.findAll();

        if (mappings.isEmpty()) {
            log.warn("No role-scenario mappings found in database! Using fallback mappings.");
            loadFallbackMappings();
            return;
        }

        // Group by role and collect scenario codes (normalized to uppercase)
        Map<String, Set<String>> newCache = mappings.stream()
                .collect(Collectors.groupingBy(
                        RoleScenarioMap::getRoleName,
                        Collectors.mapping(
                                mapping -> mapping.getScenarioCode().toUpperCase(),
                                Collectors.toSet()
                        )
                ));

        roleScenarioCache.clear();
        roleScenarioCache.putAll(newCache);
        initialized = true;

        log.info("Loaded {} role-scenario mappings from database", mappings.size());
        roleScenarioCache.forEach((role, scenarios) ->
            log.debug("Role '{}' has {} allowed scenarios", role, scenarios.size())
        );
    }

    /**
     * Fallback mappings in case database is empty or unavailable.
     * Minimal set to ensure system can function.
     */
    private void loadFallbackMappings() {
        log.warn("Loading fallback RBAC mappings (hardcoded minimal set)");

        roleScenarioCache.clear();

        // Minimal fallback - USER can access basic scenarios
        roleScenarioCache.put("USER", Set.of(
                "TXN_STATUS", "ACCOUNT_SUMMARY", "AMBIGUOUS", "UNKNOWN"
        ));

        // ADMIN has all access
        roleScenarioCache.put("ADMIN", Set.of(
                "TXN_STATUS", "ACCOUNT_SUMMARY", "FILE_STATUS",
                "BATCH_STATUS", "AMBIGUOUS", "UNKNOWN"
        ));

        initialized = true;
        log.warn("Fallback RBAC mappings loaded. Please configure proper mappings in database!");
    }

    /**
     * Refresh cache every 5 minutes to pick up Admin Panel changes.
     */
    @Scheduled(fixedDelay = 300000, initialDelay = 300000) // 5 minutes
    @CacheEvict(value = "rbacCache", allEntries = true)
    public void refreshCache() {
        log.debug("Refreshing RBAC cache from database");
        try {
            loadRoleMappingsFromDatabase();
            log.info("RBAC cache refreshed successfully");
        } catch (Exception e) {
            log.error("Failed to refresh RBAC cache, keeping existing cache", e);
        }
    }

    /**
     * Manually refresh cache (can be called from Admin Panel).
     */
    @CacheEvict(value = "rbacCache", allEntries = true)
    public void forceRefresh() {
        log.info("Force refreshing RBAC cache from database");
        loadRoleMappingsFromDatabase();
    }

    /**
     * Check if a role is authorized for a scenario.
     * Case-insensitive to handle LLM returning lowercase scenario names.
     */
    @Cacheable(value = "rbacCache", key = "#role + ':' + #scenarioCode.toUpperCase()")
    public boolean isAuthorized(String role, String scenarioCode) {
        if (!initialized) {
            log.warn("RBAC not initialized, denying access");
            return false;
        }

        // Normalize scenario code to uppercase for comparison
        String normalizedScenario = scenarioCode.toUpperCase();

        Set<String> allowedScenarios = roleScenarioCache.get(role);
        boolean authorized = allowedScenarios != null && allowedScenarios.contains(normalizedScenario);

        if (!authorized) {
            log.debug("Authorization check: role={}, scenario={} (normalized={}), authorized=false",
                     role, scenarioCode, normalizedScenario);
        }

        return authorized;
    }

    /**
     * Check if any of the given roles is authorized for a scenario.
     */
    public boolean isAnyRoleAuthorized(List<String> roles, String scenarioCode) {
        if (roles == null || roles.isEmpty()) {
            log.debug("No roles provided for authorization check");
            return false;
        }

        boolean authorized = roles.stream().anyMatch(role -> isAuthorized(role, scenarioCode));

        if (!authorized) {
            log.debug("Authorization failed: roles={}, scenario={}", roles, scenarioCode);
        }

        return authorized;
    }

    /**
     * Get all scenarios allowed for a role.
     */
    public Set<String> getAllowedScenarios(String role) {
        return roleScenarioCache.getOrDefault(role, Set.of());
    }

    /**
     * Get all roles that can access a specific scenario.
     */
    public Set<String> getRolesForScenario(String scenarioCode) {
        return roleScenarioCache.entrySet().stream()
                .filter(entry -> entry.getValue().contains(scenarioCode))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    /**
     * Add a role-scenario mapping (for Admin Panel).
     * Persists to database and updates cache.
     */
    public void addRoleScenarioMapping(String role, String scenarioCode) {
        log.info("Adding role-scenario mapping: role={}, scenario={}", role, scenarioCode);

        // Check if already exists
        if (roleScenarioMapRepository.existsByRoleNameAndScenarioCode(role, scenarioCode)) {
            log.debug("Mapping already exists: role={}, scenario={}", role, scenarioCode);
            return;
        }

        // Save to database
        RoleScenarioMap mapping = RoleScenarioMap.builder()
                .roleName(role)
                .scenarioCode(scenarioCode)
                .build();
        roleScenarioMapRepository.save(mapping);

        // Update cache
        roleScenarioCache.computeIfAbsent(role, k -> ConcurrentHashMap.newKeySet()).add(scenarioCode);

        log.info("Role-scenario mapping added successfully");
    }

    /**
     * Remove a role-scenario mapping (for Admin Panel).
     * Removes from database and updates cache.
     */
    public void removeRoleScenarioMapping(String role, String scenarioCode) {
        log.info("Removing role-scenario mapping: role={}, scenario={}", role, scenarioCode);

        // Remove from database
        roleScenarioMapRepository.findByRoleNameAndScenarioCode(role, scenarioCode)
                .ifPresent(roleScenarioMapRepository::delete);

        // Update cache
        Set<String> scenarios = roleScenarioCache.get(role);
        if (scenarios != null) {
            scenarios.remove(scenarioCode);
        }

        log.info("Role-scenario mapping removed successfully");
    }

    /**
     * Get all role mappings (for Admin Panel display).
     */
    public Map<String, Set<String>> getAllRoleMappings() {
        return Map.copyOf(roleScenarioCache);
    }

    /**
     * Get initialization status.
     */
    public boolean isInitialized() {
        return initialized;
    }
}
