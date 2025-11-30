package com.enterprise.ai.llm.validation;

import com.enterprise.ai.common.dto.IntentResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Strict JSON Schema Validator for Ollama output.
 * Prevents:
 * - Hallucinated keys
 * - Null scenario
 * - Broken params
 * - Schema mismatches
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IntentSchemaValidator {

    private final ObjectMapper objectMapper;

    // Required fields in intent output
    private static final Set<String> REQUIRED_FIELDS = Set.of("scenario", "confidence");
    
    // Optional but expected fields
    private static final Set<String> OPTIONAL_FIELDS = Set.of(
            "params", "missingParams", "reasoning", "possibleScenarios"
    );
    
    // All allowed fields
    private static final Set<String> ALLOWED_FIELDS = new HashSet<>();
    static {
        ALLOWED_FIELDS.addAll(REQUIRED_FIELDS);
        ALLOWED_FIELDS.addAll(OPTIONAL_FIELDS);
    }

    /**
     * Validate intent JSON against expected schema.
     * @return ValidationResult with success status and any errors
     */
    public ValidationResult validate(String jsonResponse) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        if (jsonResponse == null || jsonResponse.isBlank()) {
            return ValidationResult.builder()
                    .valid(false)
                    .errors(List.of("Empty JSON response from LLM"))
                    .build();
        }

        try {
            JsonNode root = objectMapper.readTree(jsonResponse);
            
            // Check if it's an object
            if (!root.isObject()) {
                return ValidationResult.builder()
                        .valid(false)
                        .errors(List.of("Response is not a JSON object"))
                        .build();
            }

            // Check required fields
            for (String field : REQUIRED_FIELDS) {
                if (!root.has(field) || root.get(field).isNull()) {
                    errors.add("Missing required field: " + field);
                }
            }

            // Validate scenario field
            if (root.has("scenario")) {
                JsonNode scenarioNode = root.get("scenario");
                if (scenarioNode.isNull()) {
                    errors.add("Scenario is null");
                } else if (!scenarioNode.isTextual()) {
                    errors.add("Scenario must be a string");
                } else {
                    String scenario = scenarioNode.asText();
                    if (scenario.isBlank()) {
                        errors.add("Scenario is empty");
                    }
                }
            }

            // Validate confidence field
            if (root.has("confidence")) {
                JsonNode confidenceNode = root.get("confidence");
                if (!confidenceNode.isNumber()) {
                    errors.add("Confidence must be a number");
                } else {
                    double confidence = confidenceNode.asDouble();
                    if (confidence < 0 || confidence > 1) {
                        warnings.add("Confidence should be between 0 and 1, got: " + confidence);
                    }
                }
            }

            // Validate params field
            if (root.has("params")) {
                JsonNode paramsNode = root.get("params");
                if (!paramsNode.isObject() && !paramsNode.isNull()) {
                    errors.add("Params must be an object");
                }
            }

            // Validate missingParams field
            if (root.has("missingParams")) {
                JsonNode missingParamsNode = root.get("missingParams");
                if (!missingParamsNode.isArray() && !missingParamsNode.isNull()) {
                    errors.add("MissingParams must be an array");
                }
            }

            // Check for hallucinated keys
            Iterator<String> fieldNames = root.fieldNames();
            while (fieldNames.hasNext()) {
                String fieldName = fieldNames.next();
                if (!ALLOWED_FIELDS.contains(fieldName)) {
                    warnings.add("Unexpected field (possible hallucination): " + fieldName);
                }
            }

            return ValidationResult.builder()
                    .valid(errors.isEmpty())
                    .errors(errors)
                    .warnings(warnings)
                    .rawJson(jsonResponse)
                    .build();

        } catch (Exception e) {
            log.error("JSON parsing failed: {}", e.getMessage());
            return ValidationResult.builder()
                    .valid(false)
                    .errors(List.of("Invalid JSON: " + e.getMessage()))
                    .rawJson(jsonResponse)
                    .build();
        }
    }

    /**
     * Validate and parse intent JSON.
     * @return IntentResult if valid, or a fallback result if invalid
     */
    public IntentResult validateAndParse(String jsonResponse) {
        ValidationResult validation = validate(jsonResponse);
        
        if (!validation.isValid()) {
            log.warn("Intent validation failed: {}", validation.getErrors());
            // Return fallback intent
            return IntentResult.builder()
                    .scenario("AMBIGUOUS")
                    .confidence(0.0)
                    .reasoning("Schema validation failed: " + String.join(", ", validation.getErrors()))
                    .build();
        }

        try {
            return objectMapper.readValue(jsonResponse, IntentResult.class);
        } catch (Exception e) {
            log.error("Failed to deserialize valid JSON: {}", e.getMessage());
            return IntentResult.builder()
                    .scenario("AMBIGUOUS")
                    .confidence(0.0)
                    .reasoning("Deserialization failed: " + e.getMessage())
                    .build();
        }
    }

    /**
     * Validate intent result object (post-deserialization).
     */
    public ValidationResult validateIntent(IntentResult intent) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (intent == null) {
            return ValidationResult.builder()
                    .valid(false)
                    .errors(List.of("Intent is null"))
                    .build();
        }

        if (intent.getScenario() == null || intent.getScenario().isBlank()) {
            errors.add("Scenario is null or empty");
        }

        // Note: confidence is primitive double, so check for valid range only
        if (intent.getConfidence() < 0 || intent.getConfidence() > 1) {
            warnings.add("Confidence out of range: " + intent.getConfidence());
        }

        return ValidationResult.builder()
                .valid(errors.isEmpty())
                .errors(errors)
                .warnings(warnings)
                .build();
    }

    @Data
    @Builder
    public static class ValidationResult {
        private boolean valid;
        private List<String> errors;
        private List<String> warnings;
        private String rawJson;
    }
}
