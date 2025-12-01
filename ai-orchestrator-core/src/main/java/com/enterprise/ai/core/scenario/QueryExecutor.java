package com.enterprise.ai.core.scenario;

import com.enterprise.ai.common.context.RequestContextHolder;
import com.enterprise.ai.common.dto.ScenarioRequest;
import com.enterprise.ai.common.dto.ScenarioResult;
import com.enterprise.ai.common.enums.ExecutionType;
import com.enterprise.ai.common.exception.SecurityViolationException;
import com.enterprise.ai.core.mapper.ResponseMappingService;
import com.enterprise.ai.core.datasource.DataSourceRegistryService;
import com.enterprise.ai.core.mapper.JsonPathResponseMapper;
import com.enterprise.ai.core.mapper.MaskingService;
import com.enterprise.ai.core.security.ReadOnlyEnforcementService;
import com.enterprise.ai.core.security.RowLevelSecurityService;
import com.enterprise.ai.data.entity.AiResponseMapping;
import com.enterprise.ai.data.entity.AiScenario;
import com.enterprise.ai.data.repository.AiResponseMappingRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.*;

/**
 * Dynamic executor for DB_QUERY execution type.
 * Executes ONLY SELECT queries with strict read-only enforcement.
 * 
 * Features:
 * - SELECT-only validation
 * - Named parameter binding from request_mapping
 * - Response shaping via response_mapping
 * - Configurable timeout
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QueryExecutor implements DynamicExecutor {

    private static final long DEFAULT_TIMEOUT_MS = 5000;
    private static final int MAX_RESULT_SIZE = 1000; // Limit to prevent memory exhaustion

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ReadOnlyEnforcementService readOnlyEnforcement;
    private final RowLevelSecurityService rowLevelSecurityService;
    private final JsonPathResponseMapper responseMapper;
    private final MaskingService maskingService;
    private final AiResponseMappingRepository responseMappingRepository;
    private final ObjectMapper objectMapper;
    private final ResponseMappingService responseMappingService;

    @Override
    public boolean supports(String executionType) {
        return ExecutionType.DB_QUERY.name().equals(executionType);
    }

    @Override
    public ScenarioResult execute(ScenarioRequest request, AiScenario scenario) {
        long startTime = System.currentTimeMillis();
        String scenarioCode = scenario.getScenarioCode();

        try {
            // Validate SQL is SELECT only
            String sqlQuery = scenario.getSqlQuery();
            readOnlyEnforcement.validateSqlQuery(sqlQuery);

            // MANDATORY: Apply Row-Level Security filters
            // This ensures users can only see their own data (owner_user_id + org_id filters)
            String securedSql = rowLevelSecurityService.applyRowLevelSecurity(
                    sqlQuery,
                    RequestContextHolder.getContext()
            );
            log.debug("Row-Level Security applied. Original: {}, Secured: {}", sqlQuery, securedSql);

            // Build parameters from request_mapping
            Map<String, Object> sqlParams = buildSqlParameters(request, scenario);
            
            // Add user context parameters for RLS filters
            if (RequestContextHolder.getContext() != null) {
                sqlParams.put("userId", RequestContextHolder.getContext().getUserId());
                sqlParams.put("orgId", RequestContextHolder.getContext().getTenantId());
            }

            log.info("Executing DB_QUERY for scenario {} with params: {}", scenarioCode, sqlParams.keySet());

            // Execute query with secured SQL and timeout
            List<Map<String, Object>> rawResults = jdbcTemplate.queryForList(securedSql, sqlParams);
            
            // Limit result size to prevent memory exhaustion
            if (rawResults.size() > MAX_RESULT_SIZE) {
                log.warn("Query returned {} rows, truncating to {} for scenario {}", 
                        rawResults.size(), MAX_RESULT_SIZE, scenarioCode);
                rawResults = rawResults.subList(0, MAX_RESULT_SIZE);
            }

            Map<String, Object> aiReadyData;
            if (responseMappingService.hasMappings(scenarioCode)) {
                log.info("Using JsonPathResponseMapper for scenario: {}", scenarioCode);
                aiReadyData = responseMappingService.mapDbResultToAiRequest(scenarioCode, rawResults);
            } else {
                log.warn("No response mappings found for scenario: {}, using legacy mapping", scenarioCode);
                aiReadyData = applyResponseMapping(rawResults, scenario);
            }

            long executionTime = System.currentTimeMillis() - startTime;
            log.info("DB_QUERY for {} completed in {}ms, {} rows returned", 
                    scenarioCode, executionTime, rawResults.size());

            return ScenarioResult.builder()
                    .scenario(scenarioCode)
                    .success(true)
                    .data(aiReadyData)
                    .build();

        } catch (SecurityViolationException e) {
            log.error("Security violation in DB_QUERY for {}: {}", scenarioCode, e.getMessage());
            return ScenarioResult.builder()
                    .scenario(scenarioCode)
                    .success(false)
                    .errorMessage("Security violation: " + e.getMessage())
                    .data(Map.of("error", "Access denied"))
                    .build();
        } catch (Exception e) {
            log.error("DB_QUERY execution failed for {}: {}", scenarioCode, e.getMessage());
            return ScenarioResult.builder()
                    .scenario(scenarioCode)
                    .success(false)
                    .errorMessage("Database query failed. Please try again.")
                    .data(Map.of("error", "Query execution error"))
                    .build();
        }
    }

    @Override
    public Mono<ScenarioResult> executeReactive(ScenarioRequest request, AiScenario scenario) {
        long timeoutMs = scenario.getTimeoutMs() != null ? scenario.getTimeoutMs() : DEFAULT_TIMEOUT_MS;
        
        return Mono.fromCallable(() -> execute(request, scenario))
                .subscribeOn(Schedulers.boundedElastic())
                .timeout(Duration.ofMillis(timeoutMs))
                .onErrorResume(e -> {
                    log.error("Reactive DB_QUERY failed: {}", e.getMessage());
                    return Mono.just(ScenarioResult.builder()
                            .scenario(scenario.getScenarioCode())
                            .success(false)
                            .errorMessage("Query timeout or error: " + e.getMessage())
                            .build());
                });
    }

    @Override
    public ScenarioResult executeDryRun(ScenarioRequest request, AiScenario scenario) {
        try {
            // Validate SQL without executing
            String sqlQuery = scenario.getSqlQuery();
            readOnlyEnforcement.validateSqlQuery(sqlQuery);
            
            Map<String, Object> sqlParams = buildSqlParameters(request, scenario);
            
            return ScenarioResult.builder()
                    .scenario(scenario.getScenarioCode())
                    .success(true)
                    .data(Map.of(
                            "dryRun", true,
                            "executionType", ExecutionType.DB_QUERY.name(),
                            "sqlQuery", sqlQuery,
                            "boundParameters", sqlParams,
                            "responseMapping", scenario.getResponseMapping(),
                            "timeoutMs", scenario.getTimeoutMs()
                    ))
                    .build();
        } catch (Exception e) {
            return ScenarioResult.builder()
                    .scenario(scenario.getScenarioCode())
                    .success(false)
                    .errorMessage("Dry-run validation failed: " + e.getMessage())
                    .build();
        }
    }

    /**
     * Build SQL parameters from request using request_mapping.
     * request_mapping format: {"sqlParamName": "$.params.intentParamName"}
     */
    private Map<String, Object> buildSqlParameters(ScenarioRequest request, AiScenario scenario) {
        Map<String, Object> sqlParams = new HashMap<>();
        
        if (scenario.getRequestMapping() == null || scenario.getRequestMapping().isBlank()) {
            // No mapping, pass all params directly
            if (request.getParams() != null) {
                sqlParams.putAll(request.getParams());
            }
            return sqlParams;
        }

        try {
            Map<String, String> mappings = objectMapper.readValue(
                    scenario.getRequestMapping(), 
                    new TypeReference<Map<String, String>>() {}
            );

            for (Map.Entry<String, String> entry : mappings.entrySet()) {
                String sqlParamName = entry.getKey();
                String jsonPath = entry.getValue();
                
                // Simple JSONPath extraction (e.g., $.params.txnId -> params.txnId -> txnId)
                Object value = extractValue(request, jsonPath);
                if (value != null) {
                    sqlParams.put(sqlParamName, value);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse request_mapping, using direct params: {}", e.getMessage());
            if (request.getParams() != null) {
                sqlParams.putAll(request.getParams());
            }
        }

        return sqlParams;
    }

    /**
     * Apply MANDATORY masking to query results before they reach the AI formatter.
     * Raw DB data must NEVER be passed to the AI.
     *
     * If response_mapping is missing, execution is BLOCKED.
     */
    private Map<String, Object> applyMandatoryMasking(List<Map<String, Object>> rawResults, AiScenario scenario) {
        String scenarioCode = scenario.getScenarioCode();
        log.info("Applying mandatory masking for {}: {} rows", scenarioCode, rawResults.size());

        Map<String, Object> result = new LinkedHashMap<>();

        if (rawResults.isEmpty()) {
            result.put("data", List.of());
            result.put("count", 0);
            return result;
        }

        // Check if response_mapping exists
        if (scenario.getResponseMapping() == null || scenario.getResponseMapping().isBlank()) {
            // Try to get mappings from ai_response_mappings table
            List<AiResponseMapping> dbMappings = responseMappingRepository.findByScenarioCodeAndActiveTrue(scenarioCode);

            if (dbMappings.isEmpty()) {
                // STRICT: Block execution if no mapping is configured
                log.error("Response mapping not configured for scenario {}. Blocking raw data exposure.", scenarioCode);
                throw new SecurityViolationException(
                        "Response mapping not configured for this scenario",
                        "MISSING_RESPONSE_MAPPING",
                        scenarioCode
                );
            }

            // Use DB mappings
            return applyDbMappingsWithMasking(rawResults, dbMappings);
        }

        try {
            Map<String, String> mappings = objectMapper.readValue(
                    scenario.getResponseMapping(),
                    new TypeReference<Map<String, String>>() {}
            );

            if (rawResults.size() == 1) {
                // Single row - map and mask fields directly
                Map<String, Object> row = rawResults.get(0);
                for (Map.Entry<String, String> entry : mappings.entrySet()) {
                    String outputField = entry.getKey();
                    String sourceField = extractFieldName(entry.getValue());
                    Object value = row.get(sourceField);

                    // Apply masking based on field name detection
                    String maskingType = maskingService.detectMaskingType(outputField, value);
                    Object maskedValue = maskingService.mask(value, maskingType);
                    result.put(outputField, maskedValue);
                }
            } else {
                // Multiple rows - map and mask each row
                List<Map<String, Object>> maskedRows = new ArrayList<>();
                for (Map<String, Object> row : rawResults) {
                    Map<String, Object> maskedRow = new LinkedHashMap<>();
                    for (Map.Entry<String, String> entry : mappings.entrySet()) {
                        String outputField = entry.getKey();
                        String sourceField = extractFieldName(entry.getValue());
                        Object value = row.get(sourceField);

                        // Apply masking
                        String maskingType = maskingService.detectMaskingType(outputField, value);
                        Object maskedValue = maskingService.mask(value, maskingType);
                        maskedRow.put(outputField, maskedValue);
                    }
                    maskedRows.add(maskedRow);
                }
                result.put("data", maskedRows);
                result.put("count", maskedRows.size());
            }
        } catch (SecurityViolationException e) {
            throw e; // Re-throw security exceptions
        } catch (Exception e) {
            log.error("Failed to apply response_mapping for {}: {}", scenarioCode, e.getMessage());
            // STRICT: Do not fallback to raw data
            throw new SecurityViolationException(
                    "Response mapping failed for this scenario",
                    "RESPONSE_MAPPING_ERROR",
                    scenarioCode
            );
        }

        return result;
    }

    /**
     * Apply mappings from ai_response_mappings table with masking.
     */
    private Map<String, Object> applyDbMappingsWithMasking(List<Map<String, Object>> rawResults,
                                                            List<AiResponseMapping> dbMappings) {
        Map<String, Object> result = new LinkedHashMap<>();

        if (rawResults.size() == 1) {
            Map<String, Object> row = rawResults.get(0);
            for (AiResponseMapping mapping : dbMappings) {
                String sourceField = mapping.getSourceField();
                String targetField = mapping.getTargetField();
                String maskingType = mapping.getMaskingType();

                Object value = row.get(sourceField);
                Object maskedValue = maskingService.mask(value, maskingType);
                result.put(targetField, maskedValue);
            }
        } else {
            List<Map<String, Object>> maskedRows = new ArrayList<>();
            for (Map<String, Object> row : rawResults) {
                Map<String, Object> maskedRow = new LinkedHashMap<>();
                for (AiResponseMapping mapping : dbMappings) {
                    String sourceField = mapping.getSourceField();
                    String targetField = mapping.getTargetField();
                    String maskingType = mapping.getMaskingType();

                    Object value = row.get(sourceField);
                    Object maskedValue = maskingService.mask(value, maskingType);
                    maskedRow.put(targetField, maskedValue);
                }
                maskedRows.add(maskedRow);
            }
            result.put("data", maskedRows);
            result.put("count", maskedRows.size());
        }

        return result;
    }

    /**
     * Apply response_mapping to shape query results.
     * response_mapping format: {"outputField": "$.column_name"}
     */
    private Map<String, Object> applyResponseMapping(List<Map<String, Object>> rawResults, AiScenario scenario) {
        log.info("Applying response mapping for {}: {} rows",scenario.getScenarioCode(), rawResults.size());
        Map<String, Object> result = new LinkedHashMap<>();
        
        if (rawResults.isEmpty()) {
            result.put("data", List.of());
            result.put("count", 0);
            return result;
        }

        if (scenario.getResponseMapping() == null || scenario.getResponseMapping().isBlank()) {
            // No mapping, return raw results
            if (rawResults.size() == 1) {
                result.putAll(rawResults.get(0));
            } else {
                result.put("data", rawResults);
                result.put("count", rawResults.size());
            }
            return result;
        }

        try {
            Map<String, String> mappings = objectMapper.readValue(
                    scenario.getResponseMapping(), 
                    new TypeReference<Map<String, String>>() {}
            );

            if (rawResults.size() == 1) {
                // Single row - map fields directly
                Map<String, Object> row = rawResults.get(0);
                for (Map.Entry<String, String> entry : mappings.entrySet()) {
                    String outputField = entry.getKey();
                    String sourceField = extractFieldName(entry.getValue());
                    result.put(outputField, row.get(sourceField));
                }
            } else {
                // Multiple rows - map each row
                List<Map<String, Object>> mappedRows = new ArrayList<>();
                for (Map<String, Object> row : rawResults) {
                    Map<String, Object> mappedRow = new LinkedHashMap<>();
                    for (Map.Entry<String, String> entry : mappings.entrySet()) {
                        String outputField = entry.getKey();
                        String sourceField = extractFieldName(entry.getValue());
                        mappedRow.put(outputField, row.get(sourceField));
                    }
                    mappedRows.add(mappedRow);
                }
                result.put("data", mappedRows);
                result.put("count", mappedRows.size());
            }
        } catch (Exception e) {
            log.warn("Failed to apply response_mapping, returning raw: {}", e.getMessage());
            if (rawResults.size() == 1) {
                result.putAll(rawResults.get(0));
            } else {
                result.put("data", rawResults);
                result.put("count", rawResults.size());
            }
        }

        return result;
    }

    /**
     * Extract value from request using simple JSONPath.
     */
    private Object extractValue(ScenarioRequest request, String jsonPath) {
        // Simple extraction: $.params.fieldName -> request.getParams().get("fieldName")
        if (jsonPath.startsWith("$.params.")) {
            String fieldName = jsonPath.substring("$.params.".length());
            return request.getParams() != null ? request.getParams().get(fieldName) : null;
        }
        if (jsonPath.startsWith("$.")) {
            String fieldName = jsonPath.substring(2);
            if ("userId".equals(fieldName)) return request.getUserId();
            if ("sessionId".equals(fieldName)) return request.getSessionId();
            if ("scenario".equals(fieldName)) return request.getScenario();
        }
        return null;
    }

    /**
     * Extract field name from JSONPath (e.g., $.column_name -> column_name).
     */
    private String extractFieldName(String jsonPath) {
        if (jsonPath.startsWith("$.")) {
            return jsonPath.substring(2);
        }
        return jsonPath;
    }
}
