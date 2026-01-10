package com.enterprise.ai.core.mapper;

import com.enterprise.ai.data.service.SystemConfigService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Masking Service for sensitive data.
 * Per ENTERPRISE_AI_RESPONSE_MAPPING_AND_SSE_SPEC.md Section 8.
 * 
 * Masking Types:
 * - ACCOUNT: XXXX-XXXX-1234 (last 4 digits visible)
 * - PAN: AA******Z (first 2 + last 1 visible)
 * - AADHAAR: XXXX-XXXX-1234 (last 4 digits visible)
 * - CARD: XXXX-XXXX-XXXX-1234 (last 4 digits visible)
 * - NONE: No masking applied
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MaskingService {

    private final SystemConfigService systemConfigService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Apply masking based on type.
     * 
     * @param value The value to mask
     * @param maskingType Type of masking to apply
     * @return Masked value
     */
    public Object mask(Object value, String maskingType) {
        if (value == null || maskingType == null || "NONE".equalsIgnoreCase(maskingType)) {
            return value;
        }

        String stringValue = value.toString();
        if (stringValue.isEmpty()) {
            return stringValue;
        }

        // Masking types are now configurable via SystemConfig (key: "masking_patterns" as JSON)
        // Custom masking patterns can be added dynamically via SystemConfig
        // Format: {"CUSTOM_TYPE": {"pattern": "regex", "format": "template", "visibleChars": 4}}
        // For now, using hardcoded types as per spec, but extensible via SystemConfig
        
        String typeUpper = maskingType.toUpperCase();
        
        // Check if custom masking pattern exists in SystemConfig
        String customPatternsJson = systemConfigService.getJson("masking_patterns");
        if (customPatternsJson != null && !customPatternsJson.isEmpty()) {
            try {
                Map<String, Map<String, Object>> customPatterns = objectMapper.readValue(
                    customPatternsJson, new TypeReference<Map<String, Map<String, Object>>>() {});
                
                if (customPatterns.containsKey(typeUpper)) {
                    return applyCustomMasking(stringValue, customPatterns.get(typeUpper));
                }
            } catch (Exception e) {
                log.debug("Failed to parse custom masking patterns: {}", e.getMessage());
            }
        }
        
        // Use built-in masking types
        try {
            return switch (typeUpper) {
                case "ACCOUNT" -> maskAccount(stringValue);
                case "PAN" -> maskPan(stringValue);
                case "AADHAAR" -> maskAadhaar(stringValue);
                case "CARD" -> maskCard(stringValue);
                case "EMAIL" -> maskEmail(stringValue);
                case "PHONE" -> maskPhone(stringValue);
                default -> value;
            };
        } catch (Exception e) {
            log.warn("Failed to mask value with type {}: {}", maskingType, e.getMessage());
            return "****";
        }
    }

    /**
     * Mask account number: XXXX-XXXX-1234
     * Shows last 4 digits only.
     */
    private String maskAccount(String account) {
        String cleaned = account.replaceAll("[^0-9]", "");
        if (cleaned.length() < 4) {
            return "****";
        }
        String last4 = cleaned.substring(cleaned.length() - 4);
        return "XXXX-XXXX-" + last4;
    }

    /**
     * Mask PAN card: AA******Z
     * Shows first 2 and last 1 characters.
     */
    private String maskPan(String pan) {
        String cleaned = pan.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
        if (cleaned.length() < 10) {
            return "**********";
        }
        return cleaned.substring(0, 2) + "******" + cleaned.substring(9);
    }

    /**
     * Mask Aadhaar: XXXX-XXXX-1234
     * Shows last 4 digits only.
     */
    private String maskAadhaar(String aadhaar) {
        String cleaned = aadhaar.replaceAll("[^0-9]", "");
        if (cleaned.length() < 4) {
            return "XXXX-XXXX-****";
        }
        String last4 = cleaned.substring(cleaned.length() - 4);
        return "XXXX-XXXX-" + last4;
    }

    /**
     * Mask card number: XXXX-XXXX-XXXX-1234
     * Shows last 4 digits only.
     */
    private String maskCard(String card) {
        String cleaned = card.replaceAll("[^0-9]", "");
        if (cleaned.length() < 4) {
            return "XXXX-XXXX-XXXX-****";
        }
        String last4 = cleaned.substring(cleaned.length() - 4);
        return "XXXX-XXXX-XXXX-" + last4;
    }

    /**
     * Mask email: a***@gmail.com
     * Shows first char and domain.
     */
    private String maskEmail(String email) {
        int atIndex = email.indexOf('@');
        if (atIndex <= 0) {
            return "***@***.com";
        }
        String local = email.substring(0, atIndex);
        String domain = email.substring(atIndex);
        if (local.length() <= 1) {
            return "*" + domain;
        }
        return local.charAt(0) + "***" + domain;
    }

    /**
     * Mask phone: +91 XXXXX-12345
     * Shows last 5 digits.
     */
    private String maskPhone(String phone) {
        String cleaned = phone.replaceAll("[^0-9]", "");
        if (cleaned.length() < 5) {
            return "XXXXX-*****";
        }
        String last5 = cleaned.substring(cleaned.length() - 5);
        return "XXXXX-" + last5;
    }

    /**
     * Check if a value needs masking based on pattern detection.
     * Useful for auto-detection of sensitive data.
     */
    public String detectMaskingType(String fieldName, Object value) {
        String name = fieldName.toLowerCase();
        
        if (name.contains("account") || name.contains("acct")) {
            return "ACCOUNT";
        }
        if (name.contains("pan")) {
            return "PAN";
        }
        if (name.contains("aadhaar") || name.contains("aadhar")) {
            return "AADHAAR";
        }
        if (name.contains("card") && (name.contains("number") || name.contains("no"))) {
            return "CARD";
        }
        if (name.contains("email")) {
            return "EMAIL";
        }
        if (name.contains("phone") || name.contains("mobile")) {
            return "PHONE";
        }
        
        return "NONE";
    }

    /**
     * Apply custom masking pattern from SystemConfig.
     * 
     * @param value Value to mask
     * @param patternConfig Custom pattern configuration from SystemConfig
     * @return Masked value
     */
    private String applyCustomMasking(String value, Map<String, Object> patternConfig) {
        try {
            String pattern = (String) patternConfig.get("pattern");
            String format = (String) patternConfig.get("format");
            Integer visibleChars = patternConfig.get("visibleChars") != null 
                ? ((Number) patternConfig.get("visibleChars")).intValue() : 4;
            
            // Apply regex pattern if provided
            if (pattern != null && !pattern.isEmpty()) {
                java.util.regex.Pattern regex = java.util.regex.Pattern.compile(pattern);
                java.util.regex.Matcher matcher = regex.matcher(value);
                if (matcher.find()) {
                    String matched = matcher.group(0);
                    String cleaned = matched.replaceAll("[^A-Za-z0-9]", "");
                    
                    if (format != null && !format.isEmpty()) {
                        // Use format template with visible chars
                        String visible = cleaned.length() >= visibleChars 
                            ? cleaned.substring(cleaned.length() - visibleChars) 
                            : cleaned;
                        return format.replace("${visible}", visible);
                    } else {
                        // Default format: show last N chars
                        String visible = cleaned.length() >= visibleChars 
                            ? cleaned.substring(cleaned.length() - visibleChars) 
                            : cleaned;
                        return "****-" + visible;
                    }
                }
            }
            
            // Fallback to simple masking
            return "****";
        } catch (Exception e) {
            log.warn("Failed to apply custom masking pattern: {}", e.getMessage());
            return "****";
        }
    }
}
