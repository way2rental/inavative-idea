package com.enterprise.ai.api.service;

import com.enterprise.ai.common.dto.IntentResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Service for validating intent detection results.
 * Implements the 3-layer protection system:
 * 1. Confidence threshold checking
 * 2. Required parameter enforcement
 * 3. Ambiguity detection and domain sanity checks
 */
@Slf4j
@Service
public class IntentValidationService {

    @Value("${intent.confidence.threshold:0.75}")
    private double confidenceThreshold;

    @Value("${intent.confidence.low-threshold:0.60}")
    private double lowConfidenceThreshold;

    // Required parameters for each scenario
    private static final Map<String, List<String>> REQUIRED_PARAMS = Map.of(
            "TXN_STATUS", List.of("txnId"),
            "FILE_STATUS", List.of("fileName"),
            "ACCOUNT_SUMMARY", List.of("accountId")
    );

    // Ambiguity detection patterns
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

    /**
     * Validation result containing all checks
     */
    public record ValidationResult(
            boolean isValid,
            boolean needsConfirmation,
            boolean isAmbiguous,
            boolean hasLowConfidence,
            List<String> missingRequiredParams,
            String validationMessage,
            List<String> suggestedScenarios
    ) {
        public static ValidationResult valid() {
            return new ValidationResult(true, false, false, false, List.of(), null, null);
        }

        public static ValidationResult needsConfirmation(String message) {
            return new ValidationResult(false, true, false, false, List.of(), message, null);
        }

        public static ValidationResult ambiguous(String message, List<String> scenarios) {
            return new ValidationResult(false, false, true, false, List.of(), message, scenarios);
        }

        public static ValidationResult lowConfidence(String message) {
            return new ValidationResult(false, false, false, true, List.of(), message, null);
        }

        public static ValidationResult missingParams(List<String> params) {
            return new ValidationResult(false, false, false, false, params, 
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
            log.info("Unknown intent detected");
            return ValidationResult.lowConfidence("Unable to understand the request");
        }

        // Layer 1: Confidence threshold check
        if (intent.getConfidence() < lowConfidenceThreshold) {
            log.info("Intent confidence {} below threshold {}", intent.getConfidence(), lowConfidenceThreshold);
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

        // Layer 2: Required parameter enforcement
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
        if (intent.getConfidence() < confidenceThreshold) {
            log.info("Moderate confidence {} - suggesting confirmation", intent.getConfidence());
            return ValidationResult.needsConfirmation(
                    String.format("I'm %.0f%% sure you want to check %s. Please confirm.",
                            intent.getConfidence() * 100, getScenarioDescription(intent.getScenario()))
            );
        }

        return ValidationResult.valid();
    }

    /**
     * Validate that all required parameters are present.
     */
    private List<String> validateRequiredParams(IntentResult intent) {
        List<String> requiredParams = REQUIRED_PARAMS.getOrDefault(intent.getScenario(), List.of());
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
                return Optional.of(ValidationResult.ambiguous(
                        "What status would you like to check?",
                        List.of("TXN_STATUS", "FILE_STATUS", "ACCOUNT_SUMMARY")
                ));
            }
        }

        return Optional.empty();
    }

    /**
     * Get human-readable description of scenario.
     */
    public String getScenarioDescription(String scenario) {
        return switch (scenario) {
            case "TXN_STATUS" -> "transaction status";
            case "FILE_STATUS" -> "file processing status";
            case "ACCOUNT_SUMMARY" -> "account summary/balance";
            default -> scenario.toLowerCase().replace("_", " ");
        };
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
