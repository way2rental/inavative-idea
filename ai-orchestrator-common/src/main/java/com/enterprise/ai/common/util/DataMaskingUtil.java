package com.enterprise.ai.common.util;

import java.util.regex.Pattern;

/**
 * Utility class for masking sensitive data.
 */
public final class DataMaskingUtil {

    private static final Pattern CARD_NUMBER_PATTERN = Pattern.compile("\\b\\d{4}[- ]?\\d{4}[- ]?\\d{4}[- ]?\\d{4}\\b");
    private static final Pattern ACCOUNT_NUMBER_PATTERN = Pattern.compile("\\b\\d{9,18}\\b");
    private static final Pattern PAN_PATTERN = Pattern.compile("\\b[A-Z]{5}\\d{4}[A-Z]\\b");
    private static final Pattern AADHAAR_PATTERN = Pattern.compile("\\b\\d{4}[- ]?\\d{4}[- ]?\\d{4}\\b");

    private DataMaskingUtil() {
        // Utility class
    }

    /**
     * Mask a card number, showing only last 4 digits.
     */
    public static String maskCardNumber(String cardNumber) {
        if (cardNumber == null || cardNumber.length() < 4) {
            return "****";
        }
        String cleaned = cardNumber.replaceAll("[- ]", "");
        if (cleaned.length() >= 16) {
            return "XXXX-XXXX-XXXX-" + cleaned.substring(cleaned.length() - 4);
        }
        return "****" + cleaned.substring(cleaned.length() - 4);
    }

    /**
     * Mask an account number, showing only last 4 digits.
     */
    public static String maskAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.length() < 4) {
            return "XXXX";
        }
        return "XXXX-XXXX-" + accountNumber.substring(accountNumber.length() - 4);
    }

    /**
     * Mask PAN number.
     */
    public static String maskPan(String pan) {
        if (pan == null || pan.length() < 10) {
            return "**********";
        }
        return pan.substring(0, 2) + "******" + pan.substring(pan.length() - 2);
    }

    /**
     * Mask Aadhaar number.
     */
    public static String maskAadhaar(String aadhaar) {
        if (aadhaar == null || aadhaar.length() < 4) {
            return "XXXX-XXXX-XXXX";
        }
        String cleaned = aadhaar.replaceAll("[- ]", "");
        if (cleaned.length() >= 12) {
            return "XXXX-XXXX-" + cleaned.substring(cleaned.length() - 4);
        }
        return "XXXX-XXXX-" + cleaned.substring(cleaned.length() - 4);
    }

    /**
     * Mask all sensitive data in a text string.
     */
    public static String maskSensitiveData(String text) {
        if (text == null) {
            return null;
        }
        String result = text;
        result = CARD_NUMBER_PATTERN.matcher(result).replaceAll("XXXX-XXXX-XXXX-****");
        result = PAN_PATTERN.matcher(result).replaceAll("**********");
        result = AADHAAR_PATTERN.matcher(result).replaceAll("XXXX-XXXX-****");
        return result;
    }
}
