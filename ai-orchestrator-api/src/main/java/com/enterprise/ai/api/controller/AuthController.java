package com.enterprise.ai.api.controller;

import com.enterprise.ai.security.jwt.JwtService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for authentication endpoints.
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Authentication API")
public class AuthController {

    private final JwtService jwtService;

    @PostMapping("/token")
    @Operation(summary = "Generate JWT token", description = "Generate a JWT token for testing purposes")
    public ResponseEntity<Map<String, String>> generateToken(
            @RequestParam String username,
            @RequestParam(defaultValue = "USER") String role) {
        
        log.info("Generating token for user: {} with role: {}", username, role);
        String token = jwtService.generateToken(username, List.of(role));
        
        return ResponseEntity.ok(Map.of(
                "token", token,
                "type", "Bearer",
                "username", username,
                "role", role
        ));
    }

    @GetMapping("/validate")
    @Operation(summary = "Validate JWT token", description = "Validate a JWT token")
    public ResponseEntity<Map<String, Object>> validateToken(
            @RequestHeader("Authorization") String authHeader) {
        
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.badRequest().body(Map.of("valid", false, "error", "Invalid Authorization header"));
        }
        
        String token = authHeader.substring(7);
        try {
            String username = jwtService.extractUsername(token);
            List<String> roles = jwtService.extractRoles(token);
            boolean expired = jwtService.isTokenExpired(token);
            
            return ResponseEntity.ok(Map.of(
                    "valid", !expired,
                    "username", username,
                    "roles", roles,
                    "expired", expired
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("valid", false, "error", e.getMessage()));
        }
    }
}
