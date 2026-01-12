package com.enterprise.ai.intelligence.kernel.tool;

import com.enterprise.ai.common.dto.ReasoningPlan;
import com.enterprise.ai.common.dto.ScenarioResult;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Tool Dispatcher - Deterministic Execution.
 * 
 * Executes real-world actions decided by Reasoning Planner.
 * 
 * Responsibilities:
 * - Route to correct executor based on capability/tools
 * - Execute DB queries
 * - Call internal services
 * - Call external APIs
 * - Return structured results
 * 
 * STRICT RULE:
 * ❗ NO LLM is allowed inside tool execution.
 * 
 * REUSES:
 * - DynamicScenarioRouter from core module
 * - Existing executors (QueryExecutor, HttpCallExecutor, LlmOnlyExecutor)
 */
public interface ToolDispatcher {

    /**
     * Execute tools based on reasoning plan.
     * 
     * @param reasoningPlan Output from Reasoning Planner
     * @param userId User ID
     * @param sessionId Session ID
     * @return Mono of ScenarioResult
     */
    Mono<ScenarioResult> execute(ReasoningPlan reasoningPlan, String userId, String sessionId);

    /**
     * Execute with custom parameters (override reasoning plan parameters).
     * 
     * @param reasoningPlan Output from Reasoning Planner
     * @param parameters Parameters to use (can override reasoning plan parameters)
     * @param userId User ID
     * @param sessionId Session ID
     * @return Mono of ScenarioResult
     */
    Mono<ScenarioResult> execute(ReasoningPlan reasoningPlan, Map<String, Object> parameters, String userId, String sessionId);
}
