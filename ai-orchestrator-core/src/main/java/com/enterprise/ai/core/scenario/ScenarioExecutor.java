package com.enterprise.ai.core.scenario;

import com.enterprise.ai.common.dto.ScenarioRequest;
import com.enterprise.ai.common.dto.ScenarioResult;

/**
 * Interface for scenario executors.
 * Each scenario (TXN_STATUS, FILE_STATUS, etc.) must implement this interface.
 */
public interface ScenarioExecutor {

    /**
     * Get the unique scenario code.
     * @return scenario code (e.g., "TXN_STATUS", "FILE_STATUS")
     */
    String getScenarioCode();

    /**
     * Execute the scenario with the given request.
     * @param request scenario request containing parameters
     * @return scenario result with data
     */
    ScenarioResult execute(ScenarioRequest request);
}
