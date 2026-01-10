package com.enterprise.ai.intelligence.service.scenario;

import com.enterprise.ai.common.dto.IntentResult;
import com.enterprise.ai.data.entity.AiScenario;
import com.enterprise.ai.data.entity.FallbackLayer;
import com.enterprise.ai.data.service.ConfigCacheService;
import com.enterprise.ai.intelligence.service.context.ReferenceResolver;
import com.enterprise.ai.intelligence.service.fallback.FallbackLayerService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Scenario-based intent detection using trigger phrases from AiScenario.
 * This is a lightweight fallback that works even when other layers have no data configured.
 * 
 * Uses trigger_phrases and example_queries from ai_scenarios table directly.
 * NO HARDCODING - All configuration from database.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScenarioTriggerMatcher implements FallbackLayerService {

    private final ConfigCacheService configCacheService;
    private final ReferenceResolver referenceResolver;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    private List<AiScenario> scenariosWithTriggers = new ArrayList<>();
    
    // Common abbreviations and variations (can be DB-driven in future)
    private static final Map<String, String> ABBREVIATIONS = Map.of(
        "txn", "transaction",
        "txns", "transactions",
        "acct", "account",
        "acc", "account",
        "bal", "balance",
        "hist", "history",
        "stmt", "statement",
        "summary", "summary"
    );
    
    // Common Hindi-English patterns (can be expanded)
    private static final Map<String, String> HINDI_ENGLISH_PATTERNS = Map.of(
        "kitne", "how many",
        "kya", "what",
        "kab", "when",
        "kaise", "how",
        "dikhao", "show",
        "batao", "tell"
    );
    
    @jakarta.annotation.PostConstruct
    public void init() {
        loadScenarios();
        log.info("ScenarioTriggerMatcher initialized with {} scenarios with trigger phrases", scenariosWithTriggers.size());
    }
    
    private void loadScenarios() {
        scenariosWithTriggers = configCacheService.getActiveScenarios().stream()
            .filter(s -> s.getTriggerPhrases() != null && !s.getTriggerPhrases().isEmpty())
            .collect(Collectors.toList());
    }
    
    @Override
    public String getLayerCode() {
        return "SCENARIO_TRIGGER_MATCHER";
    }
    
    @Override
    public boolean isEnabled() {
        return !scenariosWithTriggers.isEmpty();
    }
    
    @Override
    public Mono<Optional<IntentResult>> detectIntent(
            String userInput,
            String sessionContext,
            String lastUsedParamsJson,
            Set<String> allowedScenarios,
            FallbackLayer layerConfig) {
        
        if (!isEnabled()) {
            return Mono.just(Optional.empty());
        }
        
        return Mono.<Optional<IntentResult>>fromCallable(() -> {
            try {
                // Step 1: Normalize query (expand abbreviations, handle multilingual)
                String normalizedQuery = normalizeQuery(userInput);
                log.debug("Normalized query: '{}' -> '{}'", userInput, normalizedQuery);
                
                // Step 2: Resolve references (expand "this account" to actual values)
                String expandedQuery = resolveReferences(normalizedQuery, lastUsedParamsJson);
                log.debug("Expanded query with context: '{}' -> '{}'", normalizedQuery, expandedQuery);
                
                // Use expanded query for matching
                String queryLower = expandedQuery.toLowerCase().trim();
                Map<String, Double> scenarioScores = new HashMap<>();
                
                // Score each scenario based on trigger phrase matches
                for (AiScenario scenario : scenariosWithTriggers) {
                    String scenarioCode = scenario.getScenarioCode();
                    
                    // Check RBAC
                    if (allowedScenarios != null && !allowedScenarios.isEmpty()) {
                        if (!allowedScenarios.contains(scenarioCode)) {
                            continue;
                        }
                    }
                    
                    double score = 0.0;
                    int matches = 0;
                    
                    // Check trigger phrases
                    if (scenario.getTriggerPhrases() != null && !scenario.getTriggerPhrases().isEmpty()) {
                        try {
                            List<String> triggerPhrases = objectMapper.readValue(
                                scenario.getTriggerPhrases(),
                                new TypeReference<List<String>>() {}
                            );
                            
                            for (String phrase : triggerPhrases) {
                                String phraseLower = phrase.toLowerCase().trim();
                                
                                // Try exact match first
                                if (queryLower.contains(phraseLower)) {
                                    // Exact match gets highest score
                                    if (queryLower.equals(phraseLower)) {
                                        score += 15.0;
                                    } else if (queryLower.startsWith(phraseLower) || queryLower.endsWith(phraseLower)) {
                                        // Phrase at start/end of query - very good match
                                        score += 12.0;
                                    } else if (queryLower.contains(" " + phraseLower + " ") || 
                                               queryLower.matches(".*\\b" + Pattern.quote(phraseLower) + "\\b.*")) {
                                        // Phrase as whole word - good match
                                        score += 10.0;
                                    } else {
                                        // Partial match - still good
                                        score += 8.0;
                                    }
                                    matches++;
                                } else {
                                    // Try fuzzy matching for variations and abbreviations
                                    double fuzzyScore = fuzzyMatch(queryLower, phraseLower);
                                    if (fuzzyScore > 0) {
                                        score += fuzzyScore;
                                        matches++;
                                    }
                                }
                            }
                        } catch (Exception e) {
                            log.debug("Failed to parse trigger phrases for scenario {}: {}", scenarioCode, e.getMessage());
                        }
                    }
                    
                    // Check example queries (lower weight)
                    if (scenario.getExampleQueries() != null && !scenario.getExampleQueries().isEmpty()) {
                        try {
                            List<String> exampleQueries = objectMapper.readValue(
                                scenario.getExampleQueries(),
                                new TypeReference<List<String>>() {}
                            );
                            
                            for (String example : exampleQueries) {
                                String exampleLower = example.toLowerCase();
                                if (queryLower.contains(exampleLower)) {
                                    score += 3.0; // Lower weight for examples
                                    matches++;
                                }
                            }
                        } catch (Exception e) {
                            log.debug("Failed to parse example queries for scenario {}: {}", scenarioCode, e.getMessage());
                        }
                    }
                    
                    if (matches > 0) {
                        scenarioScores.put(scenarioCode, score);
                    }
                }
                
                if (scenarioScores.isEmpty()) {
                    log.debug("No scenario trigger phrase matches found for query: {}", userInput);
                    return Optional.empty();
                }
                
                // Find best match
                String bestScenario = scenarioScores.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse(null);
                
                double bestScore = scenarioScores.get(bestScenario);
                
                // Get scenario for threshold check
                AiScenario matchedScenario = configCacheService.getScenarioByCode(bestScenario).orElse(null);
                
                // Normalize score to 0-1 range (max score is around 12-15 for good matches)
                // Use better normalization: good matches (8+) should map to 0.65+, exact matches to 0.85+
                double normalizedScore;
                if (bestScore >= 15.0) {
                    normalizedScore = 0.90; // Exact match
                } else if (bestScore >= 12.0) {
                    normalizedScore = 0.75 + ((bestScore - 12.0) / 3.0) * 0.10; // 0.75-0.85
                } else if (bestScore >= 10.0) {
                    normalizedScore = 0.65 + ((bestScore - 10.0) / 2.0) * 0.10; // 0.65-0.75
                } else if (bestScore >= 8.0) {
                    normalizedScore = 0.55 + ((bestScore - 8.0) / 2.0) * 0.10; // 0.55-0.65
                } else {
                    normalizedScore = Math.min(0.55, bestScore / 15.0); // Lower scores
                }
                
                // If scenario has its own confidence threshold, use it (but don't go below 0.5)
                double effectiveThreshold = layerConfig.getConfidenceThreshold();
                if (matchedScenario != null && matchedScenario.getConfidenceThreshold() != null) {
                    effectiveThreshold = Math.max(0.5, matchedScenario.getConfidenceThreshold());
                }
                
                // Check confidence threshold (use scenario's threshold if available)
                if (normalizedScore < effectiveThreshold) {
                    log.debug("Scenario trigger score {} below threshold {} for scenario {}", 
                        normalizedScore, effectiveThreshold, bestScenario);
                    return Optional.empty();
                }
                
                // Check for ambiguity (multiple high scores)
                List<Map.Entry<String, Double>> topScores = scenarioScores.entrySet().stream()
                    .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                    .limit(3)
                    .collect(Collectors.toList());
                
                if (topScores.size() > 1) {
                    double secondScore = topScores.get(1).getValue();
                    // Use same normalization logic for second score
                    double normalizedSecondScore;
                    if (secondScore >= 15.0) {
                        normalizedSecondScore = 0.90;
                    } else if (secondScore >= 12.0) {
                        normalizedSecondScore = 0.75 + ((secondScore - 12.0) / 3.0) * 0.10;
                    } else if (secondScore >= 10.0) {
                        normalizedSecondScore = 0.65 + ((secondScore - 10.0) / 2.0) * 0.10;
                    } else if (secondScore >= 8.0) {
                        normalizedSecondScore = 0.55 + ((secondScore - 8.0) / 2.0) * 0.10;
                    } else {
                        normalizedSecondScore = Math.min(0.55, secondScore / 15.0);
                    }
                    
                    if (normalizedSecondScore > normalizedScore * 0.8) { // Within 20% of best
                        List<String> possibleScenarios = topScores.stream()
                            .map(Map.Entry::getKey)
                            .collect(Collectors.toList());
                        
                        return Optional.of(IntentResult.builder()
                            .scenario("AMBIGUOUS")
                            .confidence(normalizedScore * 0.9)
                            .possibleScenarios(possibleScenarios)
                            .reasoning("Multiple scenarios matched with similar trigger phrase scores")
                            .build());
                    }
                }
                
                log.debug("Scenario trigger matcher found match: scenario={}, score={}, normalized={}", 
                    bestScenario, bestScore, normalizedScore);
                
                return Optional.of(IntentResult.builder()
                    .scenario(bestScenario)
                    .confidence(normalizedScore)
                    .reasoning("Matched via scenario trigger phrases")
                    .params(new HashMap<>())
                    .build());
                
            } catch (Exception e) {
                log.error("Scenario trigger matching failed: {}", e.getMessage(), e);
                return Optional.empty();
            }
        })
        .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic())
        .timeout(java.time.Duration.ofMillis(layerConfig.getTimeoutMs()))
        .onErrorResume(e -> {
            log.warn("Scenario trigger matcher error: {}", e.getMessage());
            return Mono.just(Optional.empty());
        });
    }
    
    @Override
    public Mono<Boolean> isHealthy() {
        return Mono.just(isEnabled());
    }
    
    /**
     * Normalize query: expand abbreviations, handle multilingual patterns
     */
    private String normalizeQuery(String query) {
        if (query == null || query.isEmpty()) {
            return query;
        }
        
        String normalized = query.toLowerCase().trim();
        
        // Expand abbreviations
        for (Map.Entry<String, String> abbr : ABBREVIATIONS.entrySet()) {
            // Replace whole word matches only
            normalized = normalized.replaceAll("\\b" + Pattern.quote(abbr.getKey()) + "\\b", abbr.getValue());
        }
        
        // Handle Hindi-English patterns (simple keyword replacement)
        for (Map.Entry<String, String> pattern : HINDI_ENGLISH_PATTERNS.entrySet()) {
            normalized = normalized.replaceAll("\\b" + Pattern.quote(pattern.getKey()) + "\\b", pattern.getValue());
        }
        
        // Normalize common variations
        normalized = normalized.replaceAll("\\b(all|list|show|get|fetch|display)\\s+(txn|transaction)s?\\b", "transaction history");
        normalized = normalized.replaceAll("\\b(txn|transaction)s?\\s+(of|for)\\s+", "transaction history for ");
        normalized = normalized.replaceAll("\\b(all|all the)\\s+", "");
        
        return normalized.trim();
    }
    
    /**
     * Resolve references in query (e.g., "this account" -> "ACC001")
     */
    private String resolveReferences(String query, String lastUsedParamsJson) {
        if (query == null || query.isEmpty() || lastUsedParamsJson == null || lastUsedParamsJson.isEmpty()) {
            return query;
        }
        
        if (!referenceResolver.hasReference(query)) {
            return query;
        }
        
        try {
            // Parse lastUsedParams from JSON
            Map<String, Object> lastParams = objectMapper.readValue(
                lastUsedParamsJson,
                new TypeReference<Map<String, Object>>() {}
            );
            
            // Resolve common references
            String expanded = query;
            
            // "this account" or "that account" -> replace with accountId
            if (query.matches(".*\\b(this|that|the|same)\\s+account\\b.*")) {
                Object accountId = lastParams.get("accountId");
                if (accountId != null) {
                    expanded = expanded.replaceAll("\\b(this|that|the|same)\\s+account\\b", accountId.toString());
                }
            }
            
            // "this transaction" or "that transaction" -> replace with transactionId
            if (query.matches(".*\\b(this|that|the|same)\\s+transaction\\b.*")) {
                Object txnId = lastParams.get("transactionId");
                if (txnId != null) {
                    expanded = expanded.replaceAll("\\b(this|that|the|same)\\s+transaction\\b", txnId.toString());
                }
            }
            
            // Generic: "this" or "that" -> try to resolve from context
            if (expanded.equals(query) && query.matches(".*\\b(this|that|it)\\b.*")) {
                // Try to expand with most recent param value
                if (!lastParams.isEmpty()) {
                    Object firstValue = lastParams.values().iterator().next();
                    if (firstValue != null) {
                        expanded = expanded.replaceAll("\\b(this|that|it)\\b", firstValue.toString());
                    }
                }
            }
            
            log.debug("Resolved references in query: '{}' -> '{}'", query, expanded);
            return expanded;
            
        } catch (Exception e) {
            log.debug("Failed to resolve references: {}", e.getMessage());
            return query;
        }
    }
    
    /**
     * Fuzzy matching for variations and abbreviations
     */
    private double fuzzyMatch(String query, String phrase) {
        // Check if phrase contains abbreviations that match query
        for (Map.Entry<String, String> abbr : ABBREVIATIONS.entrySet()) {
            if (phrase.contains(abbr.getValue()) && query.contains(abbr.getKey())) {
                // Query has abbreviation, phrase has full form
                String phraseWithAbbr = phrase.replace(abbr.getValue(), abbr.getKey());
                if (query.contains(phraseWithAbbr)) {
                    return 9.0; // Good match with abbreviation
                }
            } else if (phrase.contains(abbr.getKey()) && query.contains(abbr.getValue())) {
                // Phrase has abbreviation, query has full form
                return 9.0; // Good match with abbreviation
            }
        }
        
        // Check if all words in phrase are present in query (in any order)
        String[] phraseWords = phrase.split("\\s+");
        int matchedWords = 0;
        for (String word : phraseWords) {
            if (word.length() > 2 && query.contains(word)) {
                matchedWords++;
            }
        }
        
        if (matchedWords >= phraseWords.length * 0.7) { // 70% of words match
            return 7.0 + (matchedWords / (double) phraseWords.length) * 2.0; // 7.0-9.0
        }
        
        return 0.0;
    }
    
    /**
     * Refresh scenarios from cache
     */
    public void refreshScenarios() {
        loadScenarios();
    }
}
