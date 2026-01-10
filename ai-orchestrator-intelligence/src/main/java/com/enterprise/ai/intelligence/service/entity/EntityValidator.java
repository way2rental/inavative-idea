package com.enterprise.ai.intelligence.service.entity;

import com.enterprise.ai.data.entity.EntityPattern;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Validates extracted entities using validation rules from database.
 * 
 * NO HARDCODING - All validation rules from database.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EntityValidator {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Validate an extracted entity.
     * 
     * @param pattern Entity pattern with validation rules
     * @param value Extracted entity value
     * @return true if valid, false otherwise
     */
    public boolean validate(EntityPattern pattern, String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }

        // Parse validation rule
        Map<String, Object> validationRule = parseValidationRule(pattern.getValidationRule());
        if (validationRule == null || validationRule.isEmpty()) {
            // No validation rule = always valid
            return true;
        }

        try {
            // Check min length
            if (validationRule.containsKey("minLength")) {
                int minLength = ((Number) validationRule.get("minLength")).intValue();
                if (value.length() < minLength) {
                    log.debug("Entity value {} failed minLength validation: {} < {}", value, value.length(), minLength);
                    return false;
                }
            }

            // Check max length
            if (validationRule.containsKey("maxLength")) {
                int maxLength = ((Number) validationRule.get("maxLength")).intValue();
                if (value.length() > maxLength) {
                    log.debug("Entity value {} failed maxLength validation: {} > {}", value, value.length(), maxLength);
                    return false;
                }
            }

            // Check format
            if (validationRule.containsKey("format")) {
                String format = (String) validationRule.get("format");
                if (!matchesFormat(value, format)) {
                    log.debug("Entity value {} failed format validation: {}", value, format);
                    return false;
                }
            }

            // Check allowed values
            if (validationRule.containsKey("allowedValues")) {
                @SuppressWarnings("unchecked")
                java.util.List<String> allowedValues = (java.util.List<String>) validationRule.get("allowedValues");
                if (!allowedValues.contains(value)) {
                    log.debug("Entity value {} not in allowed values", value);
                    return false;
                }
            }

            // Check regex validation
            if (validationRule.containsKey("regex")) {
                String regex = (String) validationRule.get("regex");
                if (!Pattern.matches(regex, value)) {
                    log.debug("Entity value {} failed regex validation: {}", value, regex);
                    return false;
                }
            }

            // Check numeric range
            if (validationRule.containsKey("minValue") || validationRule.containsKey("maxValue")) {
                try {
                    BigDecimal numericValue = new BigDecimal(value);
                    if (validationRule.containsKey("minValue")) {
                        BigDecimal minValue = new BigDecimal(validationRule.get("minValue").toString());
                        if (numericValue.compareTo(minValue) < 0) {
                            log.debug("Entity value {} failed minValue validation: {} < {}", value, numericValue, minValue);
                            return false;
                        }
                    }
                    if (validationRule.containsKey("maxValue")) {
                        BigDecimal maxValue = new BigDecimal(validationRule.get("maxValue").toString());
                        if (numericValue.compareTo(maxValue) > 0) {
                            log.debug("Entity value {} failed maxValue validation: {} > {}", value, numericValue, maxValue);
                            return false;
                        }
                    }
                } catch (NumberFormatException e) {
                    log.debug("Entity value {} is not numeric, skipping range validation", value);
                }
            }

            return true;
        } catch (Exception e) {
            log.error("Error validating entity value {}: {}", value, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Parse validation rule JSON.
     */
    private Map<String, Object> parseValidationRule(String validationRule) {
        if (validationRule == null || validationRule.isEmpty()) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(validationRule, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse validation rule: {}", e.getMessage());
            return new HashMap<>();
        }
    }

    /**
     * Check if value matches format.
     */
    private boolean matchesFormat(String value, String format) {
        switch (format.toUpperCase()) {
            case "ALPHANUMERIC":
                return value.matches("^[a-zA-Z0-9]+$");
            case "NUMERIC":
                return value.matches("^[0-9]+$");
            case "ALPHABETIC":
                return value.matches("^[a-zA-Z]+$");
            case "UPPERCASE":
                return value.equals(value.toUpperCase());
            case "LOWERCASE":
                return value.equals(value.toLowerCase());
            default:
                // Unknown format = always valid
                return true;
        }
    }
}
