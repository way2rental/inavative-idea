package com.enterprise.ai.intelligence.kernel.rag;

import com.enterprise.ai.data.entity.AiScenario;
import com.enterprise.ai.data.service.ConfigCacheService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Intent Definition Retriever for RAG Engine.
 * 
 * Retrieves intent definitions from scenarios (AiScenario).
 * Uses scenario descriptions, trigger phrases, example queries.
 * 
 * REUSES existing components:
 * - ConfigCacheService for scenario retrieval
 * - AiScenario entity (already has trigger_phrases, example_queries, description)
 * 
 * NO HARDCODING - All intent definitions from database.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IntentRetriever {

    private final ConfigCacheService configCacheService;
    private final ObjectMapper objectMapper;

    /**
     * Retrieve intent definitions relevant to query.
     * 
     * @param query User query
     * @param allowedScenarios RBAC-filtered scenarios
     * @param maxResults Maximum number of results
     * @return Flux of RagRetrievalResult
     */
    public Flux<RagRetrievalResult> retrieve(String query, Set<String> allowedScenarios, int maxResults) {
        return Mono.fromCallable(() -> {
            try {
                // Get all active scenarios
                List<AiScenario> scenarios = configCacheService.getActiveScenarios();
                
                // Filter by allowed scenarios (RBAC)
                if (allowedScenarios != null && !allowedScenarios.isEmpty()) {
                    scenarios = scenarios.stream()
                            .filter(s -> allowedScenarios.contains(s.getScenarioCode()))
                            .collect(Collectors.toList());
                }
                
                // Score scenarios based on query match
                List<ScoredScenario> scoredScenarios = new ArrayList<>();
                
                String queryLower = query.toLowerCase();
                
                for (AiScenario scenario : scenarios) {
                    double score = scoreScenario(queryLower, scenario);
                    if (score > 0.0) {
                        scoredScenarios.add(new ScoredScenario(scenario, score));
                    }
                }
                
                // Sort by score (descending) and limit
                return scoredScenarios.stream()
                        .sorted((a, b) -> Double.compare(b.score, a.score))
                        .limit(maxResults)
                        .map(scored -> toRetrievalResult(scored.scenario, scored.score))
                        .collect(Collectors.toList());
            } catch (Exception e) {
                log.error("Failed to retrieve intent definitions: {}", e.getMessage(), e);
                return Collections.<RagRetrievalResult>emptyList();
            }
        })
        .flatMapMany(Flux::fromIterable);
    }
    
    /**
     * Score scenario based on query match.
     * Uses description, trigger phrases, example queries.
     */
    private double scoreScenario(String queryLower, AiScenario scenario) {
        double score = 0.0;
        
        // Match against description
        if (scenario.getDescription() != null) {
            String descLower = scenario.getDescription().toLowerCase();
            if (descLower.contains(queryLower)) {
                score += 0.3;
            }
            // Simple word matching
            String[] queryWords = queryLower.split("\\s+");
            for (String word : queryWords) {
                if (word.length() > 2 && descLower.contains(word)) {
                    score += 0.1;
                }
            }
        }
        
        // Match against trigger phrases
        if (scenario.getTriggerPhrases() != null && !scenario.getTriggerPhrases().isEmpty()) {
            try {
                List<String> triggerPhrases = objectMapper.readValue(
                        scenario.getTriggerPhrases(),
                        new TypeReference<List<String>>() {}
                );
                for (String phrase : triggerPhrases) {
                    if (queryLower.contains(phrase.toLowerCase())) {
                        score += 0.5; // Higher weight for trigger phrases
                    }
                }
            } catch (Exception e) {
                log.debug("Failed to parse trigger phrases for scenario {}: {}", scenario.getScenarioCode(), e.getMessage());
            }
        }
        
        // Match against example queries
        if (scenario.getExampleQueries() != null && !scenario.getExampleQueries().isEmpty()) {
            try {
                List<String> exampleQueries = objectMapper.readValue(
                        scenario.getExampleQueries(),
                        new TypeReference<List<String>>() {}
                );
                for (String example : exampleQueries) {
                    if (queryLower.contains(example.toLowerCase()) || example.toLowerCase().contains(queryLower)) {
                        score += 0.4;
                    }
                }
            } catch (Exception e) {
                log.debug("Failed to parse example queries for scenario {}: {}", scenario.getScenarioCode(), e.getMessage());
            }
        }
        
        // Normalize score to 0.0-1.0 range
        return Math.min(score, 1.0);
    }
    
    /**
     * Convert scenario to RagRetrievalResult.
     */
    private RagRetrievalResult toRetrievalResult(AiScenario scenario, double score) {
        // Build content from scenario definition
        StringBuilder content = new StringBuilder();
        content.append("Scenario: ").append(scenario.getScenarioName() != null ? scenario.getScenarioName() : scenario.getScenarioCode()).append("\n");
        if (scenario.getDescription() != null) {
            content.append("Description: ").append(scenario.getDescription()).append("\n");
        }
        if (scenario.getRequiredParams() != null) {
            content.append("Required Parameters: ").append(scenario.getRequiredParams()).append("\n");
        }
        
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("scenarioCode", scenario.getScenarioCode());
        metadata.put("scenarioName", scenario.getScenarioName());
        metadata.put("category", scenario.getCategory());
        metadata.put("executionType", scenario.getExecutionType());
        
        return RagRetrievalResult.builder()
                .sourceType("INTENT_DEFINITION")
                .content(content.toString())
                .relevanceScore(score)
                .metadata(metadata)
                .sourceId(scenario.getScenarioCode())
                .build();
    }
    
    /**
     * Helper class for scoring scenarios.
     */
    private static class ScoredScenario {
        final AiScenario scenario;
        final double score;
        
        ScoredScenario(AiScenario scenario, double score) {
            this.scenario = scenario;
            this.score = score;
        }
    }
}
