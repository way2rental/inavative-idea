package com.enterprise.ai.intelligence.kernel.tool;

import com.enterprise.ai.common.dto.ReasoningPlan;
import com.enterprise.ai.common.dto.ScenarioRequest;
import com.enterprise.ai.common.dto.ScenarioResult;
import com.enterprise.ai.core.router.DynamicScenarioRouter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * Tool Dispatcher Implementation.
 * 
 * REUSES:
 * - DynamicScenarioRouter from core module
 * - Existing executors (QueryExecutor, HttpCallExecutor, LlmOnlyExecutor)
 * 
 * NO LLM calls inside tool execution.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ToolDispatcherImpl implements ToolDispatcher {

    private final DynamicScenarioRouter scenarioRouter;

    @Override
    public Mono<ScenarioResult> execute(ReasoningPlan reasoningPlan, String userId, String sessionId) {
        return execute(reasoningPlan, null, userId, sessionId);
    }

    @Override
    public Mono<ScenarioResult> execute(ReasoningPlan reasoningPlan, Map<String, Object> parameters, String userId, String sessionId) {
        return Mono.defer(() -> {
            try {
                log.debug("Dispatching tools for intent: {}, capability: {}", 
                        reasoningPlan.getIntent(), reasoningPlan.getCapability());

                // Handle DIRECT_ANSWER capability (no tool execution needed)
                if ("DIRECT_ANSWER".equals(reasoningPlan.getCapability())) {
                    log.debug("DIRECT_ANSWER capability - no tool execution needed");
                    return Mono.just(ScenarioResult.builder()
                            .scenario(reasoningPlan.getIntent() != null ? reasoningPlan.getIntent() : "UNKNOWN")
                            .success(true)
                            .data(Map.of(
                                    "capability", "DIRECT_ANSWER",
                                    "message", "Direct answer - no tool execution required"
                            ))
                            .build());
                }

                // For other capabilities, execute the scenario
                String scenarioCode = reasoningPlan.getIntent();
                if (scenarioCode == null || "UNKNOWN".equals(scenarioCode)) {
                    log.warn("Cannot execute tool - no valid intent in reasoning plan");
                    return Mono.just(ScenarioResult.builder()
                            .scenario("UNKNOWN")
                            .success(false)
                            .errorMessage("No valid intent to execute")
                            .data(Map.of("error", "NO_INTENT"))
                            .build());
                }

                // Build parameters from reasoning plan (merge with custom parameters if provided)
                Map<String, Object> executionParams = new HashMap<>();
                if (reasoningPlan.getParameters() != null) {
                    executionParams.putAll(reasoningPlan.getParameters());
                }
                if (parameters != null) {
                    executionParams.putAll(parameters);
                }

                // Build scenario request
                ScenarioRequest scenarioRequest = ScenarioRequest.builder()
                        .scenario(scenarioCode)
                        .params(executionParams)
                        .userId(userId)
                        .sessionId(sessionId)
                        .build();

                // Route to executor (reactive)
                log.debug("Routing scenario {} to executor via DynamicScenarioRouter", scenarioCode);
                return scenarioRouter.routeReactive(scenarioRequest)
                        .doOnSuccess(result -> log.debug("Tool execution completed for scenario: {}, success: {}", 
                                scenarioCode, result.isSuccess()))
                        .doOnError(error -> log.error("Tool execution failed for scenario: {}", scenarioCode, error));

            } catch (Exception e) {
                log.error("Failed to dispatch tools: {}", e.getMessage(), e);
                return Mono.just(ScenarioResult.builder()
                        .scenario(reasoningPlan.getIntent() != null ? reasoningPlan.getIntent() : "UNKNOWN")
                        .success(false)
                        .errorMessage("Tool execution failed: " + e.getMessage())
                        .data(Map.of("error", "EXECUTION_ERROR", "message", e.getMessage()))
                        .build());
            }
        });
    }
}
