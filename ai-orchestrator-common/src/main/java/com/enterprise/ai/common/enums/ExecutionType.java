package com.enterprise.ai.common.enums;

/**
 * Execution types for dynamic scenario executors.
 * All types are READ-ONLY by design for enterprise security compliance.
 */
public enum ExecutionType {
    /**
     * Execute SELECT queries only.
     * Blocks any INSERT, UPDATE, DELETE, DROP, etc.
     */
    DB_QUERY,

    /**
     * Execute HTTP GET or safe POST calls.
     * URL must be in whitelist.
     */
    HTTP_CALL;

    /**
     * Check if this execution type is supported.
     */
    public static boolean isSupported(String type) {
        if (type == null) return false;
        try {
            valueOf(type);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
