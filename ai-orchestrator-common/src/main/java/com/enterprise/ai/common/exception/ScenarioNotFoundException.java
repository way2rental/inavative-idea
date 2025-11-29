package com.enterprise.ai.common.exception;

/**
 * Exception thrown when a scenario is not found.
 */
public class ScenarioNotFoundException extends AiOrchestratorException {

    public ScenarioNotFoundException(String scenario) {
        super("Scenario not found: " + scenario);
    }
}
