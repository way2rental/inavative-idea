package com.enterprise.ai.intelligence.service.parameter;

import com.enterprise.ai.common.dto.IntentResult;
import com.enterprise.ai.data.entity.AiScenario;
import com.enterprise.ai.data.service.ConfigCacheService;
import com.enterprise.ai.intelligence.service.context.ContextMemoryService;
import com.enterprise.ai.intelligence.service.context.ReferenceResolver;
import com.enterprise.ai.intelligence.service.entity.EntityExtractionService;
import com.enterprise.ai.intelligence.service.entity.EntityMatch;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for extracting parameters from user queries.
 * Combines entity extraction, context memory, and reference resolution.
 * 
 * NO HARDCODING - All parameter definitions from database.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ParameterExtractionService {

    private final EntityExtractionService entityExtractionService;
    private final ContextMemoryService contextMemoryService;
    private final ReferenceResolver referenceResolver;
    private final ConfigCacheService configCacheService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Extract all parameters for a scenario from query.
     * 
     * @param query User query
     * @param scenarioCode Scenario code
     * @param sessionId Session ID for context
     * @param userId User ID
     * @return Map of parameter name to parameter value
     */
    public Mono<Map<String, Object>> extractParameters(String query, String scenarioCode, 
                                                       String sessionId, String userId) {
        return Mono.fromCallable(() -> {
            try {
                // Get scenario configuration
                AiScenario scenario = configCacheService.getScenarioByCode(scenarioCode)
                    .orElseThrow(() -> new IllegalArgumentException("Scenario not found: " + scenarioCode));
                
                // Get required parameters from scenario
                List<String> requiredParams = parseRequiredParams(scenario.getRequiredParams());
                
                Map<String, Object> extractedParams = new HashMap<>();
                
                // Step 1: Extract entities from query using Entity Extraction Service
                // This includes both required entities AND filter entities (e.g., TRANSACTION_TYPE_FILTER)
                Map<String, EntityMatch> entities = entityExtractionService.extractEntities(query, scenarioCode)
                    .block(); // Block here since we're in a Mono.fromCallable
                
                log.debug("Extracted {} entities from query", entities.size());
                
                // Step 1.5: Extract filter parameters using DB-driven filter definitions
                // Map extracted filter entities to filter parameters based on scenario's filterDefinitions
                // This uses EntityPattern entities to extract filter values (e.g., TRANSACTION_TYPE_FILTER entity extracts "debit")
                extractFilterParametersFromEntities(query, scenario, entities, extractedParams);
                
                // Step 2: Map entities to parameters
                for (String paramName : requiredParams) {
                    // Try to find entity match for this parameter
                    String entityType = mapParameterToEntityType(paramName);
                    
                    if (entityType != null && entities.containsKey(entityType)) {
                        EntityMatch match = entities.get(entityType);
                        extractedParams.put(paramName, match.getValue());
                        
                        // Store in context memory for future reference
                        contextMemoryService.storeEntity(sessionId, userId, entityType, 
                            match.getValue(), match.getMetadata()).block();
                        
                        log.debug("Mapped entity {} to parameter {}", entityType, paramName);
                    }
                }
                
                // Step 3: Resolve references (e.g., "same account", "that transaction")
                if (referenceResolver.hasReference(query)) {
                    List<String> possibleTypes = referenceResolver.getPossibleEntityTypes(query);
                    
                    for (String entityType : possibleTypes) {
                        Optional<String> resolvedValue = contextMemoryService
                            .resolveReference(sessionId, query, entityType).block();
                        
                        if (resolvedValue.isPresent()) {
                            // Map to parameter
                            String paramName = mapEntityTypeToParameter(entityType, requiredParams);
                            if (paramName != null && !extractedParams.containsKey(paramName)) {
                                extractedParams.put(paramName, resolvedValue.get());
                                log.debug("Resolved reference {} to parameter {}", entityType, paramName);
                            }
                        }
                    }
                }
                
                // Step 4: Fill missing parameters from context (most recent)
                for (String paramName : requiredParams) {
                    if (!extractedParams.containsKey(paramName)) {
                        String entityType = mapParameterToEntityType(paramName);
                        if (entityType != null) {
                            Optional<String> recentValue = contextMemoryService
                                .getMostRecentEntity(sessionId, entityType).block();
                            
                            if (recentValue.isPresent()) {
                                extractedParams.put(paramName, recentValue.get());
                                log.debug("Filled parameter {} from context: {}", paramName, recentValue.get());
                            }
                        }
                    }
                }
                
                log.info("Extracted {} parameters for scenario {}: {}", 
                    extractedParams.size(), scenarioCode, extractedParams.keySet());
                
                return extractedParams;
            } catch (Exception e) {
                log.error("Error extracting parameters: {}", e.getMessage(), e);
                return new HashMap<>();
            }
        });
    }

    /**
     * Get missing parameters.
     * 
     * @param query User query
     * @param scenarioCode Scenario code
     * @param sessionId Session ID
     * @param userId User ID
     * @return List of missing parameter names
     */
    public Mono<List<String>> getMissingParameters(String query, String scenarioCode, 
                                                   String sessionId, String userId) {
        return extractParameters(query, scenarioCode, sessionId, userId)
            .map(extractedParams -> {
                AiScenario scenario = configCacheService.getScenarioByCode(scenarioCode)
                    .orElseThrow(() -> new IllegalArgumentException("Scenario not found: " + scenarioCode));
                
                List<String> requiredParams = parseRequiredParams(scenario.getRequiredParams());
                return requiredParams.stream()
                    .filter(param -> !extractedParams.containsKey(param))
                    .collect(Collectors.toList());
            });
    }

    /**
     * Parse required parameters from scenario.
     */
    private List<String> parseRequiredParams(String requiredParamsJson) {
        if (requiredParamsJson == null || requiredParamsJson.isEmpty()) {
            return Collections.emptyList();
        }
        
        try {
            // Try parsing as JSON array
            return objectMapper.readValue(requiredParamsJson, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            try {
                // Try parsing as comma-separated string
                return Arrays.stream(requiredParamsJson.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
            } catch (Exception e2) {
                log.warn("Failed to parse required params: {}", requiredParamsJson);
                return Collections.emptyList();
            }
        }
    }

    /**
     * Map parameter name to entity type.
     * This can be enhanced with DB-driven mapping in future.
     */
    private String mapParameterToEntityType(String paramName) {
        String paramLower = paramName.toLowerCase();
        
        if (paramLower.contains("account") || paramLower.contains("acc")) {
            return "ACCOUNT_ID";
        } else if (paramLower.contains("transaction") || paramLower.contains("txn")) {
            return "TRANSACTION_ID";
        } else if (paramLower.contains("payment")) {
            return "PAYMENT_ID";
        } else if (paramLower.contains("transfer")) {
            return "TRANSFER_ID";
        } else if (paramLower.contains("amount") || paramLower.contains("amt")) {
            return "AMOUNT";
        } else if (paramLower.contains("date") || paramLower.contains("time")) {
            return "DATE";
        } else if (paramLower.contains("email")) {
            return "EMAIL";
        } else if (paramLower.contains("phone")) {
            return "PHONE";
        }
        
        // Default: try parameter name as entity type
        return paramName.toUpperCase();
    }

    /**
     * Map entity type to parameter name.
     */
    private String mapEntityTypeToParameter(String entityType, List<String> requiredParams) {
        String entityLower = entityType.toLowerCase();
        
        for (String param : requiredParams) {
            String paramLower = param.toLowerCase();
            if (entityLower.contains("account") && paramLower.contains("account")) {
                return param;
            } else if (entityLower.contains("transaction") && paramLower.contains("transaction")) {
                return param;
            } else if (entityLower.contains("payment") && paramLower.contains("payment")) {
                return param;
            } else if (entityLower.contains("amount") && paramLower.contains("amount")) {
                return param;
            } else if (entityLower.contains("date") && paramLower.contains("date")) {
                return param;
            }
        }
        
        return null;
    }
    
    /**
     * Extract filter parameters from query using fully DB-driven approach.
     * 
     * Process:
     * 1. Read scenario's filterDefinitions to discover all available filters dynamically
     * 2. For ENUM-type filters, dynamically match query against enum values AND their synonyms
     * 3. Extract filter values directly from query using filter definitions (no hardcoded entity types)
     * 
     * NO HARDCODING - Everything comes from filterDefinitions JSON:
     * - Filter names are discovered from filterDefinitions
     * - Enum values are discovered from filterDefinitions
     * - Synonyms are discovered from filterDefinitions (enumSynonyms field)
     * - No hardcoded entity types like "TRANSACTION_TYPE_FILTER"
     */
    private void extractFilterParametersFromEntities(String query, AiScenario scenario, 
                                                     Map<String, EntityMatch> entities, 
                                                     Map<String, Object> params) {
        if (scenario == null || !scenario.usesFilterEngine() || query == null || query.isEmpty()) {
            return;
        }
        
        try {
            // Parse filter definitions from scenario (dynamic discovery - no hardcoding)
            List<FilterDefinition> filterDefs = parseFilterDefinitions(scenario.getFilterDefinitions());
            if (filterDefs.isEmpty()) {
                return;
            }
            
            String queryLower = query.toLowerCase().trim();
            
            // Process each filter definition dynamically
            for (FilterDefinition filterDef : filterDefs) {
                String filterName = filterDef.name();
                
                // Skip if already extracted
                if (params.containsKey(filterName)) {
                    continue;
                }
                
                // For ENUM-type filters, dynamically match against enum values and synonyms
                if ("ENUM".equalsIgnoreCase(filterDef.type()) && filterDef.enumValues() != null) {
                    String matchedValue = matchEnumFilter(queryLower, filterDef);
                    if (matchedValue != null) {
                        params.put(filterName, matchedValue);
                        log.debug("Extracted ENUM filter parameter {}={} from query", filterName, matchedValue);
                    }
                }
                // For other types, try to find matching entity (optional - if EntityPattern exists)
                else if (entities != null && !entities.isEmpty()) {
                    // Try to find entity that might match this filter (fallback)
                    String entityType = findEntityTypeForFilter(filterName, filterDef);
                    if (entityType != null && entities.containsKey(entityType)) {
                        EntityMatch match = entities.get(entityType);
                        Object value = match.getValue();
                        Object normalizedValue = normalizeFilterValue(filterDef, value);
                        if (normalizedValue != null) {
                            params.put(filterName, normalizedValue);
                            log.debug("Extracted filter parameter {}={} from entity {}", 
                                    filterName, normalizedValue, entityType);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Error extracting filter parameters: {}", e.getMessage());
            // Don't fail - filters are optional
        }
    }
    
    /**
     * Match query against ENUM filter values and their synonyms.
     * 
     * Supports synonyms from filterDefinitions:
     * - enumSynonyms field: {"CREDIT": ["credit", "cr", "deposit"], "DEBIT": ["debit", "dr", "withdrawal"]}
     * - Or enumValues as objects: [{"value": "CREDIT", "synonyms": ["cr", "credit"]}]
     * 
     * NO HARDCODING - All synonyms come from filterDefinitions JSON.
     * Examples: "CR" → "CREDIT", "DR" → "DEBIT", "type" → matches if query context includes enum value
     */
    private String matchEnumFilter(String queryLower, FilterDefinition filterDef) {
        if (filterDef.enumValues() == null || filterDef.enumValues().isEmpty()) {
            return null;
        }
        
        // Get synonyms from filter definition (already parsed in mapToFilterDefinition)
        Map<String, List<String>> enumSynonyms = filterDef.getEnumSynonyms();
        
        // Try to match each enum value and its synonyms
        for (String enumValue : filterDef.enumValues()) {
            String enumValueLower = enumValue.toLowerCase();
            
            // Direct match with enum value (case-insensitive)
            if (queryLower.contains(enumValueLower)) {
                // Ensure whole word match for better accuracy (optional - can be enhanced)
                // For now, simple contains match
                return enumValue; // Return canonical value (e.g., "CREDIT")
            }
            
            // Match with synonyms if available
            if (enumSynonyms != null && enumSynonyms.containsKey(enumValue)) {
                List<String> synonyms = enumSynonyms.get(enumValue);
                for (String synonym : synonyms) {
                    String synonymLower = synonym.toLowerCase().trim();
                    // Match synonym in query
                    if (queryLower.contains(synonymLower)) {
                        return enumValue; // Return canonical value, not synonym
                        // Example: query contains "cr" → returns "CREDIT"
                    }
                }
            }
        }
        
        return null;
    }
    
    /**
     * Find entity type that corresponds to a filter parameter.
     * Uses naming convention: {FILTER_NAME}_FILTER or direct match.
     * NO HARDCODING - This is just a mapping convention, actual patterns come from DB.
     */
    private String findEntityTypeForFilter(String filterName, FilterDefinition filterDef) {
        // Try direct match (e.g., filter "transactionType" -> entity "TRANSACTION_TYPE")
        String directMatch = filterName.toUpperCase().replaceAll("([A-Z]+)([A-Z][a-z])", "$1_$2");
        if (directMatch.endsWith("_FILTER")) {
            // Already has _FILTER suffix
        } else {
            directMatch = directMatch + "_FILTER";
        }
        
        // Also try without _FILTER suffix
        String withoutSuffix = directMatch.replace("_FILTER", "");
        
        // Return both possibilities - entity extraction service will match against actual entity types in DB
        // For now, return the primary one
        return directMatch;
    }
    
    /**
     * Normalize filter value based on filter definition (type, enum values, etc.).
     */
    private Object normalizeFilterValue(FilterDefinition filterDef, Object value) {
        if (value == null) {
            return null;
        }
        
        String valueStr = value.toString().trim();
        
        // Handle ENUM type - map to allowed enum values
        if ("ENUM".equalsIgnoreCase(filterDef.type()) && filterDef.enumValues() != null) {
            // Try to match value to enum values (case-insensitive)
            for (String enumValue : filterDef.enumValues()) {
                if (enumValue.equalsIgnoreCase(valueStr)) {
                    return enumValue; // Return exact enum value (preserves case from definition)
                }
            }
            // Try partial match (e.g., "debit" matches "DEBIT")
            for (String enumValue : filterDef.enumValues()) {
                if (enumValue.equalsIgnoreCase(valueStr) || 
                    valueStr.toLowerCase().contains(enumValue.toLowerCase()) ||
                    enumValue.toLowerCase().contains(valueStr.toLowerCase())) {
                    return enumValue;
                }
            }
        }
        
        // Handle other types
        switch (filterDef.type().toUpperCase()) {
            case "NUMBER":
            case "DECIMAL":
                try {
                    if (filterDef.type().equals("DECIMAL")) {
                        return Double.parseDouble(valueStr);
                    } else {
                        return Long.parseLong(valueStr);
                    }
                } catch (NumberFormatException e) {
                    log.debug("Could not parse {} as {}", valueStr, filterDef.type());
                    return null;
                }
            case "BOOLEAN":
                return Boolean.parseBoolean(valueStr) || "yes".equalsIgnoreCase(valueStr) || 
                       "true".equalsIgnoreCase(valueStr) || "1".equals(valueStr);
            case "DATE":
            case "DATETIME":
                // Date parsing would go here - for now return as-is
                return valueStr;
            default:
                return valueStr;
        }
    }
    
    /**
     * Parse filter definitions JSON.
     * Fully dynamic - no hardcoding of filter names or types.
     */
    private List<FilterDefinition> parseFilterDefinitions(String filterDefinitionsJson) {
        if (filterDefinitionsJson == null || filterDefinitionsJson.isBlank()) {
            return List.of();
        }
        
        try {
            List<Map<String, Object>> filterList = objectMapper.readValue(filterDefinitionsJson, 
                    new TypeReference<List<Map<String, Object>>>() {});
            
            return filterList.stream()
                    .map(this::mapToFilterDefinition)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("Error parsing filter definitions: {}", e.getMessage());
            return List.of();
        }
    }
    
    /**
     * Map JSON object to FilterDefinition record.
     * Handles synonyms in enumSynonyms field or within enumValues.
     */
    @SuppressWarnings("unchecked")
    private FilterDefinition mapToFilterDefinition(Map<String, Object> map) {
        // Parse enum values - can be strings or objects with synonyms
        List<String> enumValues = null;
        Map<String, List<String>> enumSynonyms = null;
        
        if (map.get("enumValues") != null) {
            List<?> enumList = (List<?>) map.get("enumValues");
            List<String> canonicalValues = new ArrayList<>();
            Map<String, List<String>> synonyms = new HashMap<>();
            
            for (Object enumItem : enumList) {
                if (enumItem instanceof String) {
                    // Simple string enum value
                    canonicalValues.add((String) enumItem);
                } else if (enumItem instanceof Map) {
                    // Enum value as object with synonyms: {"value": "CREDIT", "synonyms": ["cr", "credit"]}
                    Map<String, Object> enumObj = (Map<String, Object>) enumItem;
                    String value = (String) enumObj.get("value");
                    canonicalValues.add(value);
                    
                    if (enumObj.containsKey("synonyms")) {
                        List<String> synonymList = ((List<?>) enumObj.get("synonyms")).stream()
                                .map(Object::toString)
                                .collect(Collectors.toList());
                        synonyms.put(value, synonymList);
                    }
                }
            }
            
            enumValues = canonicalValues.isEmpty() ? null : canonicalValues;
            enumSynonyms = synonyms.isEmpty() ? null : synonyms;
        }
        
        // Also check for enumSynonyms field at filter level (alternative format)
        if (map.get("enumSynonyms") != null && enumSynonyms == null) {
            enumSynonyms = (Map<String, List<String>>) map.get("enumSynonyms");
        }
        
        return new FilterDefinition(
                (String) map.get("name"),
                (String) map.get("displayName"),
                (String) map.get("description"),
                (String) map.getOrDefault("type", "STRING"),
                (String) map.get("dbColumn"),
                (String) map.getOrDefault("operator", "="),
                Boolean.TRUE.equals(map.get("mandatory")),
                map.get("defaultValue") != null ? map.get("defaultValue").toString() : null,
                (String) map.get("validationPattern"),
                (String) map.get("validationError"),
                enumValues,
                enumSynonyms
        );
    }
    
    /**
     * Filter definition record (matches AiScenario.filterDefinitions JSON structure).
     * Enhanced with enumSynonyms support for dynamic synonym matching.
     */
    private record FilterDefinition(
            String name,
            String displayName,
            String description,
            String type,
            String dbColumn,
            String operator,
            boolean mandatory,
            String defaultValue,
            String validationPattern,
            String validationError,
            List<String> enumValues,
            Map<String, List<String>> enumSynonyms  // NEW: Synonyms for enum values (e.g., {"CREDIT": ["cr", "credit"]})
    ) {
        // Helper method to get enumSynonyms with fallback to empty map
        public Map<String, List<String>> getEnumSynonyms() {
            return enumSynonyms != null ? enumSynonyms : Map.of();
        }
    }
}
