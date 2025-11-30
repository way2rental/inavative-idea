package com.enterprise.ai.core.router;

import com.enterprise.ai.common.dto.ScenarioRequest;
import com.enterprise.ai.common.dto.ScenarioResult;
import com.enterprise.ai.common.exception.ScenarioNotFoundException;
import com.enterprise.ai.core.scenario.ScenarioExecutor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Routes scenario requests to appropriate executors.
 */
@Service
public class ScenarioRouter {

    private final Map<String, ScenarioExecutor> executors;

    public ScenarioRouter(List<ScenarioExecutor> executorList) {
        this.executors = executorList.stream()
                .collect(Collectors.toMap(
                        ScenarioExecutor::getScenarioCode,
                        e -> e
                ));
    }

    /**
     * Route a scenario request to the appropriate executor.
     * @param request scenario request
     * @return scenario result
     * @throws ScenarioNotFoundException if no executor is found
     */
    public ScenarioResult route(ScenarioRequest request) {
        ScenarioExecutor executor = executors.get(request.getScenario());
        if (executor == null) {
            throw new ScenarioNotFoundException(request.getScenario());
        }
        return executor.execute(request);
    }

    /**
     * Check if an executor exists for the given scenario.
     * @param scenarioCode scenario code
     * @return true if executor exists
     */
    public boolean hasExecutor(String scenarioCode) {
        return executors.containsKey(scenarioCode);
    }

    /**
     * Get all registered scenario codes.
     * @return set of scenario codes
     */
    public java.util.Set<String> getRegisteredScenarios() {
        return executors.keySet();
    }
}
