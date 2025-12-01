package com.enterprise.ai.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Health check controller for production readiness.
 * 
 * Provides comprehensive health status including:
 * - Application status
 * - Database connectivity
 * - Component status
 */
@Slf4j
@RestController
@RequestMapping("/api/public")
@Tag(name = "Public", description = "Public endpoints that don't require authentication")
@RequiredArgsConstructor
public class HealthController {

    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;

    @GetMapping("/health")
    @Operation(summary = "Health check", description = "Check if the service is running")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> health = new LinkedHashMap<>();
        health.put("status", "UP");
        health.put("service", "AI Orchestrator");
        health.put("version", "1.0.0");
        health.put("timestamp", Instant.now().toString());
        return ResponseEntity.ok(health);
    }

    @GetMapping("/health/detailed")
    @Operation(summary = "Detailed health check", description = "Get comprehensive health status including DB connectivity")
    public ResponseEntity<Map<String, Object>> detailedHealth() {
        Map<String, Object> health = new LinkedHashMap<>();
        health.put("status", "UP");
        health.put("service", "AI Orchestrator");
        health.put("version", "1.0.0");
        health.put("timestamp", Instant.now().toString());

        // Database health check
        Map<String, Object> dbHealth = new LinkedHashMap<>();
        try {
            if (jdbcTemplate != null) {
                jdbcTemplate.queryForObject("SELECT 1", Integer.class);
                dbHealth.put("status", "UP");
                dbHealth.put("database", "MySQL");
            } else {
                dbHealth.put("status", "UNKNOWN");
                dbHealth.put("message", "JdbcTemplate not configured");
            }
        } catch (Exception e) {
            dbHealth.put("status", "DOWN");
            dbHealth.put("error", e.getMessage());
            health.put("status", "DEGRADED");
        }
        health.put("database", dbHealth);

        // Components health
        Map<String, String> components = new LinkedHashMap<>();
        components.put("jwt", "ENABLED");
        components.put("rbac", "ENABLED");
        components.put("rls", "ENABLED");
        components.put("masking", "ENABLED");
        components.put("async-audit", "ENABLED");
        components.put("caching", "ENABLED");
        health.put("components", components);

        return ResponseEntity.ok(health);
    }

    @GetMapping("/health/ready")
    @Operation(summary = "Readiness probe", description = "Kubernetes readiness probe - checks if app is ready to serve traffic")
    public ResponseEntity<Map<String, Object>> readiness() {
        try {
            // Check database connectivity
            if (jdbcTemplate != null) {
                jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            }
            return ResponseEntity.ok(Map.of("status", "READY"));
        } catch (Exception e) {
            log.error("Readiness check failed: {}", e.getMessage());
            return ResponseEntity.status(503).body(Map.of(
                    "status", "NOT_READY",
                    "reason", e.getMessage()
            ));
        }
    }

    @GetMapping("/health/live")
    @Operation(summary = "Liveness probe", description = "Kubernetes liveness probe - checks if app process is alive")
    public ResponseEntity<Map<String, Object>> liveness() {
        // Simple liveness check - if we can respond, we're alive
        return ResponseEntity.ok(Map.of("status", "ALIVE"));
    }

    @GetMapping("/info")
    @Operation(summary = "Service info", description = "Get service information")
    public ResponseEntity<Map<String, Object>> info() {
        return ResponseEntity.ok(Map.of(
                "name", "Enterprise Scenario-Driven AI Orchestrator",
                "description", "Secure AI for Live Enterprise Systems",
                "version", "1.0.0",
                "features", Map.of(
                        "llm", "OpenAI GPT-4o-mini",
                        "jwt", true,
                        "rbac", true,
                        "rls", true,
                        "masking", true,
                        "sse", true,
                        "caching", true,
                        "async-audit", true
                )
        ));
    }
}
