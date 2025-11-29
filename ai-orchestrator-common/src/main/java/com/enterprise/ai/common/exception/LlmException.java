package com.enterprise.ai.common.exception;

/**
 * Exception thrown when LLM communication fails.
 */
public class LlmException extends AiOrchestratorException {

    public LlmException(String message) {
        super(message);
    }

    public LlmException(String message, Throwable cause) {
        super(message, cause);
    }
}
