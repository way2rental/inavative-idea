package com.enterprise.ai.common.enums;

/**
 * Security levels for scenarios.
 */
public enum SecurityLevel {
    PUBLIC,     // No authentication required
    AUTH,       // Authentication required
    INTERNAL    // Internal use only, restricted roles
}
