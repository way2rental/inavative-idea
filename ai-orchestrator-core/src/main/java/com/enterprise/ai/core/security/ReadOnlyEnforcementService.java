package com.enterprise.ai.core.security;

import com.enterprise.ai.common.exception.SecurityViolationException;
import com.enterprise.ai.data.entity.HttpUrlWhitelist;
import com.enterprise.ai.data.repository.HttpUrlWhitelistRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.AntPathMatcher;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;

/**
 * Read-Only Enforcement Layer.
 * Ensures all operations are strictly read-only for enterprise compliance.
 * 
 * Enforces:
 * - SQL queries must start with SELECT
 * - HTTP methods only GET or POST
 * - URLs must match whitelist patterns
 */
@Slf4j
@Service
public class ReadOnlyEnforcementService {

    // SQL keywords that indicate write operations
    private static final Set<String> BLOCKED_SQL_KEYWORDS = Set.of(
            "INSERT", "UPDATE", "DELETE", "DROP", "CREATE", "ALTER", "TRUNCATE",
            "MERGE", "REPLACE", "CALL", "EXECUTE", "EXEC", "GRANT", "REVOKE"
    );

    // Pattern to extract first SQL keyword (only alphabetic characters)
    private static final Pattern SQL_FIRST_WORD = Pattern.compile("^\\s*([a-zA-Z]+)\\s+", Pattern.CASE_INSENSITIVE);
    
    // Pre-compiled patterns for blocked keywords (performance optimization)
    private static final Map<String, Pattern> BLOCKED_KEYWORD_PATTERNS = new java.util.HashMap<>();
    static {
        for (String keyword : BLOCKED_SQL_KEYWORDS) {
            BLOCKED_KEYWORD_PATTERNS.put(keyword, Pattern.compile("\\b" + keyword + "\\b", Pattern.CASE_INSENSITIVE));
        }
    }

    // Allowed HTTP methods (read-only)
    private static final Set<String> ALLOWED_HTTP_METHODS = Set.of("GET", "POST");

    private final HttpUrlWhitelistRepository whitelistRepository;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final List<String> cachedUrlPatterns = new CopyOnWriteArrayList<>();

    public ReadOnlyEnforcementService(HttpUrlWhitelistRepository whitelistRepository) {
        this.whitelistRepository = whitelistRepository;
    }

    @PostConstruct
    public void refreshUrlWhitelist() {
        try {
            List<HttpUrlWhitelist> activePatterns = whitelistRepository.findByActiveTrue();
            cachedUrlPatterns.clear();
            activePatterns.forEach(p -> cachedUrlPatterns.add(p.getUrlPattern()));
            log.info("Loaded {} URL whitelist patterns", cachedUrlPatterns.size());
        } catch (Exception e) {
            log.error("Failed to load URL whitelist, using defaults", e);
            // Default patterns for development
            cachedUrlPatterns.add("http://localhost:*/**");
            cachedUrlPatterns.add("http://127.0.0.1:*/**");
        }
    }

    /**
     * Validate SQL query is SELECT only.
     * @throws SecurityViolationException if query attempts write operation
     */
    public void validateSqlQuery(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new SecurityViolationException("SQL query cannot be empty", "EMPTY_QUERY", null);
        }

        String normalizedSql = sql.trim().toUpperCase();
        
        // Check first keyword
        var matcher = SQL_FIRST_WORD.matcher(normalizedSql);
        if (matcher.find()) {
            String firstKeyword = matcher.group(1);
            
            if (!"SELECT".equals(firstKeyword)) {
                throw new SecurityViolationException(
                        "Only SELECT queries are allowed. Attempted: " + firstKeyword,
                        "NON_SELECT_QUERY",
                        firstKeyword
                );
            }
        } else {
            throw new SecurityViolationException("Invalid SQL query format", "INVALID_SQL", sql);
        }

        // Check for blocked keywords anywhere in query (prevents injection via subqueries)
        // Uses pre-compiled patterns for performance
        for (Map.Entry<String, Pattern> entry : BLOCKED_KEYWORD_PATTERNS.entrySet()) {
            if (entry.getValue().matcher(normalizedSql).find()) {
                throw new SecurityViolationException(
                        "Blocked SQL keyword detected: " + entry.getKey(),
                        "BLOCKED_SQL_KEYWORD",
                        entry.getKey()
                );
            }
        }

        log.debug("SQL query validated as read-only: {}", sql.substring(0, Math.min(50, sql.length())));
    }

    /**
     * Validate HTTP method is allowed (GET or POST only).
     * @throws SecurityViolationException if method is not allowed
     */
    public void validateHttpMethod(String method) {
        if (method == null || method.isBlank()) {
            throw new SecurityViolationException("HTTP method cannot be empty", "EMPTY_METHOD", null);
        }

        String normalizedMethod = method.trim().toUpperCase();
        if (!ALLOWED_HTTP_METHODS.contains(normalizedMethod)) {
            throw new SecurityViolationException(
                    "HTTP method not allowed: " + normalizedMethod + ". Allowed: " + ALLOWED_HTTP_METHODS,
                    "BLOCKED_HTTP_METHOD",
                    normalizedMethod
            );
        }

        log.debug("HTTP method validated: {}", normalizedMethod);
    }

    /**
     * Validate URL is in whitelist.
     * @throws SecurityViolationException if URL is not whitelisted
     */
    public void validateUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new SecurityViolationException("URL cannot be empty", "EMPTY_URL", null);
        }

        // Check against all whitelist patterns
        for (String pattern : cachedUrlPatterns) {
            if (matchesPattern(url, pattern)) {
                log.debug("URL validated against whitelist: {}", url);
                return;
            }
        }

        throw new SecurityViolationException(
                "URL not in whitelist: " + url,
                "URL_NOT_WHITELISTED",
                url
        );
    }

    /**
     * Match URL against a whitelist pattern.
     * Supports Ant-style patterns with wildcards.
     */
    private boolean matchesPattern(String url, String pattern) {
        // Handle port wildcard (e.g., localhost:*)
        if (pattern.contains(":*")) {
            // Convert port wildcard to regex
            String regexPattern = pattern
                    .replace(":*", ":[0-9]+")
                    .replace("/**", "/.*")
                    .replace("/*", "/[^/]*");
            return url.matches(regexPattern);
        }
        
        // Use Ant path matcher for path patterns
        try {
            return pathMatcher.match(pattern, url);
        } catch (Exception e) {
            log.warn("Error matching URL pattern: {} against {}", url, pattern);
            return false;
        }
    }

    /**
     * Validate all aspects of an HTTP call.
     */
    public void validateHttpCall(String method, String url) {
        validateHttpMethod(method);
        validateUrl(url);
    }

    /**
     * Check if URL is whitelisted (non-throwing).
     */
    public boolean isUrlWhitelisted(String url) {
        try {
            validateUrl(url);
            return true;
        } catch (SecurityViolationException e) {
            return false;
        }
    }

    /**
     * Get current whitelist patterns (for debugging/admin).
     */
    public List<String> getWhitelistPatterns() {
        return List.copyOf(cachedUrlPatterns);
    }
}
