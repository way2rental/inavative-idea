package com.enterprise.ai.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.function.Function;

/**
 * Service for JWT token operations.
 * 
 * SECURITY CRITICAL:
 * - JWT secret MUST be loaded from environment variable or Kubernetes Secret
 * - Application will FAIL TO START if secret is missing or is the default value
 * - Never hardcode secrets in source code
 */
@Slf4j
@Service
public class JwtService {

    @Value("${JWT_SECRET:#{null}}")
    private String jwtSecretEnv;

    @Value("${jwt.secret:#{null}}")
    private String jwtSecretConfig;

    @Value("${jwt.expiration:86400000}")
    private long expiration;

    private String secret;

    /**
     * Initialize and validate JWT secret on application startup.
     * Fails fast if secret is missing or insecure.
     */
    @PostConstruct
    public void init() {
        // Priority: Environment variable > Config file
        if (jwtSecretEnv != null && !jwtSecretEnv.isBlank()) {
            secret = jwtSecretEnv;
            log.info("JWT secret loaded from environment variable");
        } else if (jwtSecretConfig != null && !jwtSecretConfig.isBlank()) {
            secret = jwtSecretConfig;
            log.info("JWT secret loaded from config file");
        } else {
            log.error("JWT Secret Missing - Startup Aborted");
            throw new IllegalStateException("JWT Secret Missing - Startup Aborted. " +
                    "Set JWT_SECRET environment variable or jwt.secret config property.");
        }

        // Check for default/insecure secrets
        if (isInsecureSecret(secret)) {
            log.error("JWT Secret is insecure (default or too short) - Startup Aborted");
            throw new IllegalStateException("JWT Secret is insecure - Startup Aborted. " +
                    "Provide a strong secret with at least 256 bits (32+ characters).");
        }

        log.info("JWT Service initialized successfully");
    }

    /**
     * Check if the secret is insecure (default value or too short)
     */
    private boolean isInsecureSecret(String secret) {
        // List of known insecure/default secrets that should not be used
        List<String> insecureSecrets = List.of(
                "default-secret-key-for-development-only-change-in-production",
                "your-256-bit-secret-key-for-jwt-token-generation-change-in-production",
                "secret",
                "changeme",
                "password"
        );

        // Check if it's a known insecure secret
        if (insecureSecrets.contains(secret)) {
            return true;
        }

        // Check minimum length (256 bits = 32 bytes)
        if (secret.length() < 32) {
            log.warn("JWT secret is shorter than recommended 32 characters");
            return true;
        }

        return false;
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Extract username from token.
     */
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /**
     * Extract expiration date from token.
     */
    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    /**
     * Extract roles from token.
     */
    @SuppressWarnings("unchecked")
    public List<String> extractRoles(String token) {
        Claims claims = extractAllClaims(token);
        return claims.get("roles", List.class);
    }

    /**
     * Extract a specific claim from token.
     */
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Check if token is expired.
     */
    public boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    /**
     * Validate token.
     */
    public boolean validateToken(String token, String username) {
        final String extractedUsername = extractUsername(token);
        return (extractedUsername.equals(username) && !isTokenExpired(token));
    }

    /**
     * Generate token for user.
     */
    public String generateToken(String username, List<String> roles) {
        return Jwts.builder()
                .subject(username)
                .claim("roles", roles)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey())
                .compact();
    }
}
