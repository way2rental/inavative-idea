package com.enterprise.ai.intelligence.kernel.rag;

import com.enterprise.ai.data.entity.AiScenario;
import com.enterprise.ai.data.service.ConfigCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Tool Definition Retriever for RAG Engine.
 * 
 * Retrieves tool definitions from scenarios (AiScenario execution config).
 * Shows what the system CAN DO (DB queries, API calls, Business actions).
 * 
 * REUSES existing components:
 * - ConfigCacheService for scenario retrieval
 * - AiScenario entity (already has execution_type, sql_query, http_url)
 * 
 * NO HARDCODING - All tool definitions from database.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ToolRetriever {

    private final ConfigCacheService configCacheService;

    /**
     * Retrieve tool definitions relevant to query.
     * 
     * @param query User query
     * @param executionTypes Optional execution types to filter
     * @param maxResults Maximum number of results
     * @return Flux of RagRetrievalResult
     */
    public Flux<RagRetrievalResult> retrieve(String query, List<String> executionTypes, int maxResults) {
        return Mono.fromCallable(() -> {
            try {
                // Get all active scenarios
                List<AiScenario> scenarios = configCacheService.getActiveScenarios();
                
                // Filter by execution type if specified
                if (executionTypes != null && !executionTypes.isEmpty()) {
                    scenarios = scenarios.stream()
                            .filter(s -> executionTypes.contains(s.getExecutionType()))
                            .collect(Collectors.toList());
                }
                
                // Filter out LLM_ONLY scenarios (they're not tools)
                scenarios = scenarios.stream()
                        .filter(s -> s.getExecutionType() != null && !s.getExecutionType().equals("LLM_ONLY"))
                        .collect(Collectors.toList());
                
                // Score scenarios based on query match
                List<ScoredScenario> scoredScenarios = new ArrayList<>();
                String queryLower = query.toLowerCase();
                
                for (AiScenario scenario : scenarios) {
                    double score = scoreTool(queryLower, scenario);
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
                log.error("Failed to retrieve tool definitions: {}", e.getMessage(), e);
                return Collections.<RagRetrievalResult>emptyList();
            }
        })
        .flatMapMany(Flux::fromIterable);
    }
    
    /**
     * Score tool based on query match.
     * Uses description, execution type, scenario code.
     */
    private double scoreTool(String queryLower, AiScenario scenario) {
        double score = 0.0;
        
        // Match against description
        if (scenario.getDescription() != null) {
            String descLower = scenario.getDescription().toLowerCase();
            if (descLower.contains(queryLower)) {
                score += 0.4;
            }
        }
        
        // Match against scenario code/name
        if (scenario.getScenarioCode() != null && queryLower.contains(scenario.getScenarioCode().toLowerCase())) {
            score += 0.5;
        }
        if (scenario.getScenarioName() != null && queryLower.contains(scenario.getScenarioName().toLowerCase())) {
            score += 0.4;
        }
        
        // Match against execution type keywords
        if (scenario.getExecutionType() != null) {
            String execType = scenario.getExecutionType().toLowerCase();
            if (queryLower.contains(execType.replace("_", " "))) {
                score += 0.3;
            }
        }
        
        // Normalize score to 0.0-1.0 range
        return Math.min(score, 1.0);
    }
    
    /**
     * Convert scenario to RagRetrievalResult (tool definition).
     */
    private RagRetrievalResult toRetrievalResult(AiScenario scenario, double score) {
        // Build tool definition content
        StringBuilder content = new StringBuilder();
        content.append("Tool: ").append(scenario.getScenarioCode()).append("\n");
        content.append("Execution Type: ").append(scenario.getExecutionType()).append("\n");
        if (scenario.getDescription() != null) {
            content.append("Description: ").append(scenario.getDescription()).append("\n");
        }
        if (scenario.getExecutionType() != null) {
            switch (scenario.getExecutionType()) {
                case "DB_QUERY":
                    if (scenario.getSqlQuery() != null) {
                        content.append("Query Type: SQL\n");
                    }
                    break;
                case "HTTP_CALL":
                    if (scenario.getHttpUrl() != null) {
                        content.append("API Endpoint: ").append(scenario.getHttpUrl()).append("\n");
                    }
                    break;
            }
        }
        if (scenario.getRequiredParams() != null) {
            content.append("Required Parameters: ").append(scenario.getRequiredParams()).append("\n");
        }
        
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("scenarioCode", scenario.getScenarioCode());
        metadata.put("executionType", scenario.getExecutionType());
        metadata.put("toolType", scenario.getExecutionType());
        
        return RagRetrievalResult.builder()
                .sourceType("TOOL_DEFINITION")
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
