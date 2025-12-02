package com.enterprise.ai.core.mapper;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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
public class MaskingService {

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

        // TODO think and make them dynamic so that new masking types can be added via config using patterns
        // For now, hardcoded types as per spec
        try {
            return switch (maskingType.toUpperCase()) {
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

    public static void main(String[] args) {
        MaskingService maskingService = new MaskingService();
        System.out.println(maskingService.maskAccount("123412342345"));
    }
}
