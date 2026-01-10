package com.enterprise.ai.api.service;

import com.enterprise.ai.common.dto.IntentResult;
import com.enterprise.ai.data.entity.AiScenario;
import com.enterprise.ai.data.service.ConfigCacheService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Service for validating intent detection results.
 * Implements the 3-layer protection system:
 * 1. Confidence threshold checking
 * 2. Required parameter enforcement (from DB)
 * 3. Ambiguity detection and domain sanity checks
 * 
 * NOTE: All required parameters are loaded from ai_scenarios table - NO HARDCODING.
 */
@Slf4j
@Service
public class IntentValidationService {

    private final ConfigCacheService configCacheService;
    private final ObjectMapper objectMapper;

    @Value("${intent.confidence.threshold:0.75}")
    private double confidenceThreshold;

    @Value("${intent.confidence.low-threshold:0.60}")
    private double lowConfidenceThreshold;

    // Ambiguity detection patterns - could be moved to DB in future
    private static final Map<String, List<Pattern>> AMBIGUITY_PATTERNS = Map.of(
            "BALANCE_WITH_PENDING", List.of(
                    Pattern.compile("(?i)balance.*pending"),
                    Pattern.compile("(?i)pending.*balance")
            ),
            "GENERIC_STATUS", List.of(
                    Pattern.compile("(?i)^status\\s*$"),
                    Pattern.compile("(?i)^check\\s+status\\s*$"),
                    Pattern.compile("(?i)^kya\\s+status\\s+hai\\s*$")
            )
    );

    public IntentValidationService(ConfigCacheService configCacheService, ObjectMapper objectMapper) {
        this.configCacheService = configCacheService;
        this.objectMapper = objectMapper;
    }

    /**
     * Validation result containing all checks
     */
    public record ValidationResult(
            boolean isValid,
            boolean needsConfirmation,
            boolean isAmbiguous,
            boolean hasLowConfidence,
            boolean isUnknown,
            List<String> missingRequiredParams,
            String validationMessage,
            List<String> suggestedScenarios
    ) {
        public static ValidationResult valid() {
            return new ValidationResult(true, false, false, false, false, List.of(), null, null);
        }

        public static ValidationResult needsConfirmation(String message) {
            return new ValidationResult(false, true, false, false, false, List.of(), message, null);
        }

        public static ValidationResult ambiguous(String message, List<String> scenarios) {
            return new ValidationResult(false, false, true, false, false, List.of(), message, scenarios);
        }

        public static ValidationResult lowConfidence(String message) {
            return new ValidationResult(false, false, false, true, false, List.of(), message, null);
        }

        public static ValidationResult unknown(String message) {
            return new ValidationResult(false, false, false, false, true, List.of(), message, null);
        }

        public static ValidationResult missingParams(List<String> params) {
            return new ValidationResult(false, false, false, false, false, params,
                    "Missing required parameters: " + String.join(", ", params), null);
        }
    }

    /**
     * Validate the intent result using 3-layer protection.
     */
    public ValidationResult validate(IntentResult intent, String userQuery) {
        log.debug("Validating intent: {} with confidence: {}", intent.getScenario(), intent.getConfidence());

        // Layer 1: Check for UNKNOWN or invalid scenario
        if (intent.getScenario() == null || "UNKNOWN".equals(intent.getScenario())) {
            log.info("Unknown intent detected - will engage conversationally");
            // Return special flag to trigger conversational AI
            return ValidationResult.unknown(intent.getReasoning() != null ? intent.getReasoning() : "UNKNOWN_INTENT");
        }

        // Layer 1: Confidence threshold check
        // Use scenario's own threshold if available, otherwise use global threshold
        double effectiveThreshold = lowConfidenceThreshold;
        if (intent.getScenario() != null && !"UNKNOWN".equals(intent.getScenario()) && !"AMBIGUOUS".equals(intent.getScenario())) {
            Optional<AiScenario> scenario = configCacheService.getScenarioByCode(intent.getScenario());
            if (scenario.isPresent() && scenario.get().getConfidenceThreshold() != null) {
                // Use scenario's threshold, but don't let it go too low (minimum 0.5)
                effectiveThreshold = Math.max(0.5, Math.min(scenario.get().getConfidenceThreshold(), lowConfidenceThreshold));
                log.debug("Using scenario-specific threshold {} for {}", effectiveThreshold, intent.getScenario());
            }
        }
        
        if (intent.getConfidence() < effectiveThreshold) {
            log.info("Intent confidence {} below threshold {} for scenario {}", 
                intent.getConfidence(), effectiveThreshold, intent.getScenario());
            return ValidationResult.lowConfidence(
                    String.format("Low confidence (%.0f%%) in understanding your request", intent.getConfidence() * 100)
            );
        }

        // Layer 1: Check for explicit ambiguity from LLM
        if ("AMBIGUOUS".equals(intent.getScenario()) || intent.isAmbiguous()) {
            log.info("Ambiguous intent detected with possible scenarios: {}", intent.getPossibleScenarios());
            return ValidationResult.ambiguous(
                    "Your query could mean multiple things",
                    intent.getPossibleScenarios() != null ? intent.getPossibleScenarios() : List.of()
            );
        }

        // Layer 2: Required parameter enforcement (from DB)
        List<String> missingParams = validateRequiredParams(intent);
        if (!missingParams.isEmpty()) {
            log.info("Missing required params for {}: {}", intent.getScenario(), missingParams);
            return ValidationResult.missingParams(missingParams);
        }

        // Layer 3: Domain sanity checks for ambiguity
        Optional<ValidationResult> ambiguityCheck = checkDomainAmbiguity(intent, userQuery);
        if (ambiguityCheck.isPresent()) {
            return ambiguityCheck.get();
        }

        // Layer 3: Moderate confidence - suggest confirmation
        // For common banking scenarios like TRANSACTION_HISTORY, allow lower confidence (60%+)
        // Only ask for confirmation if confidence is really low (< 50%)
        double confirmationThreshold = confidenceThreshold;
        if (intent.getScenario() != null && isCommonBankingScenario(intent.getScenario())) {
            // Common scenarios: TRANSACTION_HISTORY, ACCOUNT_BALANCE, ACCOUNT_SUMMARY
            // Allow execution with 60% confidence, only confirm if < 50%
            confirmationThreshold = 0.50;
        }
        
        if (intent.getConfidence() < confirmationThreshold) {
            log.info("Low confidence {} - suggesting confirmation (threshold: {})", 
                intent.getConfidence(), confirmationThreshold);
            return ValidationResult.needsConfirmation(
                    String.format("I'm %.0f%% sure you want to check %s. Please confirm.",
                            intent.getConfidence() * 100, getScenarioDescription(intent.getScenario()))
            );
        }

        return ValidationResult.valid();
    }
    
    /**
     * Check if scenario is a common banking scenario that should execute with lower confidence.
     */
    private boolean isCommonBankingScenario(String scenarioCode) {
        if (scenarioCode == null) return false;
        String code = scenarioCode.toUpperCase();
        return code.equals("TRANSACTION_HISTORY") || 
               code.equals("ACCOUNT_BALANCE") || 
               code.equals("ACCOUNT_SUMMARY") ||
               code.equals("CARD_DETAILS") ||
               code.equals("BILL_PAYMENT_HISTORY");
    }

    /**
     * Validate that all required parameters are present.
     * Loads required parameters from database via ConfigCacheService.
     */
    private List<String> validateRequiredParams(IntentResult intent) {
        // Get required params from DB
        List<String> requiredParams = getRequiredParamsFromDb(intent.getScenario());
        List<String> missing = new ArrayList<>();

        // Check what LLM reported as missing
        if (intent.getMissingParams() != null) {
            missing.addAll(intent.getMissingParams());
        }

        // Double-check by verifying params actually exist and have values
        Map<String, Object> params = intent.getParams();
        if (params == null) {
            params = Map.of();
        }

        for (String required : requiredParams) {
            Object value = params.get(required);
            if (value == null || (value instanceof String && ((String) value).isBlank())) {
                if (!missing.contains(required)) {
                    missing.add(required);
                }
            }
        }

        return missing;
    }

    /**
     * Get required parameters from database for a scenario.
     * Falls back to empty list if scenario not found.
     */
    private List<String> getRequiredParamsFromDb(String scenarioCode) {
        return configCacheService.getScenarioByCode(scenarioCode)
                .map(this::parseRequiredParams)
                .orElseGet(() -> {
                    log.warn("Scenario {} not found in database, no required params", scenarioCode);
                    return List.of();
                });
    }

    /**
     * Parse required_params JSON array from AiScenario.
     */
    private List<String> parseRequiredParams(AiScenario scenario) {
        String json = scenario.getRequiredParams();
        if (json == null || json.isEmpty()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.error("Error parsing required_params for scenario {}, JSON: {}: {}", 
                    scenario.getScenarioCode(), json, e.getMessage());
            return List.of();
        }
    }

    /**
     * Check for domain-specific ambiguity patterns.
     */
    private Optional<ValidationResult> checkDomainAmbiguity(IntentResult intent, String userQuery) {
        String query = userQuery.toLowerCase();

        // Check: ACCOUNT_SUMMARY with "pending" might be transaction
        if ("ACCOUNT_SUMMARY".equals(intent.getScenario())) {
            for (Pattern pattern : AMBIGUITY_PATTERNS.get("BALANCE_WITH_PENDING")) {
                if (pattern.matcher(query).find()) {
                    log.info("Ambiguity detected: balance with pending in query");
                    return Optional.of(ValidationResult.ambiguous(
                            "Are you asking about your account balance or a pending transaction?",
                            List.of("ACCOUNT_SUMMARY", "TXN_STATUS")
                    ));
                }
            }
        }

        // Check: Generic "status" queries
        for (Pattern pattern : AMBIGUITY_PATTERNS.get("GENERIC_STATUS")) {
            if (pattern.matcher(query).find()) {
                log.info("Ambiguity detected: generic status query");
                // Get active scenarios for clarification
                List<String> statusScenarios = getStatusRelatedScenarios();
                return Optional.of(ValidationResult.ambiguous(
                        "What status would you like to check?",
                        statusScenarios
                ));
            }
        }

        return Optional.empty();
    }

    /**
     * Get status-related scenarios from DB for clarification.
     */
    private List<String> getStatusRelatedScenarios() {
        return configCacheService.getActiveScenarios().stream()
                .map(AiScenario::getScenarioCode)
                .filter(code -> code.contains("STATUS") || code.contains("SUMMARY"))
                .limit(5)  // Limit to 5 options for user-friendliness
                .toList();
    }

    /**
     * Get common scenarios for clarification options (loaded from DB).
     * Returns up to 5 most relevant scenarios based on user role access.
     */
    public List<String> getCommonScenarios() {
        return configCacheService.getActiveScenarios().stream()
                .filter(s -> !"AMBIGUOUS".equals(s.getScenarioCode()))
                .filter(s -> !"UNKNOWN".equals(s.getScenarioCode()))
                .map(AiScenario::getScenarioCode)
                .limit(5)
                .toList();
    }

    /**
     * Get human-readable description of scenario from database.
     */
    public String getScenarioDescription(String scenario) {
        return configCacheService.getScenarioByCode(scenario)
                .map(AiScenario::getDescription)
                .orElse(scenario.toLowerCase().replace("_", " "));
    }

    /**
     * Generate clarification options for ambiguous queries.
     */
    public List<String> generateClarificationOptions(List<String> scenarios) {
        return scenarios.stream()
                .map(this::getScenarioDescription)
                .toList();
    }
}
