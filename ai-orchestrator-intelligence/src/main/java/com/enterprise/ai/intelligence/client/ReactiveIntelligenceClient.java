package com.enterprise.ai.intelligence.client;

import com.enterprise.ai.common.dto.IntentResult;
import com.enterprise.ai.common.dto.ScenarioResult;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Set;

/**
 * Reactive Intelligence Client interface.
 * Implements the ReactiveLlmClient interface with fully DB-driven fallback architecture.
 * 
 * NO HARDCODING - All configuration comes from database.
 * Fully configurable via Admin Panel.
 */
public interface ReactiveIntelligenceClient {

    /**
     * Detect intent from user input using multi-layer fallback (non-blocking).
     * Falls through layers until acceptable confidence is achieved.
     * 
     * @param userInput user's query
     * @param sessionContext previous conversation context
     * @return Mono of IntentResult
     */
    Mono<IntentResult> detectIntent(String userInput, String sessionContext);

    /**
     * Detect intent with last used params context (non-blocking).
     * Enables resolution of references like "same account", "that transaction".
     * 
     * @param userInput user's query
     * @param sessionContext previous conversation context
     * @param lastUsedParamsJson JSON of last used params for reference resolution
     * @return Mono of IntentResult
     */
    Mono<IntentResult> detectIntent(String userInput, String sessionContext, String lastUsedParamsJson);

    /**
     * Detect intent with RBAC filtering - only shows scenarios the user can access.
     * This is the preferred method for production use.
     * 
     * @param userInput user's query
     * @param sessionContext previous conversation context
     * @param lastUsedParamsJson JSON of last used params for reference resolution
     * @param allowedScenarios Set of scenario codes the user's roles can access
     * @return Mono of IntentResult
     */
    Mono<IntentResult> detectIntent(String userInput, String sessionContext, String lastUsedParamsJson, Set<String> allowedScenarios);

    /**
     * Detect intent with explicit sessionId and userId for context memory and parameter extraction.
     * This method is preferred when sessionId and userId are available from the request.
     * 
     * @param userInput user's query
     * @param sessionContext previous conversation context
     * @param lastUsedParamsJson JSON of last used params for reference resolution
     * @param allowedScenarios Set of scenario codes the user's roles can access
     * @param sessionId Session ID for context memory
     * @param userId User ID for context memory and auditing
     * @return Mono of IntentResult
     */
    Mono<IntentResult> detectIntent(String userInput, String sessionContext, String lastUsedParamsJson, Set<String> allowedScenarios, String sessionId, String userId);

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
     * Uses DB-driven templates from ai_followup_templates.
     * 
     * @param scenarioCode scenario code
     * @param missingParams list of missing parameters
     * @return Mono of follow-up question string
     */
    Mono<String> generateFollowUpQuestion(String scenarioCode, List<String> missingParams);

    /**
     * Format response using DB-driven templates (non-blocking).
     * Uses Freemarker templates from ai_response_templates.
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
     * Check if intelligence client is healthy and ready.
     * 
     * @return Mono of boolean indicating health status
     */
    Mono<Boolean> isHealthy();
}
