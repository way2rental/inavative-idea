package com.enterprise.ai.common.exception;

/**
 * Exception thrown when user is not authorized for a scenario.
 */
public class UnauthorizedScenarioAccessException extends AiOrchestratorException {

    public UnauthorizedScenarioAccessException(String scenario, String role) {
        super("Role '" + role + "' is not authorized to access scenario: " + scenario);
    }
}
