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
    HTTP_CALL,

    /**
     * LLM-only execution - no database or HTTP calls.
     * Used for content generation scenarios like email drafting,
     * summarization, and conversational responses based on chat history.
     * The LLM generates content directly without external data fetching.
     */
    LLM_ONLY;

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
