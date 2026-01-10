package com.enterprise.ai.intelligence.service.keyword;

import com.enterprise.ai.common.dto.IntentResult;
import com.enterprise.ai.data.entity.FallbackLayer;
import com.enterprise.ai.data.entity.KeywordPattern;
import com.enterprise.ai.data.repository.KeywordPatternRepository;
import com.enterprise.ai.intelligence.service.fallback.FallbackLayerService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Keyword-based intent detection service.
 * Uses DB-driven keyword patterns with weighted scoring.
 * 
 * NO HARDCODING - All keywords from database.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KeywordMatcher implements FallbackLayerService {

    private final KeywordPatternRepository keywordRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    private Map<String, List<KeywordPattern>> scenarioKeywords = new HashMap<>();
    
    @PostConstruct
    public void init() {
        refreshKeywords();
        log.info("KeywordMatcher initialized with {} scenarios", scenarioKeywords.size());
    }
    
    private void refreshKeywords() {
        List<KeywordPattern> allKeywords = keywordRepository.findByActiveTrue();
        scenarioKeywords = allKeywords.stream()
            .collect(Collectors.groupingBy(KeywordPattern::getScenarioCode));
    }

    @Override
    public String getLayerCode() {
        return "KEYWORD_MATCHER";
    }

    @Override
    public boolean isEnabled() {
        return !scenarioKeywords.isEmpty();
    }

    @Override
    public Mono<Optional<IntentResult>> detectIntent(
            String userInput,
            String sessionContext,
            String lastUsedParamsJson,
            Set<String> allowedScenarios,
            FallbackLayer layerConfig) {
        
        if (!isEnabled()) {
            return Mono.just(Optional.<IntentResult>empty());
        }
        
        return Mono.<Optional<IntentResult>>fromCallable(() -> {
            try {
                String queryLower = userInput.toLowerCase();
                Map<String, Double> scenarioScores = new HashMap<>();
                
                // Score each scenario based on keyword matches
                for (Map.Entry<String, List<KeywordPattern>> entry : scenarioKeywords.entrySet()) {
                    String scenarioCode = entry.getKey();
                    
                    // Check RBAC
                    if (allowedScenarios != null && !allowedScenarios.isEmpty()) {
                        if (!allowedScenarios.contains(scenarioCode)) {
                            continue;
                        }
                    }
                    
                    double score = 0.0;
                    int matches = 0;
                    
                    for (KeywordPattern pattern : entry.getValue()) {
                        String keyword = pattern.getKeyword().toLowerCase();
                        double weight = pattern.getWeight() != null ? pattern.getWeight() : 1.0;
                        
                        // Check if keyword appears in query
                        if (queryLower.contains(keyword)) {
                            score += weight;
                            matches++;
                        }
                        
                        // Check synonyms
                        if (pattern.getSynonyms() != null) {
                            try {
                                List<String> synonyms = objectMapper.readValue(
                                    pattern.getSynonyms(),
                                    new TypeReference<List<String>>() {}
                                );
                                for (String synonym : synonyms) {
                                    if (queryLower.contains(synonym.toLowerCase())) {
                                        score += weight * 0.8; // Synonyms slightly less weight
                                        matches++;
                                    }
                                }
                            } catch (Exception e) {
                                log.warn("Failed to parse synonyms for keyword {}: {}", pattern.getKeyword(), e.getMessage());
                            }
                        }
                    }
                    
                    // Normalize score by total keywords (optional)
                    if (matches > 0) {
                        scenarioScores.put(scenarioCode, score);
                    }
                }
                
                if (scenarioScores.isEmpty()) {
                    log.debug("No keyword matches found for query: {}", userInput);
                    return Optional.empty();
                }
                
                // Find best match
                String bestScenario = scenarioScores.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse(null);
                
                double bestScore = scenarioScores.get(bestScenario);
                
                // Normalize score to 0-1 range (assuming max score is around 5-10)
                double normalizedScore = Math.min(1.0, bestScore / 10.0);
                
                // Check confidence threshold
                if (normalizedScore < layerConfig.getConfidenceThreshold()) {
                    log.debug("Keyword score {} below threshold {}", normalizedScore, layerConfig.getConfidenceThreshold());
                    return Optional.empty();
                }
                
                // Check for ambiguity (multiple high scores)
                List<Map.Entry<String, Double>> topScores = scenarioScores.entrySet().stream()
                    .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                    .limit(3)
                    .collect(Collectors.toList());
                
                if (topScores.size() > 1) {
                    double secondScore = topScores.get(1).getValue();
                    double normalizedSecondScore = Math.min(1.0, secondScore / 10.0);
                    
                    if (normalizedSecondScore > normalizedScore * 0.8) { // Within 20% of best
                        List<String> possibleScenarios = topScores.stream()
                            .map(Map.Entry::getKey)
                            .collect(Collectors.toList());
                        
                        return Optional.of(IntentResult.builder()
                            .scenario("AMBIGUOUS")
                            .confidence(normalizedScore * 0.9)
                            .possibleScenarios(possibleScenarios)
                            .reasoning("Multiple scenarios matched with similar keyword scores")
                            .build());
                    }
                }
                
                // Single clear match - always ask for confirmation since keyword matching is lower confidence
                return Optional.of(IntentResult.builder()
                    .scenario(bestScenario)
                    .confidence(normalizedScore)
                    .params(new HashMap<>())
                    .reasoning("Matched via keyword matching (score: " + String.format("%.2f", bestScore) + ")")
                    .build());
                
            } catch (Exception e) {
                log.error("Keyword matching failed: {}", e.getMessage(), e);
                return Optional.empty();
            }
        })
        .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic())
        .timeout(java.time.Duration.ofMillis(layerConfig.getTimeoutMs()))
        .onErrorReturn(Optional.<IntentResult>empty());
    }

    @Override
    public Mono<Boolean> isHealthy() {
        return Mono.just(isEnabled());
    }
    
    /**
     * Refresh keywords from database (public method for refresh)
     */
    public void refreshKeywordsFromDb() {
        log.info("Refreshing keywords from database...");
        List<KeywordPattern> allKeywords = keywordRepository.findByActiveTrue();
        scenarioKeywords = allKeywords.stream()
            .collect(Collectors.groupingBy(KeywordPattern::getScenarioCode));
        log.info("Keywords refreshed: {} scenarios with keywords", scenarioKeywords.size());
    }
}
