package com.enterprise.ai.common.exception;

/**
 * Base exception for AI orchestrator.
 */
public class AiOrchestratorException extends RuntimeException {

    public AiOrchestratorException(String message) {
        super(message);
    }

    public AiOrchestratorException(String message, Throwable cause) {
        super(message, cause);
    }
}
