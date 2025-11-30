package com.enterprise.ai.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Health check controller.
 */
@RestController
@RequestMapping("/api/public")
@Tag(name = "Public", description = "Public endpoints that don't require authentication")
public class HealthController {

    @GetMapping("/health")
    @Operation(summary = "Health check", description = "Check if the service is running")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "AI Orchestrator",
                "version", "1.0.0"
        ));
    }

    @GetMapping("/info")
    @Operation(summary = "Service info", description = "Get service information")
    public ResponseEntity<Map<String, Object>> info() {
        return ResponseEntity.ok(Map.of(
                "name", "Enterprise Scenario-Driven AI Orchestrator",
                "description", "Secure Offline AI for Live Enterprise Systems",
                "version", "1.0.0",
                "features", Map.of(
                        "ollama", true,
                        "jwt", true,
                        "rbac", true,
                        "sse", true
                )
        ));
    }
}
