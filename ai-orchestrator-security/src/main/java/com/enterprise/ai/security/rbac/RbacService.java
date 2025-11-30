package com.enterprise.ai.security.rbac;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for Role-Based Access Control.
 */
@Service
public class RbacService {

    // In production, this would be loaded from database
    private final Map<String, Set<String>> roleScenarioMap = new ConcurrentHashMap<>();

    public RbacService() {
        // Default role-scenario mappings
        roleScenarioMap.put("USER", Set.of("TXN_STATUS", "ACCOUNT_SUMMARY"));
        roleScenarioMap.put("ADMIN", Set.of("TXN_STATUS", "ACCOUNT_SUMMARY", "FILE_STATUS"));
        roleScenarioMap.put("OPERATOR", Set.of("FILE_STATUS"));
    }

    /**
     * Check if a role is authorized for a scenario.
     */
    public boolean isAuthorized(String role, String scenarioCode) {
        Set<String> allowedScenarios = roleScenarioMap.get(role);
        return allowedScenarios != null && allowedScenarios.contains(scenarioCode);
    }

    /**
     * Check if any of the given roles is authorized for a scenario.
     */
    public boolean isAnyRoleAuthorized(List<String> roles, String scenarioCode) {
        if (roles == null || roles.isEmpty()) {
            return false;
        }
        return roles.stream().anyMatch(role -> isAuthorized(role, scenarioCode));
    }

    /**
     * Get all scenarios allowed for a role.
     */
    public Set<String> getAllowedScenarios(String role) {
        return roleScenarioMap.getOrDefault(role, Set.of());
    }

    /**
     * Add a role-scenario mapping.
     */
    public void addRoleScenarioMapping(String role, String scenarioCode) {
        roleScenarioMap.computeIfAbsent(role, k -> ConcurrentHashMap.newKeySet()).add(scenarioCode);
    }
}
