package com.enterprise.ai.intelligence.client;

import com.enterprise.ai.common.dto.IntentResult;
import com.enterprise.ai.common.dto.ScenarioResult;
import com.enterprise.ai.intelligence.service.FallbackLayerOrchestrator;
import com.enterprise.ai.intelligence.service.formatting.ResponseFormatterService;
import com.enterprise.ai.intelligence.service.formatting.FollowUpQuestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Set;

/**
 * Implementation of ReactiveIntelligenceClient.
 * Fully DB-driven intelligence system with multi-layer fallback.
 * 
 * NO HARDCODING - All configuration from database.
 * Replaces Spring AI completely.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReactiveIntelligenceClientImpl implements ReactiveIntelligenceClient {

    private final FallbackLayerOrchestrator orchestrator;
    private final ResponseFormatterService responseFormatter;
    private final FollowUpQuestionService followUpQuestionService;
    private final com.enterprise.ai.intelligence.service.parameter.ParameterExtractionService parameterExtractionService;

    @Override
    public Mono<IntentResult> detectIntent(String userInput, String sessionContext) {
        return detectIntent(userInput, sessionContext, null, null);
    }

    @Override
    public Mono<IntentResult> detectIntent(String userInput, String sessionContext, String lastUsedParamsJson) {
        return detectIntent(userInput, sessionContext, lastUsedParamsJson, null);
    }

    @Override
    public Mono<IntentResult> detectIntent(
            String userInput,
            String sessionContext,
            String lastUsedParamsJson,
            Set<String> allowedScenarios) {
        
        log.debug("Detecting intent for query: {}", userInput);
        
        // Extract sessionId and userId from context (simplified - will be passed from ChatService)
        String sessionId = extractSessionId(sessionContext);
        String userId = extractUserId(sessionContext);
        
        return detectIntent(userInput, sessionContext, lastUsedParamsJson, allowedScenarios, sessionId, userId);
    }

    @Override
    public Mono<IntentResult> detectIntent(
            String userInput,
            String sessionContext,
            String lastUsedParamsJson,
            Set<String> allowedScenarios,
            String sessionId,
            String userId) {
        
        log.debug("Detecting intent for query: {} (sessionId: {}, userId: {})", userInput, sessionId, userId);
        
        return orchestrator.detectIntent(userInput, sessionContext, lastUsedParamsJson, allowedScenarios, sessionId, userId)
            .doOnSuccess(result -> 
                log.info("Intent detected: scenario={}, confidence={}, layer={}", 
                    result.getScenario(), result.getConfidence(), 
                    result.getReasoning() != null && result.getReasoning().contains("via") 
                        ? extractLayerName(result.getReasoning()) : "unknown"))
            .doOnError(e -> log.error("Intent detection failed: {}", e.getMessage()));
    }

    @Override
    public Mono<IntentResult> detectIntentTwoStage(String userInput, String sessionContext) {
        // Two-stage detection: First detect category, then exact scenario
        // For now, use single-stage (can be enhanced later with category detection)
        log.debug("Two-stage intent detection requested, using single-stage fallback");
        return detectIntent(userInput, sessionContext);
    }

    @Override
    public Mono<String> generateFollowUpQuestion(String scenarioCode, List<String> missingParams) {
        log.debug("Generating follow-up question for scenario: {}, missing params: {}", scenarioCode, missingParams);
        
        return followUpQuestionService.generateQuestion(scenarioCode, missingParams)
            .doOnError(e -> log.error("Follow-up question generation failed: {}", e.getMessage()));
    }

    @Override
    public Mono<String> formatResponse(String scenarioCode, ScenarioResult result, String userQuery) {
        log.debug("Formatting response for scenario: {}", scenarioCode);
        
        return responseFormatter.formatResponse(scenarioCode, result, userQuery)
            .doOnError(e -> log.error("Response formatting failed: {}", e.getMessage()));
    }

    @Override
    public Flux<String> formatResponseStreaming(String scenarioCode, ScenarioResult result, String userQuery) {
        log.debug("Streaming response for scenario: {}", scenarioCode);
        
        // Format the complete response and emit it as a single chunk
        // For formatted responses (from templates), we don't need character-by-character streaming
        // The entire formatted response should be sent as one chunk for better performance
        return formatResponse(scenarioCode, result, userQuery)
            .flatMapMany(response -> {
                // For pre-formatted responses from templates, emit as single chunk
                // If you need word-by-word or sentence-by-sentence streaming later, 
                // you can split by words/sentences instead of characters
                if (response != null && !response.isEmpty()) {
                    log.debug("Streaming complete formatted response (length: {})", response.length());
                    return Flux.just(response);
                } else {
                    log.warn("Empty response for scenario: {}", scenarioCode);
                    return Flux.empty();
                }
            })
            .doOnError(e -> log.error("Streaming response formatting failed: {}", e.getMessage()));
    }

    @Override
    public Mono<Boolean> isHealthy() {
        return orchestrator.getEnabledLayers().stream()
            .map(layer -> layer.getLayerCode())
            .map(layerCode -> {
                // Check if at least one layer is healthy
                // This is a simplified health check - can be enhanced
                return true;
            })
            .findFirst()
            .map(Mono::just)
            .orElse(Mono.just(false));
    }
    
    /**
     * Extract layer name from reasoning string
     */
    private String extractLayerName(String reasoning) {
        if (reasoning == null) return "unknown";
        if (reasoning.contains("embedding")) return "EMBEDDING";
        if (reasoning.contains("rule")) return "RULES";
        if (reasoning.contains("keyword")) return "KEYWORDS";
        if (reasoning.contains("conversational")) return "CONVERSATIONAL";
        return "unknown";
    }
    
    /**
     * Extract session ID from context.
     * This is a fallback method when sessionId is not explicitly passed.
     * ChatService should pass sessionId directly, but this provides fallback parsing.
     */
    private String extractSessionId(String sessionContext) {
        if (sessionContext == null || sessionContext.isEmpty()) {
            return null;
        }
        
        // Try to extract from context format: "[SESSION]session-id-123"
        if (sessionContext.contains("[SESSION]")) {
            int start = sessionContext.indexOf("[SESSION]") + 9;
            int end = sessionContext.indexOf("\n", start);
            if (end == -1) end = sessionContext.length();
            String sessionId = sessionContext.substring(start, end).trim();
            if (!sessionId.isEmpty()) {
                log.debug("Extracted session ID from context: {}", sessionId);
                return sessionId;
            }
        }
        
        // Try to extract from JSON format if context contains JSON
        try {
            if (sessionContext.trim().startsWith("{")) {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                com.fasterxml.jackson.databind.JsonNode json = mapper.readTree(sessionContext);
                if (json.has("sessionId")) {
                    String sessionId = json.get("sessionId").asText();
                    log.debug("Extracted session ID from JSON context: {}", sessionId);
                    return sessionId;
                }
            }
        } catch (Exception e) {
            log.debug("Failed to parse session ID from context JSON: {}", e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Extract user ID from context.
     * This is a fallback method when userId is not explicitly passed.
     * ChatService should pass userId directly, but this provides fallback parsing.
     */
    private String extractUserId(String sessionContext) {
        if (sessionContext == null || sessionContext.isEmpty()) {
            return null;
        }
        
        // Try to extract from JSON format if context contains JSON
        try {
            if (sessionContext.trim().startsWith("{")) {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                com.fasterxml.jackson.databind.JsonNode json = mapper.readTree(sessionContext);
                if (json.has("userId")) {
                    String userId = json.get("userId").asText();
                    log.debug("Extracted user ID from JSON context: {}", userId);
                    return userId;
                }
                if (json.has("user_id")) {
                    String userId = json.get("user_id").asText();
                    log.debug("Extracted user ID from JSON context: {}", userId);
                    return userId;
                }
            }
        } catch (Exception e) {
            log.debug("Failed to parse user ID from context JSON: {}", e.getMessage());
        }
        
        return null;
    }
}
