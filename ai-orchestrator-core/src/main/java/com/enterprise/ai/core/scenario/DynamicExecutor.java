package com.enterprise.ai.core.scenario;

import com.enterprise.ai.common.dto.ScenarioRequest;
import com.enterprise.ai.common.dto.ScenarioResult;
import com.enterprise.ai.data.entity.AiScenario;
import reactor.core.publisher.Mono;

/**
 * Dynamic executor interface for config-driven scenario execution.
 * Executors are selected based on execution_type, not scenario_code.
 * 
 * This enables:
 * - 150+ scenarios with only 2-4 executor implementations
 * - No code changes when adding new scenarios
 * - DB-driven configuration and rollback
 */
public interface DynamicExecutor {

    /**
     * Check if this executor supports the given execution type.
     * @param executionType the execution type (DB_QUERY, HTTP_CALL, etc.)
     * @return true if this executor can handle the type
     */
    boolean supports(String executionType);

    /**
     * Execute the scenario using the configuration from AiScenario.
     * @param request the scenario request with params
     * @param scenario the scenario configuration from database
     * @return scenario result with data
     */
    ScenarioResult execute(ScenarioRequest request, AiScenario scenario);

    /**
     * Execute the scenario reactively (non-blocking).
     * @param request the scenario request with params
     * @param scenario the scenario configuration from database
     * @return Mono of scenario result
     */
    Mono<ScenarioResult> executeReactive(ScenarioRequest request, AiScenario scenario);

    /**
     * Execute in dry-run mode (no real backend calls).
     * Returns what would have been executed.
     * @param request the scenario request with params
     * @param scenario the scenario configuration from database
     * @return dry-run result showing the planned execution
     */
    default ScenarioResult executeDryRun(ScenarioRequest request, AiScenario scenario) {
        return ScenarioResult.builder()
                .scenario(scenario.getScenarioCode())
                .success(true)
                .data(java.util.Map.of(
                        "dryRun", true,
                        "executionType", scenario.getExecutionType(),
                        "wouldExecute", buildDryRunDescription(request, scenario)
                ))
                .build();
    }

    /**
     * Build a description of what would be executed in dry-run mode.
     */
    default String buildDryRunDescription(ScenarioRequest request, AiScenario scenario) {
        return String.format("Execute %s for scenario %s with params %s",
                scenario.getExecutionType(), 
                scenario.getScenarioCode(),
                request.getParams());
    }
}
