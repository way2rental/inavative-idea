package com.enterprise.ai.common.exception;

/**
 * Exception thrown when a security violation is detected.
 * This includes:
 * - Non-SELECT SQL queries
 * - HTTP calls to non-whitelisted URLs
 * - Attempts to write data
 */
public class SecurityViolationException extends AiOrchestratorException {

    private final String violationType;
    private final String attemptedOperation;

    public SecurityViolationException(String message) {
        super(message);
        this.violationType = "UNKNOWN";
        this.attemptedOperation = null;
    }

    public SecurityViolationException(String message, String violationType, String attemptedOperation) {
        super(message);
        this.violationType = violationType;
        this.attemptedOperation = attemptedOperation;
    }

    public String getViolationType() {
        return violationType;
    }

    public String getAttemptedOperation() {
        return attemptedOperation;
    }
}
