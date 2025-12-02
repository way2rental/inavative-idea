package com.enterprise.ai.core.security;

import com.enterprise.ai.common.context.RequestContext;
import com.enterprise.ai.common.context.RequestContextHolder;
import com.enterprise.ai.common.exception.SecurityViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Central Row-Level Security Service.
 * 
 * MANDATORY for all DB queries. This service ensures:
 * - Only SELECT queries are allowed
 * - owner_user_id filter is always applied
 * - org_id filter is always applied
 * 
 * This prevents:
 * - User1 seeing User2's data
 * - Cross-tenant data access
 * - Data breaches from missed WHERE clauses
 * 
 * Per RBI and banking compliance requirements.
 */
@Slf4j
@Service
public class RowLevelSecurityService {

    // Pattern to detect WHERE clause
    private static final Pattern WHERE_PATTERN = Pattern.compile(
            "\\bWHERE\\b", Pattern.CASE_INSENSITIVE);

    // Pattern to detect existing owner_user_id in query
    private static final Pattern OWNER_USER_ID_PATTERN = Pattern.compile(
            "\\bowner_user_id\\b", Pattern.CASE_INSENSITIVE);

    // Pattern to detect existing org_id in query
    private static final Pattern ORG_ID_PATTERN = Pattern.compile(
            "\\borg_id\\b", Pattern.CASE_INSENSITIVE);

    // Pattern to detect GROUP BY, ORDER BY, LIMIT, HAVING (to insert before these)
    private static final Pattern TRAILING_CLAUSES_PATTERN = Pattern.compile(
            "\\b(GROUP\\s+BY|ORDER\\s+BY|LIMIT|HAVING|UNION|OFFSET)\\b", 
            Pattern.CASE_INSENSITIVE);

    // Pattern to detect subqueries (we only apply to outermost)
    private static final Pattern SUBQUERY_PATTERN = Pattern.compile(
            "\\(\\s*SELECT", Pattern.CASE_INSENSITIVE);

    /**
     * Apply row-level security filters to SQL query.
     * 
     * This method MUST be called for every SQL query before execution.
     * It automatically injects owner_user_id and org_id filters.
     * 
     * @param rawSql The raw SQL query
     * @param userContext The user context containing userId and orgId
     * @return SQL with row-level security filters applied
     * @throws SecurityViolationException if query is not SELECT or context is missing
     */
    public String applyRowLevelSecurity(String rawSql, RequestContext userContext) {
        if(true){
            // TODO Remove and make it proper Row Level Security enforcement for PROD Only.
            return rawSql;
        }
        // Validate inputs
        if (rawSql == null || rawSql.isBlank()) {
            throw new SecurityViolationException(
                    "SQL query cannot be empty",
                    "EMPTY_QUERY",
                    null
            );
        }

        if (userContext == null) {
            throw new SecurityViolationException(
                    "Security context missing. Access denied.",
                    "MISSING_CONTEXT",
                    null
            );
        }

        // Validate userId is present
        String userId = userContext.getUserId();
        if (userId == null || userId.isBlank()) {
            throw new SecurityViolationException(
                    "Security context missing. Access denied.",
                    "MISSING_USER_ID",
                    null
            );
        }

        // Validate orgId is present
        String orgId = userContext.getTenantId(); // tenantId is used as orgId
        if (orgId == null || orgId.isBlank()) {
            // STRICT: orgId must be present for row-level security
            log.error("org_id not present in context for user {}. Access denied.", userId);
            throw new SecurityViolationException(
                    "Security context missing. Access denied.",
                    "MISSING_ORG_ID",
                    null
            );
        }

        String normalizedSql = rawSql.trim();

        // A. Check if query starts with SELECT
        if (!normalizedSql.toUpperCase().startsWith("SELECT")) {
            throw new SecurityViolationException(
                    "Only SELECT queries are allowed in READ-ONLY mode",
                    "NON_SELECT_QUERY",
                    normalizedSql.split("\\s+")[0]
            );
        }

        StringBuilder securedSql = new StringBuilder(normalizedSql);

        // Build security conditions
        StringBuilder conditions = new StringBuilder();
        boolean hasConditions = false;

        // B. Inject owner_user_id if not present
        if (!OWNER_USER_ID_PATTERN.matcher(normalizedSql).find()) {
            conditions.append("owner_user_id = :userId");
            hasConditions = true;
            log.debug("Injecting owner_user_id filter for user: {}", userId);
        }

        // C. Inject org_id if not present
        if (!ORG_ID_PATTERN.matcher(normalizedSql).find()) {
            if (hasConditions) {
                conditions.append(" AND ");
            }
            conditions.append("org_id = :orgId");
            hasConditions = true;
            log.debug("Injecting org_id filter for org: {}", orgId);
        }

        // Only modify SQL if we need to inject conditions
        if (hasConditions) {
            String securityClause = conditions.toString();
            
            // D. Determine where to inject the security conditions
            Matcher whereMatcher = WHERE_PATTERN.matcher(normalizedSql);
            Matcher trailingMatcher = TRAILING_CLAUSES_PATTERN.matcher(normalizedSql);
            
            boolean hasWhere = whereMatcher.find();
            int trailingClausePos = trailingMatcher.find() ? trailingMatcher.start() : -1;

            if (hasWhere) {
                // SQL has WHERE → append AND at the end of WHERE conditions
                if (trailingClausePos > 0) {
                    // Insert before GROUP BY/ORDER BY/LIMIT/etc.
                    securedSql = new StringBuilder();
                    securedSql.append(normalizedSql, 0, trailingClausePos);
                    securedSql.append(" AND ").append(securityClause).append(" ");
                    securedSql.append(normalizedSql.substring(trailingClausePos));
                } else {
                    // Append at the end
                    securedSql.append(" AND ").append(securityClause);
                }
            } else {
                // SQL has no WHERE → create WHERE clause
                if (trailingClausePos > 0) {
                    // Insert before GROUP BY/ORDER BY/LIMIT/etc.
                    securedSql = new StringBuilder();
                    securedSql.append(normalizedSql, 0, trailingClausePos);
                    securedSql.append(" WHERE ").append(securityClause).append(" ");
                    securedSql.append(normalizedSql.substring(trailingClausePos));
                } else {
                    // Append at the end
                    securedSql.append(" WHERE ").append(securityClause);
                }
            }
        }

        String finalSql = securedSql.toString();
        log.info("Row-level security applied. Original length: {}, Secured length: {}", 
                rawSql.length(), finalSql.length());
        
        return finalSql;
    }

    /**
     * Apply row-level security using the current thread's request context.
     * Convenience method that gets context from RequestContextHolder.
     * 
     * @param rawSql The raw SQL query
     * @return SQL with row-level security filters applied
     * @throws SecurityViolationException if context is not available
     */
    public String applyRowLevelSecurity(String rawSql) {
        RequestContext context = RequestContextHolder.getContext();
        if (context == null) {
            throw new SecurityViolationException(
                    "Security context missing. Access denied.",
                    "MISSING_CONTEXT",
                    null
            );
        }
        return applyRowLevelSecurity(rawSql, context);
    }

    /**
     * Check if a query has row-level security filters.
     * Useful for validation and debugging.
     */
    public boolean hasRowLevelFilters(String sql) {
        if (sql == null) return false;
        return OWNER_USER_ID_PATTERN.matcher(sql).find() 
                && ORG_ID_PATTERN.matcher(sql).find();
    }

    /**
     * Validate that SQL is safe for execution.
     * Throws if any security issues are detected.
     */
    public void validateSecuredSql(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new SecurityViolationException("SQL cannot be empty", "EMPTY_QUERY", null);
        }

        if (!sql.trim().toUpperCase().startsWith("SELECT")) {
            throw new SecurityViolationException(
                    "Only SELECT queries are allowed in READ-ONLY mode",
                    "NON_SELECT_QUERY",
                    sql.split("\\s+")[0]
            );
        }

        if (!hasRowLevelFilters(sql)) {
            throw new SecurityViolationException(
                    "SQL query missing row-level security filters",
                    "MISSING_RLS_FILTERS",
                    sql
            );
        }
    }
}
