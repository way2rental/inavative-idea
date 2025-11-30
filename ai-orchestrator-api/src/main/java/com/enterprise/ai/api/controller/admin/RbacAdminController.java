package com.enterprise.ai.api.controller.admin;

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

import java.util.Map;
import java.util.Set;

/**
 * Admin API for managing RBAC (Role-Based Access Control) mappings.
 * Allows dynamic configuration of role-scenario permissions from Admin Panel.
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/rbac")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "RBAC Admin", description = "Manage role-scenario permissions")
public class RbacAdminController {

    private final RbacService rbacService;

    /**
     * Get all role-scenario mappings.
     */
    @GetMapping("/mappings")
    @Operation(summary = "Get all role-scenario mappings")
    public ResponseEntity<Map<String, Set<String>>> getAllMappings() {
        log.info("Admin requested all RBAC mappings");
        return ResponseEntity.ok(rbacService.getAllRoleMappings());
    }

    /**
     * Get scenarios allowed for a specific role.
     */
    @GetMapping("/roles/{role}/scenarios")
    @Operation(summary = "Get allowed scenarios for a role")
    public ResponseEntity<Set<String>> getAllowedScenarios(@PathVariable String role) {
        log.info("Admin requested scenarios for role: {}", role);
        return ResponseEntity.ok(rbacService.getAllowedScenarios(role));
    }

    /**
     * Get roles that can access a specific scenario.
     */
    @GetMapping("/scenarios/{scenarioCode}/roles")
    @Operation(summary = "Get roles that can access a scenario")
    public ResponseEntity<Set<String>> getRolesForScenario(@PathVariable String scenarioCode) {
        log.info("Admin requested roles for scenario: {}", scenarioCode);
        return ResponseEntity.ok(rbacService.getRolesForScenario(scenarioCode));
    }

    /**
     * Add a role-scenario mapping.
     */
    @PostMapping("/mappings")
    @Operation(summary = "Add a role-scenario mapping")
    public ResponseEntity<String> addMapping(@RequestBody RoleMappingRequest request) {
        log.info("Admin adding mapping: role={}, scenario={}", request.getRole(), request.getScenarioCode());

        try {
            rbacService.addRoleScenarioMapping(request.getRole(), request.getScenarioCode());
            return ResponseEntity.ok("Mapping added successfully");
        } catch (Exception e) {
            log.error("Failed to add mapping", e);
            return ResponseEntity.badRequest().body("Failed to add mapping: " + e.getMessage());
        }
    }

    /**
     * Remove a role-scenario mapping.
     */
    @DeleteMapping("/mappings")
    @Operation(summary = "Remove a role-scenario mapping")
    public ResponseEntity<String> removeMapping(@RequestBody RoleMappingRequest request) {
        log.info("Admin removing mapping: role={}, scenario={}", request.getRole(), request.getScenarioCode());

        try {
            rbacService.removeRoleScenarioMapping(request.getRole(), request.getScenarioCode());
            return ResponseEntity.ok("Mapping removed successfully");
        } catch (Exception e) {
            log.error("Failed to remove mapping", e);
            return ResponseEntity.badRequest().body("Failed to remove mapping: " + e.getMessage());
        }
    }

    /**
     * Refresh RBAC cache from database.
     */
    @PostMapping("/refresh")
    @Operation(summary = "Refresh RBAC cache from database")
    public ResponseEntity<String> refreshCache() {
        log.info("Admin requested RBAC cache refresh");

        try {
            rbacService.forceRefresh();
            return ResponseEntity.ok("RBAC cache refreshed successfully");
        } catch (Exception e) {
            log.error("Failed to refresh RBAC cache", e);
            return ResponseEntity.badRequest().body("Failed to refresh cache: " + e.getMessage());
        }
    }

    /**
     * Check if RBAC service is initialized.
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
     * Request DTO for role-scenario mapping.
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
}

