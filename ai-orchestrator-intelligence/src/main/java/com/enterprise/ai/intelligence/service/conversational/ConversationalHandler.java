package com.enterprise.ai.intelligence.service.conversational;

import com.enterprise.ai.common.dto.IntentResult;
import com.enterprise.ai.data.entity.ConversationalResponse;
import com.enterprise.ai.data.entity.FallbackLayer;
import com.enterprise.ai.data.repository.ConversationalResponseRepository;
import com.enterprise.ai.data.service.SystemConfigService;
import com.enterprise.ai.intelligence.service.fallback.FallbackLayerService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import freemarker.template.Configuration;
import freemarker.template.Template;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import jakarta.annotation.PostConstruct;
import java.io.StringWriter;
import java.util.*;

/**
 * Conversational intent handler (catch-all layer).
 * Handles greetings, thanks, goodbye, unknown queries.
 * 
 * NO HARDCODING - All responses from database.
 * Uses Freemarker templates with SystemConfig variables.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationalHandler implements FallbackLayerService {

    private final ConversationalResponseRepository responseRepository;
    private final SystemConfigService systemConfigService;
    private final Configuration freemarkerConfig;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    private Map<String, ConversationalResponse> responseMap = new HashMap<>();
    
    @PostConstruct
    public void init() {
        List<ConversationalResponse> responses = responseRepository.findAllActiveOrderByPriorityDesc();
        responseMap.clear();
        for (ConversationalResponse response : responses) {
            responseMap.put(response.getIntentType().toUpperCase(), response);
        }
        log.info("ConversationalHandler initialized with {} response types", responseMap.size());
    }
    
    private void refreshResponses() {
        List<ConversationalResponse> responses = responseRepository.findAllActiveOrderByPriorityDesc();
        responseMap.clear();
        for (ConversationalResponse response : responses) {
            responseMap.put(response.getIntentType().toUpperCase(), response);
        }
    }

    @Override
    public String getLayerCode() {
        return "CONVERSATIONAL_HANDLER";
    }

    @Override
    public boolean isEnabled() {
        return !responseMap.isEmpty();
    }

    @Override
    public Mono<Optional<IntentResult>> detectIntent(
            String userInput,
            String sessionContext,
            String lastUsedParamsJson,
            Set<String> allowedScenarios,
            FallbackLayer layerConfig) {
        
        // This is the catch-all layer - always enabled if we reach here
        return Mono.<Optional<IntentResult>>fromCallable(() -> {
            try {
                String queryLower = userInput.toLowerCase().trim();
                
                // Check for greetings
                if (matchesPattern(queryLower, "GREETING")) {
                    return buildConversationalIntent("GREETING", layerConfig);
                }
                
                // Check for thanks
                if (matchesPattern(queryLower, "THANKS")) {
                    return buildConversationalIntent("THANKS", layerConfig);
                }
                
                // Check for goodbye
                if (matchesPattern(queryLower, "GOODBYE")) {
                    return buildConversationalIntent("GOODBYE", layerConfig);
                }
                
                // Check for help
                if (matchesPattern(queryLower, "HELP")) {
                    return buildConversationalIntent("HELP", layerConfig);
                }
                
                // Unknown - always return this as fallback
                return buildConversationalIntent("UNKNOWN", layerConfig);
                
            } catch (Exception e) {
                log.error("Conversational handling failed: {}", e.getMessage(), e);
                return buildConversationalIntent("UNKNOWN", layerConfig);
            }
        })
        .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic())
        .timeout(java.time.Duration.ofMillis(layerConfig.getTimeoutMs()))
        .onErrorResume(e -> Mono.just(buildUnknownIntent(layerConfig)));
    }
    
    /**
     * Check if query matches a response pattern
     */
    private boolean matchesPattern(String queryLower, String intentType) {
        ConversationalResponse response = responseMap.get(intentType);
        if (response == null || response.getUserQueryPatterns() == null) {
            return false;
        }
        
        try {
            List<String> patterns = objectMapper.readValue(
                response.getUserQueryPatterns(),
                new TypeReference<List<String>>() {}
            );
            
            for (String pattern : patterns) {
                if (queryLower.contains(pattern.toLowerCase()) || queryLower.equals(pattern.toLowerCase())) {
                    return true;
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse patterns for {}: {}", intentType, e.getMessage());
        }
        
        return false;
    }
    
    /**
     * Build conversational intent result
     */
    private Optional<IntentResult> buildConversationalIntent(String intentType, FallbackLayer layerConfig) {
        ConversationalResponse response = responseMap.get(intentType);
        if (response == null) {
            response = responseMap.get("UNKNOWN");
        }
        
        if (response == null) {
            return buildUnknownIntent(layerConfig);
        }
        
        // Process template with SystemConfig variables
        String message = processTemplate(response.getResponseTemplate());
        
        return Optional.of(IntentResult.builder()
            .scenario("UNKNOWN")
            .confidence(0.1) // Low confidence for conversational responses
            .params(Map.of("message", message, "intentType", intentType))
            .reasoning("Conversational response: " + intentType)
            .build());
    }
    
    /**
     * Build unknown intent (fallback)
     */
    private Optional<IntentResult> buildUnknownIntent(FallbackLayer layerConfig) {
        return Optional.of(IntentResult.builder()
            .scenario("UNKNOWN")
            .confidence(0.1)
            .params(Map.of("message", "I'm not sure I understand. Could you please rephrase?"))
            .reasoning("Unknown query - no matches in any layer")
            .build());
    }
    
    /**
     * Process Freemarker template with SystemConfig variables
     */
    private String processTemplate(String templateString) {
        try {
            Template template = new Template("response", templateString, freemarkerConfig);
            Map<String, Object> dataModel = new HashMap<>();
            
            // Add SystemConfig variables
            dataModel.put("assistantName", systemConfigService.getAssistantName());
            dataModel.put("orgName", systemConfigService.getOrgName());
            // Add more variables as needed
            
            StringWriter writer = new StringWriter();
            template.process(dataModel, writer);
            return writer.toString();
            
        } catch (Exception e) {
            log.warn("Failed to process template: {}", e.getMessage());
            return templateString; // Return template as-is if processing fails
        }
    }

    @Override
    public Mono<Boolean> isHealthy() {
        return Mono.just(isEnabled());
    }
    
    /**
     * Refresh responses from database
     */
    public void refreshResponsesFromDb() {
        log.info("Refreshing conversational responses from database...");
        List<ConversationalResponse> responses = responseRepository.findAllActiveOrderByPriorityDesc();
        responseMap.clear();
        for (ConversationalResponse response : responses) {
            responseMap.put(response.getIntentType().toUpperCase(), response);
        }
        log.info("Responses refreshed: {} response types", responseMap.size());
    }
}
