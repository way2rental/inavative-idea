package com.enterprise.ai.intelligence.service.domain;

import com.enterprise.ai.data.service.ConfigCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Banking Domain Classification Service.
 * Determines if a query is related to Corporate Banking domain.
 * 
 * This is the FIRST layer - rejects out-of-domain queries before processing.
 * 
 * NO HARDCODING - Banking patterns and terminology from database.
 * In future, will use ML model (ONNX) for classification.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BankingDomainClassifierService {

    private final ConfigCacheService configCacheService;
    
    // Banking keywords corpus (will be DB-driven)
    // These are fallback patterns - actual patterns should come from ai_banking_terminology table
    private static final Set<String> BANKING_KEYWORDS = Set.of(
        // Account related
        "account", "balance", "transaction", "transfer", "payment", "deposit", "withdrawal",
        // Transaction types
        "debit", "credit", "transfer", "upi", "imps", "neft", "rtgs", "wallet",
        // Banking operations
        "beneficiary", "card", "statement", "summary", "history", "bill", "loan",
        // Corporate banking
        "bulk", "batch", "file", "reconciliation", "salary", "payment file",
        // Common abbreviations
        "txn", "txns", "acct", "acc", "bal", "stmt", "utr", "ref"
    );
    
    // Out-of-domain patterns (will be DB-driven)
    private static final Set<String> OUT_OF_DOMAIN_INDICATORS = Set.of(
        "weather", "sports", "news", "movie", "game", "recipe", "health",
        "travel", "shopping", "restaurant", "hotel", "flight"
    );
    
    /**
     * Classify if query is banking-related.
     * 
     * @param query User query
     * @param sessionContext Conversation context
     * @return DomainClassificationResult with isBanking flag and confidence
     */
    public Mono<DomainClassificationResult> classify(String query, String sessionContext) {
        return Mono.fromCallable(() -> {
            try {
                if (query == null || query.trim().isEmpty()) {
                    return DomainClassificationResult.unknown("Empty query");
                }
                
                String queryLower = query.toLowerCase().trim();
                
                // Method 1: Check against banking scenarios (most reliable)
                boolean hasBankingScenario = hasBankingScenarioMatch(queryLower);
                
                // Method 2: Check for banking keywords
                double keywordScore = calculateBankingKeywordScore(queryLower);
                
                // Method 3: Check for out-of-domain indicators
                boolean hasOutOfDomainIndicators = hasOutOfDomainIndicators(queryLower);
                
                // Method 4: Check conversation context (if previous queries were banking)
                boolean contextIsBanking = isContextBanking(sessionContext);
                
                // Combine scores
                double bankingScore = 0.0;
                if (hasBankingScenario) {
                    bankingScore += 0.5; // Strong indicator
                }
                bankingScore += keywordScore * 0.3; // Keyword matching
                if (contextIsBanking) {
                    bankingScore += 0.2; // Context helps
                }
                
                // Out-of-domain indicators reduce score
                if (hasOutOfDomainIndicators) {
                    bankingScore -= 0.4;
                }
                
                // Normalize to 0-1 range
                double confidence = Math.max(0.0, Math.min(1.0, bankingScore));
                
                // Classification decision
                boolean isBanking = confidence >= 0.5;
                
                if (!isBanking) {
                    log.debug("Query classified as out-of-domain: {} (confidence: {})", query, confidence);
                } else {
                    log.debug("Query classified as banking: {} (confidence: {})", query, confidence);
                }
                
                return DomainClassificationResult.builder()
                    .isBanking(isBanking)
                    .confidence(confidence)
                    .reasoning(buildReasoning(hasBankingScenario, keywordScore, hasOutOfDomainIndicators, contextIsBanking))
                    .bankingCategory(getBankingCategory(queryLower))
                    .build();
                    
            } catch (Exception e) {
                log.error("Domain classification failed: {}", e.getMessage(), e);
                // On error, assume banking (let other layers handle)
                return DomainClassificationResult.banking(0.5, "Classification error - assuming banking");
            }
        })
        .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic());
    }
    
    /**
     * Check if query matches any banking scenario.
     */
    private boolean hasBankingScenarioMatch(String queryLower) {
        return configCacheService.getActiveScenarios().stream()
            .anyMatch(scenario -> {
                // Check trigger phrases
                if (scenario.getTriggerPhrases() != null) {
                    try {
                        List<String> triggers = new com.fasterxml.jackson.databind.ObjectMapper()
                            .readValue(scenario.getTriggerPhrases(), 
                                new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
                        for (String trigger : triggers) {
                            if (queryLower.contains(trigger.toLowerCase())) {
                                return true;
                            }
                        }
                    } catch (Exception e) {
                        // Ignore parsing errors
                    }
                }
                return false;
            });
    }
    
    /**
     * Calculate banking keyword score.
     */
    private double calculateBankingKeywordScore(String queryLower) {
        long matches = BANKING_KEYWORDS.stream()
            .mapToLong(keyword -> queryLower.contains(keyword) ? 1 : 0)
            .sum();
        
        // Normalize: 1 keyword = 0.2, 2+ keywords = 0.5+
        if (matches == 0) return 0.0;
        if (matches == 1) return 0.2;
        if (matches >= 2) return 0.5;
        return 0.0;
    }
    
    /**
     * Check for out-of-domain indicators.
     */
    private boolean hasOutOfDomainIndicators(String queryLower) {
        return OUT_OF_DOMAIN_INDICATORS.stream()
            .anyMatch(indicator -> queryLower.contains(indicator));
    }
    
    /**
     * Check if conversation context is banking-related.
     */
    private boolean isContextBanking(String sessionContext) {
        if (sessionContext == null || sessionContext.isEmpty()) {
            return false;
        }
        
        String contextLower = sessionContext.toLowerCase();
        
        // Check if previous queries mention banking scenarios
        return configCacheService.getActiveScenarios().stream()
            .anyMatch(scenario -> contextLower.contains(scenario.getScenarioCode().toLowerCase()) ||
                                 (scenario.getScenarioName() != null && 
                                  contextLower.contains(scenario.getScenarioName().toLowerCase())));
    }
    
    /**
     * Determine banking category from query.
     */
    private String getBankingCategory(String queryLower) {
        if (queryLower.contains("transaction") || queryLower.contains("txn")) {
            return "TRANSACTION";
        }
        if (queryLower.contains("balance") || queryLower.contains("bal")) {
            return "ACCOUNT";
        }
        if (queryLower.contains("transfer") || queryLower.contains("payment")) {
            return "PAYMENT";
        }
        if (queryLower.contains("email") || queryLower.contains("mail")) {
            return "EMAIL";
        }
        if (queryLower.contains("card")) {
            return "CARD";
        }
        if (queryLower.contains("loan")) {
            return "LOAN";
        }
        if (queryLower.contains("beneficiary")) {
            return "BENEFICIARY";
        }
        return "GENERAL_BANKING";
    }
    
    /**
     * Build reasoning for classification.
     */
    private String buildReasoning(boolean hasBankingScenario, double keywordScore, 
                                  boolean hasOutOfDomainIndicators, boolean contextIsBanking) {
        List<String> reasons = new ArrayList<>();
        if (hasBankingScenario) {
            reasons.add("matches banking scenario");
        }
        if (keywordScore > 0) {
            reasons.add("contains banking keywords");
        }
        if (contextIsBanking) {
            reasons.add("context is banking");
        }
        if (hasOutOfDomainIndicators) {
            reasons.add("has out-of-domain indicators");
        }
        
        if (reasons.isEmpty()) {
            return "no clear banking indicators";
        }
        
        return String.join(", ", reasons);
    }
    
    /**
     * Domain classification result.
     */
    @lombok.Data
    @lombok.Builder
    public static class DomainClassificationResult {
        private boolean isBanking;
        private double confidence;
        private String reasoning;
        private String bankingCategory;
        
        public static DomainClassificationResult banking(double confidence, String reasoning) {
            return DomainClassificationResult.builder()
                .isBanking(true)
                .confidence(confidence)
                .reasoning(reasoning)
                .build();
        }
        
        public static DomainClassificationResult outOfDomain(double confidence, String reasoning) {
            return DomainClassificationResult.builder()
                .isBanking(false)
                .confidence(confidence)
                .reasoning(reasoning)
                .build();
        }
        
        public static DomainClassificationResult unknown(String reasoning) {
            return DomainClassificationResult.builder()
                .isBanking(false)
                .confidence(0.0)
                .reasoning(reasoning)
                .build();
        }
    }
}
