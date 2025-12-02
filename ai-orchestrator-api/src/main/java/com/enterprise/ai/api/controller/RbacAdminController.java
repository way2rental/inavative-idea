package com.enterprise.ai.api.controller;

import com.enterprise.ai.data.entity.AiScenario;
import com.enterprise.ai.data.entity.RoleScenarioMap;
import com.enterprise.ai.data.service.RbacManagementService;
import com.enterprise.ai.data.service.ConfigCacheService;
import com.enterprise.ai.security.rbac.RbacService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Unified RBAC Admin Controller.
 * Combines legacy RbacService functionality with new RbacManagementService.
 * Manages role-scenario access control for the admin panel.
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/rbac")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "RBAC Management", description = "Admin API for managing role-scenario access control")
public class RbacAdminController {

    private final RbacService rbacService; // Legacy service for cache management
    private final RbacManagementService managementService; // New service for CRUD operations
    private final ConfigCacheService configCacheService; // For loading all scenarios

    // ===================== LEGACY ENDPOINTS (Preserved for backward compatibility) =====================

    /**
     * Get all role-scenario mappings (Legacy format).
     */
    @GetMapping("/mappings/legacy")
    @Operation(summary = "Get all role-scenario mappings (Legacy format)")
    public ResponseEntity<Map<String, Set<String>>> getAllMappingsLegacy() {
        log.info("Admin requested all RBAC mappings (legacy format)");
        return ResponseEntity.ok(rbacService.getAllRoleMappings());
    }

    /**
     * Get RBAC service status.
     */
    @GetMapping("/status")
    @Operation(summary = "Get RBAC service status")
    public ResponseEntity<RbacStatusResponse> getStatus() {
        boolean initialized = rbacService.isInitialized();
        Map<String, Set<String>> mappings = rbacService.getAllRoleMappings();

        return ResponseEntity.ok(new RbacStatusResponse(
                initialized,
                mappings.size(),
                mappings.values().stream().mapToInt(Set::size).sum()
        ));
    }

    /**
     * Refresh RBAC cache from database (Legacy).
     */
    @PostMapping("/refresh")
    @Operation(summary = "Refresh RBAC cache from database")
    public ResponseEntity<String> refreshCacheLegacy() {
        log.info("Admin requested RBAC cache refresh (legacy)");
        try {
            rbacService.forceRefresh();
            return ResponseEntity.ok("RBAC cache refreshed successfully");
        } catch (Exception e) {
            log.error("Failed to refresh RBAC cache", e);
            return ResponseEntity.badRequest().body("Failed to refresh cache: " + e.getMessage());
        }
    }

    // ===================== NEW ENDPOINTS (Enhanced CRUD operations) =====================

    /**
     * Get all RBAC mappings (New format with entity details).
     */
    @GetMapping("/mappings")
    @Operation(summary = "Get all RBAC mappings", description = "Returns all role-scenario access mappings")
    public ResponseEntity<List<RoleScenarioMap>> getAllMappings() {
        log.info("Fetching all RBAC mappings");
        return ResponseEntity.ok(managementService.getAllMappings());
    }

    /**
     * Get mapping by ID.
     */
    @GetMapping("/mappings/{id}")
    @Operation(summary = "Get mapping by ID", description = "Returns a single mapping by its ID")
    public ResponseEntity<RoleScenarioMap> getMappingById(@PathVariable Long id) {
        log.info("Fetching RBAC mapping: id={}", id);
        return managementService.getMappingById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get all distinct roles.
     */
    @GetMapping("/roles")
    @Operation(summary = "Get all roles", description = "Returns distinct list of roles that have scenario mappings")
    public ResponseEntity<List<String>> getAllRoles() {
        log.info("Fetching all roles");
        return ResponseEntity.ok(managementService.getDistinctRoles());
    }

    /**
     * Get all mapped scenarios.
     */
    @GetMapping("/scenarios")
    @Operation(summary = "Get all mapped scenarios", description = "Returns distinct list of scenarios that have role mappings")
    public ResponseEntity<List<String>> getAllMappedScenarios() {
        log.info("Fetching all mapped scenarios");
        return ResponseEntity.ok(managementService.getDistinctScenarios());
    }

    /**
     * Get scenarios by role.
     */
    @GetMapping("/roles/{roleName}/scenarios")
    @Operation(summary = "Get scenarios by role", description = "Returns list of scenarios accessible by a role")
    public ResponseEntity<List<String>> getScenariosByRole(@PathVariable String roleName) {
        log.info("Fetching scenarios for role: {}", roleName);
        return ResponseEntity.ok(managementService.getScenariosByRole(roleName));
    }

    /**
     * Get roles by scenario.
     */
    @GetMapping("/scenarios/{scenarioCode}/roles")
    @Operation(summary = "Get roles by scenario", description = "Returns list of roles that can access a scenario")
    public ResponseEntity<List<String>> getRolesByScenario(@PathVariable String scenarioCode) {
        log.info("Fetching roles for scenario: {}", scenarioCode);
        return ResponseEntity.ok(managementService.getRolesByScenario(scenarioCode));
    }

    /**
     * Check access.
     */
    @GetMapping("/check-access")
    @Operation(summary = "Check access", description = "Checks if a role has access to a scenario")
    public ResponseEntity<Map<String, Boolean>> checkAccess(
            @RequestParam String roleName,
            @RequestParam String scenarioCode) {
        log.info("Checking access: role={}, scenario={}", roleName, scenarioCode);
        boolean hasAccess = managementService.hasAccess(roleName, scenarioCode);

        Map<String, Boolean> response = new HashMap<>();
        response.put("hasAccess", hasAccess);
        return ResponseEntity.ok(response);
    }

    /**
     * Grant access (Add mapping).
     */
    @PostMapping("/mappings")
    @Operation(summary = "Grant access", description = "Grants a role access to a scenario")
    public ResponseEntity<RoleScenarioMap> grantAccess(@RequestBody RbacMappingDTO dto) {
        log.info("Granting access: role={}, scenario={}", dto.roleName, dto.scenarioCode);

        if (dto.roleName == null || dto.scenarioCode == null) {
            return ResponseEntity.badRequest().build();
        }

        RoleScenarioMap mapping = managementService.grantAccess(dto.roleName, dto.scenarioCode);

        // Refresh legacy cache
        rbacService.forceRefresh();

        return ResponseEntity.ok(mapping);
    }

    /**
     * Revoke access by ID.
     */
    @DeleteMapping("/mappings/{id}")
    @Operation(summary = "Revoke access", description = "Revokes a role's access to a scenario")
    public ResponseEntity<Void> revokeAccess(@PathVariable Long id) {
        log.info("Revoking access: id={}", id);

        if (managementService.getMappingById(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        managementService.revokeAccess(id);

        // Refresh legacy cache
        rbacService.forceRefresh();

        return ResponseEntity.ok().build();
    }

    /**
     * Revoke access by role and scenario.
     */
    @DeleteMapping("/revoke")
    @Operation(summary = "Revoke access by role and scenario", description = "Revokes access by role and scenario code")
    public ResponseEntity<Map<String, String>> revokeAccessByRoleAndScenario(
            @RequestParam String roleName,
            @RequestParam String scenarioCode) {
        log.info("Revoking access: role={}, scenario={}", roleName, scenarioCode);

        managementService.revokeAccessByRoleAndScenario(roleName, scenarioCode);

        // Refresh legacy cache
        rbacService.forceRefresh();

        Map<String, String> response = new HashMap<>();
        response.put("message", "Access revoked successfully");
        return ResponseEntity.ok(response);
    }

    /**
     * Bulk grant access.
     */
    @PostMapping("/mappings/bulk")
    @Operation(summary = "Bulk grant access", description = "Grants a role access to multiple scenarios")
    public ResponseEntity<List<RoleScenarioMap>> bulkGrantAccess(@RequestBody BulkRbacDTO dto) {
        log.info("Bulk granting access: role={}, {} scenarios", dto.roleName, dto.scenarioCodes.size());

        if (dto.roleName == null || dto.scenarioCodes == null || dto.scenarioCodes.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        List<RoleScenarioMap> mappings = managementService.bulkAssignScenarios(dto.roleName, dto.scenarioCodes);

        // Refresh legacy cache
        rbacService.forceRefresh();

        return ResponseEntity.ok(mappings);
    }

    /**
     * Revoke all from role.
     */
    @DeleteMapping("/roles/{roleName}/revoke-all")
    @Operation(summary = "Revoke all from role", description = "Revokes all scenario access from a role")
    public ResponseEntity<Map<String, String>> revokeAllFromRole(@PathVariable String roleName) {
        log.info("Revoking all scenarios from role: {}", roleName);

        managementService.bulkRevokeAllFromRole(roleName);

        // Refresh legacy cache
        rbacService.forceRefresh();

        Map<String, String> response = new HashMap<>();
        response.put("message", "All scenarios revoked from role: " + roleName);
        return ResponseEntity.ok(response);
    }

    /**
     * Revoke all from scenario.
     */
    @DeleteMapping("/scenarios/{scenarioCode}/revoke-all")
    @Operation(summary = "Revoke all from scenario", description = "Revokes all role access from a scenario")
    public ResponseEntity<Map<String, String>> revokeAllFromScenario(@PathVariable String scenarioCode) {
        log.info("Revoking all roles from scenario: {}", scenarioCode);

        managementService.bulkRevokeAllFromScenario(scenarioCode);

        // Refresh legacy cache
        rbacService.forceRefresh();

        Map<String, String> response = new HashMap<>();
        response.put("message", "All roles revoked from scenario: " + scenarioCode);
        return ResponseEntity.ok(response);
    }

    /**
     * Refresh cache (New endpoint).
     */
    @PostMapping("/cache/refresh")
    @Operation(summary = "Refresh cache", description = "Refreshes the RBAC mappings cache")
    public ResponseEntity<Map<String, String>> refreshCache() {
        log.info("Refreshing RBAC mappings cache");

        managementService.refreshCache();
        rbacService.forceRefresh();

        Map<String, String> response = new HashMap<>();
        response.put("message", "RBAC mappings cache refreshed successfully");
        return ResponseEntity.ok(response);
    }

    /**
     * Get RBAC matrix for admin UI.
     */
    @GetMapping("/matrix")
    @Operation(summary = "Get RBAC matrix", description = "Returns a matrix view of roles vs scenarios for admin UI")
    public ResponseEntity<Map<String, Object>> getRbacMatrix() {
        log.info("Fetching RBAC matrix");

        // Get roles from existing mappings
        List<String> mappedRoles = managementService.getDistinctRoles();

        // Add default roles if no mappings exist
        Set<String> roles = new HashSet<>(mappedRoles);
        if (roles.isEmpty()) {
            roles.add("ADMIN");
            roles.add("USER");
            roles.add("OPERATOR");
        }
        List<String> roleList = new ArrayList<>(roles);
        roleList.sort(String::compareTo);

        // Get ALL scenarios from ConfigCacheService (not just mapped ones)
        List<String> scenarios = configCacheService.getAllScenarios().stream()
                .map(AiScenario::getScenarioCode)
                .sorted()
                .collect(Collectors.toList());

        // Get existing mappings
        List<RoleScenarioMap> mappings = managementService.getAllMappings();

        Map<String, Object> matrix = new HashMap<>();
        matrix.put("roles", roleList);
        matrix.put("scenarios", scenarios);
        matrix.put("mappings", mappings);

        log.info("RBAC matrix: {} roles, {} scenarios, {} mappings", roleList.size(), scenarios.size(), mappings.size());

        return ResponseEntity.ok(matrix);
    }

    // ===================== DTO CLASSES =====================

    /**
     * Request DTO for role-scenario mapping (Legacy).
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RoleMappingRequest {
        private String role;
        private String scenarioCode;
    }

    /**
     * Response DTO for RBAC status.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RbacStatusResponse {
        private boolean initialized;
        private int totalRoles;
        private int totalMappings;
    }

    /**
     * DTO for new RBAC mapping operations.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RbacMappingDTO {
        public String roleName;
        public String scenarioCode;
    }

    /**
     * DTO for bulk operations.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BulkRbacDTO {
        public String roleName;
        public List<String> scenarioCodes;
    }
}

