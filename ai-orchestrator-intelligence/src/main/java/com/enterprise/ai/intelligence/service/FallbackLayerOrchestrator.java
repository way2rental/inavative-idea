package com.enterprise.ai.intelligence.service;

import com.enterprise.ai.common.dto.IntentResult;
import com.enterprise.ai.data.entity.FallbackLayer;
import com.enterprise.ai.data.repository.FallbackLayerRepository;
import com.enterprise.ai.intelligence.service.conversational.ConversationalHandler;
import com.enterprise.ai.intelligence.service.embedding.EmbeddingMatcher;
import com.enterprise.ai.intelligence.service.entity.EntityExtractionService;
import com.enterprise.ai.intelligence.service.fallback.FallbackLayerService;
import com.enterprise.ai.intelligence.service.keyword.KeywordMatcher;
import com.enterprise.ai.intelligence.service.parameter.ParameterExtractionService;
import com.enterprise.ai.intelligence.service.rules.RuleEngineMatcher;
import com.enterprise.ai.intelligence.service.scenario.ScenarioTriggerMatcher;
import com.enterprise.ai.intelligence.service.ml.MlIntentClassifierService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Orchestrator for multi-layer fallback architecture.
 * Manages fallback layers and routes queries through them in priority order.
 * 
 * NO HARDCODING - All configuration from database.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FallbackLayerOrchestrator {

    private final FallbackLayerRepository layerRepository;
    private final EmbeddingMatcher embeddingMatcher;
    private final RuleEngineMatcher ruleEngineMatcher;
    private final KeywordMatcher keywordMatcher;
    private final ScenarioTriggerMatcher scenarioTriggerMatcher;
    private final ConversationalHandler conversationalHandler;
    private final EntityExtractionService entityExtractionService;
    private final ParameterExtractionService parameterExtractionService;
    private final MlIntentClassifierService mlIntentClassifierService;
    
    private List<FallbackLayer> enabledLayers = new ArrayList<>();
    private Map<String, FallbackLayerService> layerServiceMap = new HashMap<>();
    
    @PostConstruct
    public void init() {
        refreshLayers();
        log.info("FallbackLayerOrchestrator initialized with {} enabled layers", enabledLayers.size());
    }
    
    private void refreshLayers() {
        enabledLayers = layerRepository.findAllEnabledOrderByPriorityAsc();
        
        // Map layer codes to services
        layerServiceMap.put("ML_INTENT_CLASSIFIER", mlIntentClassifierService);
        layerServiceMap.put("EMBEDDING_SIMILARITY", embeddingMatcher);
        layerServiceMap.put("RULE_ENGINE", ruleEngineMatcher);
        layerServiceMap.put("KEYWORD_MATCHER", keywordMatcher);
        layerServiceMap.put("SCENARIO_TRIGGER_MATCHER", scenarioTriggerMatcher);
        layerServiceMap.put("CONVERSATIONAL_HANDLER", conversationalHandler);
    }

    /**
     * Detect intent using multi-layer fallback.
     * Tries each layer in priority order until one returns a confident result.
     * 
     * @param userInput user query
     * @param sessionContext conversation history
     * @param lastUsedParamsJson last used parameters
     * @param allowedScenarios RBAC-filtered scenarios
     * @param sessionId Session ID for context memory (optional)
     * @param userId User ID for context memory (optional)
     * @return Mono of IntentResult
     */
    public Mono<IntentResult> detectIntent(
            String userInput,
            String sessionContext,
            String lastUsedParamsJson,
            Set<String> allowedScenarios,
            String sessionId,
            String userId) {
        
        return detectIntentWithLayers(userInput, sessionContext, lastUsedParamsJson, allowedScenarios, enabledLayers, 0, sessionId, userId)
            .timeout(java.time.Duration.ofSeconds(10))
            .onErrorResume(e -> {
                log.error("Fallback layer orchestration failed: {}", e.getMessage(), e);
                return Mono.just(buildUnknownIntent("Error processing query: " + e.getMessage()));
            });
    }
    
    /**
     * Recursive helper to try layers in sequence
     */
    private Mono<IntentResult> detectIntentWithLayers(
            String userInput,
            String sessionContext,
            String lastUsedParamsJson,
            Set<String> allowedScenarios,
            List<FallbackLayer> layers,
            int layerIndex,
            String sessionId,
            String userId) {
        
        // If we've exhausted all layers, try scenario trigger matcher as final fallback
        if (layerIndex >= layers.size()) {
            log.debug("All configured fallback layers exhausted, trying scenario trigger matcher as final fallback");
            // Try scenario trigger matcher as final fallback (uses trigger_phrases from scenarios)
            FallbackLayerService scenarioTriggerService = layerServiceMap.get("SCENARIO_TRIGGER_MATCHER");
            if (scenarioTriggerService != null && scenarioTriggerService.isEnabled()) {
                // Create a temporary layer config for scenario trigger matcher
                FallbackLayer tempLayer = FallbackLayer.builder()
                    .layerCode("SCENARIO_TRIGGER_MATCHER")
                    .confidenceThreshold(0.5) // Lower threshold for trigger phrase matching
                    .timeoutMs(1000)
                    .build();
                
                return scenarioTriggerService.detectIntent(userInput, sessionContext, lastUsedParamsJson, allowedScenarios, tempLayer)
                    .flatMap(resultOpt -> {
                        if (resultOpt.isPresent()) {
                            IntentResult result = resultOpt.get();
                            if (result.getConfidence() >= 0.5) {
                                log.info("Scenario trigger matcher found match: scenario={}, confidence={}", 
                                    result.getScenario(), result.getConfidence());
                                return Mono.just(result);
                            }
                        }
                        log.debug("Scenario trigger matcher also returned no confident result, returning UNKNOWN");
                        return Mono.just(buildUnknownIntent("No matching scenario found after trying all layers including scenario triggers"));
                    })
                    .onErrorResume(e -> {
                        log.warn("Scenario trigger matcher failed: {}", e.getMessage());
                        return Mono.just(buildUnknownIntent("No matching scenario found"));
                    });
            }
            log.debug("All fallback layers exhausted (including scenario trigger matcher), returning UNKNOWN");
            return Mono.just(buildUnknownIntent("No matching scenario found"));
        }
        
        FallbackLayer layer = layers.get(layerIndex);
        FallbackLayerService service = layerServiceMap.get(layer.getLayerCode());
        
        // If service not found or not enabled, skip to next layer
        if (service == null || !service.isEnabled()) {
            log.debug("Layer {} not available (service={}, enabled={}), trying next layer", 
                layer.getLayerCode(), service != null ? "found" : "null", service != null ? service.isEnabled() : false);
            return detectIntentWithLayers(userInput, sessionContext, lastUsedParamsJson, allowedScenarios, layers, layerIndex + 1, sessionId, userId);
        }
        
        log.debug("Trying layer {} (priority: {}, threshold: {})", 
            layer.getLayerCode(), layer.getPriority(), layer.getConfidenceThreshold());
        
        // Try this layer
        return service.detectIntent(userInput, sessionContext, lastUsedParamsJson, allowedScenarios, layer)
            .flatMap(resultOpt -> {
                if (resultOpt.isPresent()) {
                    IntentResult result = resultOpt.get();
                    
                    // Check if result meets confidence threshold
                    if (result.getConfidence() >= layer.getConfidenceThreshold()) {
                        log.debug("Layer {} returned confident result: scenario={}, confidence={}", 
                            layer.getLayerCode(), result.getScenario(), result.getConfidence());
                        
                        // Enhance result with extracted parameters if scenario is detected and session info available
                        if (result.getScenario() != null && !result.getScenario().equals("UNKNOWN") 
                            && !result.getScenario().equals("AMBIGUOUS") && sessionId != null && userId != null) {
                            return enhanceIntentResultWithParameters(result, userInput, sessionContext, allowedScenarios, sessionId, userId)
                                .defaultIfEmpty(result);
                        }
                        
                        return Mono.just(result);
                    } else {
                        log.debug("Layer {} returned low confidence result: {}, trying next layer", 
                            layer.getLayerCode(), result.getConfidence());
                        // Fall through to next layer
                        return detectIntentWithLayers(userInput, sessionContext, lastUsedParamsJson, allowedScenarios, layers, layerIndex + 1, sessionId, userId);
                    }
                } else {
                    log.debug("Layer {} returned no result, trying next layer", layer.getLayerCode());
                    // Layer returned empty - try next layer
                    return detectIntentWithLayers(userInput, sessionContext, lastUsedParamsJson, allowedScenarios, layers, layerIndex + 1, sessionId, userId);
                }
            })
            .onErrorResume(e -> {
                log.warn("Layer {} failed: {}, trying next layer", layer.getLayerCode(), e.getMessage());
                // Layer failed - try next layer
                return detectIntentWithLayers(userInput, sessionContext, lastUsedParamsJson, allowedScenarios, layers, layerIndex + 1, sessionId, userId);
            });
    }
    
    /**
     * Enhance intent result with extracted parameters.
     */
    private Mono<IntentResult> enhanceIntentResultWithParameters(IntentResult intentResult, 
                                                                 String userInput, 
                                                                 String sessionContext,
                                                                 Set<String> allowedScenarios,
                                                                 String sessionId,
                                                                 String userId) {
        return parameterExtractionService.extractParameters(
                userInput, intentResult.getScenario(), sessionId, userId)
            .flatMap(params -> {
                // Merge extracted parameters with existing params
                Map<String, Object> mergedParams = new HashMap<>();
                if (intentResult.getParams() != null) {
                    mergedParams.putAll(intentResult.getParams());
                }
                mergedParams.putAll(params);
                
                // Get missing parameters
                return parameterExtractionService.getMissingParameters(
                    userInput, intentResult.getScenario(), sessionId, userId)
                    .map(missingParams -> {
                        return IntentResult.builder()
                            .scenario(intentResult.getScenario())
                            .confidence(intentResult.getConfidence())
                            .params(mergedParams)
                            .missingParams(missingParams)
                            .possibleScenarios(intentResult.getPossibleScenarios())
                            .reasoning(intentResult.getReasoning() + " | Parameters extracted: " + mergedParams.keySet())
                            .build();
                    });
            })
            .onErrorResume(e -> {
                log.warn("Failed to extract parameters: {}", e.getMessage());
                return Mono.just(intentResult);
            });
    }
    
    /**
     * Build unknown intent result
     */
    private IntentResult buildUnknownIntent(String reasoning) {
        return IntentResult.builder()
            .scenario("UNKNOWN")
            .confidence(0.1)
            .params(new HashMap<>())
            .reasoning(reasoning)
            .build();
    }
    
    /**
     * Refresh layers from database (public method for refresh)
     */
    public void refreshLayersFromDb() {
        log.info("Refreshing fallback layers from database...");
        enabledLayers = layerRepository.findAllEnabledOrderByPriorityAsc();
        
        // Re-map layer codes to services
        layerServiceMap.put("ML_INTENT_CLASSIFIER", mlIntentClassifierService);
        layerServiceMap.put("EMBEDDING_SIMILARITY", embeddingMatcher);
        layerServiceMap.put("RULE_ENGINE", ruleEngineMatcher);
        layerServiceMap.put("KEYWORD_MATCHER", keywordMatcher);
        layerServiceMap.put("SCENARIO_TRIGGER_MATCHER", scenarioTriggerMatcher);
        layerServiceMap.put("CONVERSATIONAL_HANDLER", conversationalHandler);
        
        log.info("Layers refreshed: {} enabled layers", enabledLayers.size());
    }
    
    /**
     * Get all enabled layers
     */
    public List<FallbackLayer> getEnabledLayers() {
        return new ArrayList<>(enabledLayers);
    }
}
