package com.enterprise.ai.intelligence.service.rules;

import com.enterprise.ai.common.dto.IntentResult;
import com.enterprise.ai.data.entity.FallbackLayer;
import com.enterprise.ai.data.entity.RuleEngineRule;
import com.enterprise.ai.data.repository.RuleEngineRuleRepository;
import com.enterprise.ai.intelligence.service.fallback.FallbackLayerService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Rule-based intent detection service.
 * Uses DB-driven rules for deterministic intent matching.
 * 
 * NO HARDCODING - All rules from database.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RuleEngineMatcher implements FallbackLayerService {

    private final RuleEngineRuleRepository ruleRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    private List<RuleEngineRule> activeRules = new ArrayList<>();
    
    @PostConstruct
    public void init() {
        loadRules();
        log.info("RuleEngineMatcher initialized with {} active rules", activeRules.size());
    }
    
    private void loadRules() {
        activeRules = ruleRepository.findAllActiveOrderByPriorityDesc();
    }

    @Override
    public String getLayerCode() {
        return "RULE_ENGINE";
    }

    @Override
    public boolean isEnabled() {
        return !activeRules.isEmpty();
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
                List<RuleMatch> matches = new ArrayList<>();
                
                // Evaluate all rules
                for (RuleEngineRule rule : activeRules) {
                    RuleMatch match = evaluateRule(rule, queryLower, userInput, allowedScenarios);
                    if (match != null) {
                        matches.add(match);
                    }
                }
                
                if (matches.isEmpty()) {
                    log.debug("No rule matches found for query: {}", userInput);
                    return Optional.empty();
                }
                
                // Sort by confidence (highest first)
                matches.sort(Comparator.comparing(RuleMatch::confidence).reversed());
                
                RuleMatch bestMatch = matches.get(0);
                
                // Check confidence threshold
                if (bestMatch.confidence() < layerConfig.getConfidenceThreshold()) {
                    log.debug("Rule confidence {} below threshold {}", bestMatch.confidence(), layerConfig.getConfidenceThreshold());
                    return Optional.empty();
                }
                
                // Check if multiple high-confidence matches (ambiguous)
                if (matches.size() > 1) {
                    RuleMatch secondMatch = matches.get(1);
                    if (secondMatch.confidence() > bestMatch.confidence() * 0.9) {
                        List<String> possibleScenarios = matches.stream()
                            .limit(3)
                            .map(RuleMatch::scenarioCode)
                            .distinct()
                            .collect(Collectors.toList());
                        
                        return Optional.of(IntentResult.builder()
                            .scenario("AMBIGUOUS")
                            .confidence(bestMatch.confidence() * 0.9)
                            .possibleScenarios(possibleScenarios)
                            .reasoning("Multiple rules matched with similar confidence")
                            .build());
                    }
                }
                
                // Single clear match
                return Optional.of(IntentResult.builder()
                    .scenario(bestMatch.scenarioCode())
                    .confidence(bestMatch.confidence())
                    .params(bestMatch.params())
                    .missingParams(bestMatch.missingParams())
                    .reasoning("Matched via rule: " + bestMatch.ruleCode())
                    .build());
                
            } catch (Exception e) {
                log.error("Rule matching failed: {}", e.getMessage(), e);
                return Optional.<IntentResult>empty();
            }
        })
        .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic())
        .timeout(java.time.Duration.ofMillis(layerConfig.getTimeoutMs()))
        .onErrorReturn(Optional.<IntentResult>empty());
    }
    
    /**
     * Evaluate a single rule against the query
     */
    private RuleMatch evaluateRule(RuleEngineRule rule, String queryLower, String queryOriginal, Set<String> allowedScenarios) {
        try {
            // Parse rule conditions
            Map<String, Object> conditions = objectMapper.readValue(
                rule.getConditions(),
                new TypeReference<Map<String, Object>>() {}
            );
            
            boolean matched = false;
            double matchScore = 0.0;
            
            // Check keywords
            if (conditions.containsKey("keywords")) {
                @SuppressWarnings("unchecked")
                List<String> keywords = (List<String>) conditions.get("keywords");
                int keywordMatches = 0;
                for (String keyword : keywords) {
                    if (queryLower.contains(keyword.toLowerCase())) {
                        keywordMatches++;
                    }
                }
                if (keywordMatches > 0) {
                    matched = true;
                    matchScore = Math.max(matchScore, (double) keywordMatches / keywords.size());
                }
            }
            
            // Check patterns
            if (conditions.containsKey("patterns")) {
                @SuppressWarnings("unchecked")
                List<String> patterns = (List<String>) conditions.get("patterns");
                for (String pattern : patterns) {
                    if (queryLower.contains(pattern.toLowerCase())) {
                        matched = true;
                        matchScore = Math.max(matchScore, 0.9); // Pattern match is high confidence
                        break;
                    }
                }
            }
            
            // Check regex
            if (conditions.containsKey("regex")) {
                String regex = (String) conditions.get("regex");
                try {
                    Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
                    if (pattern.matcher(queryOriginal).find()) {
                        matched = true;
                        matchScore = Math.max(matchScore, 0.95); // Regex match is very high confidence
                    }
                } catch (Exception e) {
                    log.warn("Invalid regex in rule {}: {}", rule.getRuleCode(), regex);
                }
            }
            
            if (!matched) {
                return null;
            }
            
            // Check RBAC if scenario is specified
            String scenarioCode = rule.getScenarioCode();
            if (scenarioCode != null && allowedScenarios != null && !allowedScenarios.isEmpty()) {
                if (!allowedScenarios.contains(scenarioCode)) {
                    return null;
                }
            }
            
            // Calculate final confidence (rule confidence * match score)
            double confidence = rule.getConfidence() * matchScore;
            
            // Extract parameters using Entity Extraction Service
            Map<String, Object> params = new HashMap<>();
            List<String> missingParams = new ArrayList<>();
            
            // Enhanced parameter extraction from query using EntityExtractionService
            try {
                // This will be enhanced when ParameterExtractionService is available via dependency injection
                // For now, extract basic patterns from query
                if (scenarioCode != null) {
                    // Extract common entities from query (basic regex patterns)
                    extractBasicParams(queryOriginal, params);
                }
            } catch (Exception e) {
                log.debug("Failed to extract parameters in rule matching: {}", e.getMessage());
            }
            
            return new RuleMatch(
                rule.getRuleCode(),
                scenarioCode != null ? scenarioCode : "UNKNOWN",
                confidence,
                params,
                missingParams
            );
            
        } catch (Exception e) {
            log.warn("Failed to evaluate rule {}: {}", rule.getRuleCode(), e.getMessage());
            return null;
        }
    }

    /**
         * Internal class for rule match results
         */

        private record RuleMatch(String ruleCode, String scenarioCode, double confidence, Map<String, Object> params,
                                 List<String> missingParams) {

    }

    @Override
    public Mono<Boolean> isHealthy() {
        return Mono.just(isEnabled());
    }
    
    /**
     * Extract basic parameters from query using simple regex patterns.
     * This is a lightweight extraction for rule engine layer.
     * For comprehensive extraction, ParameterExtractionService should be used at higher level.
     */
    private void extractBasicParams(String query, Map<String, Object> params) {
        // Extract account numbers (e.g., "account 12345", "acc 12345")
        Pattern accountPattern = Pattern.compile("(?:account|acc)[\\s#:-]*([A-Z0-9]{4,})", Pattern.CASE_INSENSITIVE);
        java.util.regex.Matcher accountMatcher = accountPattern.matcher(query);
        if (accountMatcher.find()) {
            params.put("accountId", accountMatcher.group(1));
        }
        
        // Extract transaction IDs (e.g., "transaction TXN123", "txn TXN123")
        Pattern txnPattern = Pattern.compile("(?:transaction|txn)[\\s#:-]*([A-Z0-9]{4,})", Pattern.CASE_INSENSITIVE);
        java.util.regex.Matcher txnMatcher = txnPattern.matcher(query);
        if (txnMatcher.find()) {
            params.put("transactionId", txnMatcher.group(1));
        }
        
        // Extract amounts (e.g., "1000", "Rs 1000", "₹1000")
        Pattern amountPattern = Pattern.compile("(?:Rs|₹|INR)?[\\s]*(\\d+(?:\\.\\d{2})?)", Pattern.CASE_INSENSITIVE);
        java.util.regex.Matcher amountMatcher = amountPattern.matcher(query);
        if (amountMatcher.find()) {
            try {
                params.put("amount", Double.parseDouble(amountMatcher.group(1)));
            } catch (NumberFormatException e) {
                log.debug("Failed to parse amount: {}", amountMatcher.group(1));
            }
        }
        
        // Extract dates (e.g., "2024-01-15", "15/01/2024", "Jan 15 2024")
        Pattern datePattern = Pattern.compile("\\b(\\d{4}-\\d{2}-\\d{2}|\\d{2}/\\d{2}/\\d{4}|\\d{2}-\\d{2}-\\d{4})\\b");
        java.util.regex.Matcher dateMatcher = datePattern.matcher(query);
        if (dateMatcher.find()) {
            params.put("date", dateMatcher.group(1));
        }
    }
    
    /**
     * Refresh rules from database
     */
    public void refreshRules() {
        log.info("Refreshing rules from database...");
        activeRules = ruleRepository.findAllActiveOrderByPriorityDesc();
        log.info("Rules refreshed: {} active rules", activeRules.size());
    }
}
