package com.enterprise.ai.core.scenario;

import com.enterprise.ai.common.dto.ScenarioRequest;
import com.enterprise.ai.common.dto.ScenarioResult;
import com.enterprise.ai.common.enums.ExecutionType;
import com.enterprise.ai.core.security.ReadOnlyEnforcementService;
import com.enterprise.ai.data.entity.AiScenario;
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
    private final ObjectMapper objectMapper;

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

            // Build parameters from request_mapping
            Map<String, Object> sqlParams = buildSqlParameters(request, scenario);

            log.info("Executing DB_QUERY for scenario {} with params: {}", scenarioCode, sqlParams.keySet());

            // Execute query with timeout
            List<Map<String, Object>> rawResults = jdbcTemplate.queryForList(sqlQuery, sqlParams);
            
            // Limit result size to prevent memory exhaustion
            if (rawResults.size() > MAX_RESULT_SIZE) {
                log.warn("Query returned {} rows, truncating to {} for scenario {}", 
                        rawResults.size(), MAX_RESULT_SIZE, scenarioCode);
                rawResults = rawResults.subList(0, MAX_RESULT_SIZE);
            }

            // Apply response_mapping to shape output
            Map<String, Object> shapedResult = applyResponseMapping(rawResults, scenario);

            long executionTime = System.currentTimeMillis() - startTime;
            log.info("DB_QUERY for {} completed in {}ms, {} rows returned", 
                    scenarioCode, executionTime, rawResults.size());

            return ScenarioResult.builder()
                    .scenario(scenarioCode)
                    .success(true)
                    .data(shapedResult)
                    .build();

        } catch (Exception e) {
            log.error("DB_QUERY execution failed for {}: {}", scenarioCode, e.getMessage());
            return ScenarioResult.builder()
                    .scenario(scenarioCode)
                    .success(false)
                    .errorMessage("Database query failed: " + e.getMessage())
                    .data(Map.of("error", e.getMessage()))
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
     * Apply response_mapping to shape query results.
     * response_mapping format: {"outputField": "$.column_name"}
     */
    private Map<String, Object> applyResponseMapping(List<Map<String, Object>> rawResults, AiScenario scenario) {
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
