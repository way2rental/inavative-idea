package com.enterprise.ai.llm.client;

import com.enterprise.ai.common.dto.IntentResult;
import com.enterprise.ai.common.dto.ScenarioResult;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Reactive LLM client interface for non-blocking Ollama interactions.
 * Supports both Mono (single response) and Flux (streaming) operations.
 */
public interface ReactiveLlmClient {

    /**
     * Detect intent from user input (non-blocking).
     * Uses enterprise-intent model for accurate classification.
     *
     * @param userInput user's query
     * @param sessionContext previous conversation context
     * @return Mono of IntentResult
     */
    Mono<IntentResult> detectIntent(String userInput, String sessionContext);

    /**
     * Two-stage intent detection: First detect category, then exact scenario.
     * Optimized for 150+ scenarios where single-stage detection is too slow.
     *
     * @param userInput user's query
     * @param sessionContext previous conversation context
     * @return Mono of IntentResult with category-aware detection
     */
    Mono<IntentResult> detectIntentTwoStage(String userInput, String sessionContext);

    /**
     * Generate follow-up question for missing parameters (non-blocking).
     *
     * @param scenarioCode scenario code
     * @param missingParams list of missing parameters
     * @return Mono of follow-up question string
     */
    Mono<String> generateFollowUpQuestion(String scenarioCode, List<String> missingParams);

    /**
     * Format response using LLM (non-blocking).
     * Uses enterprise-formatter model for consistent formatting.
     *
     * @param scenarioCode scenario code
     * @param result scenario execution result
     * @param userQuery original user query
     * @return Mono of formatted response string
     */
    Mono<String> formatResponse(String scenarioCode, ScenarioResult result, String userQuery);

    /**
     * Format response with streaming (SSE support).
     * Returns response token by token for real-time UI updates.
     *
     * @param scenarioCode scenario code
     * @param result scenario execution result
     * @param userQuery original user query
     * @return Flux of response tokens (characters/words)
     */
    Flux<String> formatResponseStreaming(String scenarioCode, ScenarioResult result, String userQuery);

    /**
     * Check if LLM client is healthy and ready.
     *
     * @return Mono of boolean indicating health status
     */
    Mono<Boolean> isHealthy();
}
