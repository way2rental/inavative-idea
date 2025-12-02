package com.enterprise.ai.core.filter;

import com.enterprise.ai.common.context.RequestContext;
import com.enterprise.ai.common.context.RequestContextHolder;
import com.enterprise.ai.data.entity.AiScenario;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Multi-Filter Engine for dynamic SQL generation.
 * 
 * ENTERPRISE AI PRINCIPLE: AI NEVER generates business data.
 * AI extracts filters → FilterEngine builds SQL → Database executes → AI formats response
 * 
 * Features:
 * - Dynamic WHERE clause generation from extracted parameters
 * - Mandatory/optional filter enforcement
 * - Type validation (STRING, NUMBER, DECIMAL, DATE, DATETIME, BOOLEAN, ENUM)
 * - Pattern validation (regex)
 * - Security filter injection (userId, orgId for RLS)
 * - SQL injection prevention via parameterized queries
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FilterEngine {

    private final ObjectMapper objectMapper;

    /**
     * Filter definition structure (parsed from JSON).
     */
    public record FilterDefinition(
            String name,
            String displayName,
            String description,
            String type,           // STRING, NUMBER, DECIMAL, DATE, DATETIME, BOOLEAN, ENUM
            String dbColumn,
            String operator,       // =, !=, <, >, <=, >=, LIKE, IN, BETWEEN
            boolean mandatory,
            String defaultValue,
            String validationPattern,
            String validationError,
            List<String> enumValues
    ) {
        @SuppressWarnings("unchecked")
        public static FilterDefinition fromMap(Map<String, Object> map) {
            return new FilterDefinition(
                    (String) map.get("name"),
                    (String) map.get("displayName"),
                    (String) map.get("description"),
                    (String) map.getOrDefault("type", "STRING"),
                    (String) map.get("dbColumn"),
                    (String) map.getOrDefault("operator", "="),
                    Boolean.TRUE.equals(map.get("mandatory")),
                    (String) map.get("defaultValue"),
                    (String) map.get("validationPattern"),
                    (String) map.get("validationError"),
                    map.get("enumValues") != null ? (List<String>) map.get("enumValues") : null
            );
        }
    }

    /**
     * Security filter definition structure.
     */
    public record SecurityFilter(
            String dbColumn,
            String contextKey
    ) {
        public static SecurityFilter fromMap(Map<String, Object> map) {
            return new SecurityFilter(
                    (String) map.get("dbColumn"),
                    (String) map.get("contextKey")
            );
        }
    }

    /**
     * Result of filter processing.
     */
    public record FilterResult(
            String whereClauses,           // SQL WHERE clauses to append
            Map<String, Object> parameters, // Named parameters for PreparedStatement
            List<String> missingMandatory,  // Missing mandatory filters
            List<String> validationErrors   // Validation error messages
    ) {
        public boolean isValid() {
            return missingMandatory.isEmpty() && validationErrors.isEmpty();
        }

        public boolean hasMissingParams() {
            return !missingMandatory.isEmpty();
        }
    }

    /**
     * Build dynamic SQL with filters from extracted parameters.
     * 
     * @param scenario The scenario configuration
     * @param extractedParams Parameters extracted by AI from user query
     * @return FilterResult with WHERE clauses and bound parameters
     */
    public FilterResult buildFilters(AiScenario scenario, Map<String, Object> extractedParams) {
        if (!scenario.usesFilterEngine()) {
            // Fallback to legacy behavior - no filter engine configured
            log.debug("Scenario {} does not use filter engine, returning empty filters", scenario.getScenarioCode());
            return new FilterResult("", extractedParams != null ? extractedParams : Map.of(), List.of(), List.of());
        }

        List<FilterDefinition> filters = parseFilterDefinitions(scenario.getFilterDefinitions());
        if (filters.isEmpty()) {
            return new FilterResult("", extractedParams != null ? extractedParams : Map.of(), List.of(), List.of());
        }

        StringBuilder whereClause = new StringBuilder();
        Map<String, Object> boundParams = new HashMap<>();
        List<String> missingMandatory = new ArrayList<>();
        List<String> validationErrors = new ArrayList<>();

        // Process each filter definition
        for (FilterDefinition filter : filters) {
            Object value = extractedParams != null ? extractedParams.get(filter.name()) : null;

            // Apply default value if not provided
            if (value == null && filter.defaultValue() != null) {
                value = resolveDefaultValue(filter.defaultValue());
            }

            // Check mandatory
            if (filter.mandatory() && (value == null || isBlank(value))) {
                missingMandatory.add(filter.name());
                continue;
            }

            // Skip if no value and not mandatory
            if (value == null || isBlank(value)) {
                continue;
            }

            // Validate value
            String validationError = validateValue(filter, value);
            if (validationError != null) {
                validationErrors.add(validationError);
                continue;
            }

            // Build WHERE clause for this filter
            String clause = buildWhereClause(filter, value, boundParams);
            if (clause != null && !clause.isEmpty()) {
                if (whereClause.length() > 0) {
                    whereClause.append(" AND ");
                }
                whereClause.append(clause);
            }
        }

        // Add security filters (always applied)
        String securityClauses = buildSecurityFilters(scenario.getSecurityFilters(), boundParams);
        if (securityClauses != null && !securityClauses.isEmpty()) {
            if (whereClause.length() > 0) {
                whereClause.append(" AND ");
            }
            whereClause.append(securityClauses);
        }

        log.debug("Built filter query for {}: WHERE {} with params {}", 
                scenario.getScenarioCode(), whereClause, boundParams.keySet());

        return new FilterResult(whereClause.toString(), boundParams, missingMandatory, validationErrors);
    }

    /**
     * Build complete SQL query with dynamic filters.
     */
    public String buildDynamicQuery(AiScenario scenario, FilterResult filterResult) {
        String baseQuery = scenario.getSqlQuery();
        if (baseQuery == null || baseQuery.isBlank()) {
            throw new IllegalStateException("No base SQL query configured for scenario: " + scenario.getScenarioCode());
        }

        StringBuilder query = new StringBuilder(baseQuery.trim());

        // Add WHERE clause if we have filters
        String whereClauses = filterResult.whereClauses();
        if (whereClauses != null && !whereClauses.isEmpty()) {
            // Check if base query already has WHERE
            String upperQuery = baseQuery.toUpperCase();
            if (upperQuery.contains(" WHERE ")) {
                query.append(" AND ").append(whereClauses);
            } else {
                query.append(" WHERE ").append(whereClauses);
            }
        }

        // Add default sort if configured
        if (scenario.getDefaultSort() != null && !scenario.getDefaultSort().isBlank()) {
            if (!baseQuery.toUpperCase().contains(" ORDER BY ")) {
                query.append(" ORDER BY ").append(scenario.getDefaultSort());
            }
        }

        // Add limit if configured
        if (scenario.getMaxResults() != null && scenario.getMaxResults() > 0) {
            if (!baseQuery.toUpperCase().contains(" LIMIT ")) {
                query.append(" LIMIT ").append(scenario.getMaxResults());
            }
        }

        return query.toString();
    }

    /**
     * Get mandatory filter names for a scenario (for prompt building).
     */
    public List<String> getMandatoryFilterNames(AiScenario scenario) {
        if (!scenario.usesFilterEngine()) {
            return List.of();
        }
        return parseFilterDefinitions(scenario.getFilterDefinitions()).stream()
                .filter(FilterDefinition::mandatory)
                .map(FilterDefinition::name)
                .toList();
    }

    /**
     * Get all filter definitions for a scenario (for prompt context).
     */
    public List<FilterDefinition> getFilterDefinitions(AiScenario scenario) {
        if (!scenario.usesFilterEngine()) {
            return List.of();
        }
        return parseFilterDefinitions(scenario.getFilterDefinitions());
    }

    /**
     * Build filter context for LLM prompt.
     * Returns human-readable description of available filters.
     */
    public String buildFilterContext(AiScenario scenario) {
        List<FilterDefinition> filters = getFilterDefinitions(scenario);
        if (filters.isEmpty()) {
            return "";
        }

        StringBuilder context = new StringBuilder();
        context.append("Available filters:\n");

        for (FilterDefinition filter : filters) {
            context.append("- ").append(filter.displayName() != null ? filter.displayName() : filter.name());
            if (filter.mandatory()) {
                context.append(" (REQUIRED)");
            } else {
                context.append(" (optional)");
            }
            if (filter.description() != null) {
                context.append(": ").append(filter.description());
            }
            if (filter.enumValues() != null && !filter.enumValues().isEmpty()) {
                context.append(" [values: ").append(String.join(", ", filter.enumValues())).append("]");
            }
            context.append("\n");
        }

        return context.toString();
    }

    // =====================================================================
    // PRIVATE HELPER METHODS
    // =====================================================================

    private List<FilterDefinition> parseFilterDefinitions(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<Map<String, Object>> maps = objectMapper.readValue(json, 
                    new TypeReference<List<Map<String, Object>>>() {});
            return maps.stream()
                    .map(FilterDefinition::fromMap)
                    .toList();
        } catch (Exception e) {
            log.error("Failed to parse filter definitions: {}", e.getMessage());
            return List.of();
        }
    }

    private String buildWhereClause(FilterDefinition filter, Object value, Map<String, Object> params) {
        String dbColumn = filter.dbColumn();
        String paramName = "filter_" + filter.name().replaceAll("[^a-zA-Z0-9]", "_");
        String operator = filter.operator() != null ? filter.operator().toUpperCase() : "=";

        // Convert value to appropriate type
        Object typedValue = convertValue(filter.type(), value);

        switch (operator) {
            case "=", "!=", "<", ">", "<=", ">=" -> {
                params.put(paramName, typedValue);
                return dbColumn + " " + operator + " :" + paramName;
            }
            case "LIKE" -> {
                params.put(paramName, "%" + typedValue + "%");
                return dbColumn + " LIKE :" + paramName;
            }
            case "IN" -> {
                if (typedValue instanceof Collection) {
                    params.put(paramName, typedValue);
                } else {
                    params.put(paramName, List.of(typedValue));
                }
                return dbColumn + " IN (:" + paramName + ")";
            }
            case "BETWEEN" -> {
                // Expects value to be a map with "from" and "to" keys
                if (typedValue instanceof Map<?, ?> rangeMap) {
                    params.put(paramName + "_from", rangeMap.get("from"));
                    params.put(paramName + "_to", rangeMap.get("to"));
                    return dbColumn + " BETWEEN :" + paramName + "_from AND :" + paramName + "_to";
                }
                return null;
            }
            case "IS NULL" -> {
                return dbColumn + " IS NULL";
            }
            case "IS NOT NULL" -> {
                return dbColumn + " IS NOT NULL";
            }
            default -> {
                params.put(paramName, typedValue);
                return dbColumn + " = :" + paramName;
            }
        }
    }

    private String buildSecurityFilters(String securityFiltersJson, Map<String, Object> params) {
        RequestContext context = RequestContextHolder.getContext();
        if (context == null) {
            log.warn("No request context available for security filters");
            return null;
        }

        if (securityFiltersJson == null || securityFiltersJson.isBlank()) {
            // Apply default security filters
            StringBuilder clauses = new StringBuilder();
            if (context.getUserId() != null) {
                params.put("sec_userId", context.getUserId());
                clauses.append("user_id = :sec_userId");
            }
            return clauses.toString();
        }

        try {
            Map<String, Map<String, Object>> secFilters = objectMapper.readValue(securityFiltersJson,
                    new TypeReference<Map<String, Map<String, Object>>>() {});

            StringBuilder clauses = new StringBuilder();

            // User level filter
            if (secFilters.containsKey("userLevel")) {
                SecurityFilter userFilter = SecurityFilter.fromMap(secFilters.get("userLevel"));
                String userId = getContextValue(userFilter.contextKey(), context);
                if (userId != null) {
                    params.put("sec_userId", userId);
                    if (clauses.length() > 0) clauses.append(" AND ");
                    clauses.append(userFilter.dbColumn()).append(" = :sec_userId");
                }
            }

            // Org level filter
            if (secFilters.containsKey("orgLevel")) {
                SecurityFilter orgFilter = SecurityFilter.fromMap(secFilters.get("orgLevel"));
                String orgId = getContextValue(orgFilter.contextKey(), context);
                if (orgId != null) {
                    params.put("sec_orgId", orgId);
                    if (clauses.length() > 0) clauses.append(" AND ");
                    clauses.append(orgFilter.dbColumn()).append(" = :sec_orgId");
                }
            }

            // Tenant level filter
            if (secFilters.containsKey("tenantLevel")) {
                SecurityFilter tenantFilter = SecurityFilter.fromMap(secFilters.get("tenantLevel"));
                String tenantId = getContextValue(tenantFilter.contextKey(), context);
                if (tenantId != null) {
                    params.put("sec_tenantId", tenantId);
                    if (clauses.length() > 0) clauses.append(" AND ");
                    clauses.append(tenantFilter.dbColumn()).append(" = :sec_tenantId");
                }
            }

            return clauses.toString();
        } catch (Exception e) {
            log.error("Failed to parse security filters: {}", e.getMessage());
            return null;
        }
    }

    private String getContextValue(String key, RequestContext context) {
        return switch (key) {
            case "userId" -> context.getUserId();
            case "orgId" -> context.getTenantId();
            case "tenantId" -> context.getTenantId();
            default -> null;
        };
    }

    private Object convertValue(String type, Object value) {
        if (value == null) return null;
        if (type == null) return value;

        try {
            return switch (type.toUpperCase()) {
                case "NUMBER" -> value instanceof Number ? ((Number) value).longValue() : Long.parseLong(value.toString());
                case "DECIMAL" -> value instanceof Number ? ((Number) value).doubleValue() : Double.parseDouble(value.toString());
                case "DATE" -> value instanceof LocalDate ? value : LocalDate.parse(value.toString());
                case "DATETIME" -> value instanceof LocalDateTime ? value : LocalDateTime.parse(value.toString());
                case "BOOLEAN" -> value instanceof Boolean ? value : Boolean.parseBoolean(value.toString());
                default -> value.toString();
            };
        } catch (Exception e) {
            log.warn("Failed to convert value '{}' to type {}: {}", value, type, e.getMessage());
            return value;
        }
    }

    private String validateValue(FilterDefinition filter, Object value) {
        // Type validation
        String type = filter.type() != null ? filter.type().toUpperCase() : "STRING";
        try {
            convertValue(type, value);
        } catch (Exception e) {
            return String.format("Invalid %s value for %s: %s", 
                    type.toLowerCase(), filter.displayName() != null ? filter.displayName() : filter.name(), value);
        }

        // Pattern validation
        if (filter.validationPattern() != null && !filter.validationPattern().isBlank()) {
            Pattern pattern = Pattern.compile(filter.validationPattern());
            if (!pattern.matcher(value.toString()).matches()) {
                return filter.validationError() != null ? filter.validationError() :
                        String.format("Invalid format for %s", filter.displayName() != null ? filter.displayName() : filter.name());
            }
        }

        // Enum validation
        if ("ENUM".equalsIgnoreCase(type) && filter.enumValues() != null && !filter.enumValues().isEmpty()) {
            if (!filter.enumValues().contains(value.toString())) {
                return String.format("Invalid value for %s. Allowed values: %s",
                        filter.displayName() != null ? filter.displayName() : filter.name(),
                        String.join(", ", filter.enumValues()));
            }
        }

        return null; // No validation errors
    }

    private Object resolveDefaultValue(String defaultValue) {
        if (defaultValue == null) return null;
        
        // Handle dynamic placeholders
        if (defaultValue.contains("${")) {
            RequestContext context = RequestContextHolder.getContext();
            if (context != null) {
                defaultValue = defaultValue
                        .replace("${userId}", context.getUserId() != null ? context.getUserId() : "")
                        .replace("${orgId}", context.getTenantId() != null ? context.getTenantId() : "")
                        .replace("${currentDate}", LocalDate.now().toString())
                        .replace("${currentDateTime}", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            }
        }
        return defaultValue;
    }

    private boolean isBlank(Object value) {
        if (value == null) return true;
        if (value instanceof String) return ((String) value).isBlank();
        if (value instanceof Collection) return ((Collection<?>) value).isEmpty();
        return false;
    }
}
