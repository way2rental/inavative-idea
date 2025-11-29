package com.enterprise.ai.core.scenario;

import com.enterprise.ai.common.dto.ScenarioRequest;
import com.enterprise.ai.common.dto.ScenarioResult;
import reactor.core.publisher.Mono;

/**
 * Reactive scenario executor interface for non-blocking execution.
 * Executors should implement this for async processing.
 */
public interface ReactiveScenarioExecutor {

    /**
     * Get the unique scenario code.
     * @return scenario code (e.g., "TXN_STATUS", "FILE_STATUS")
     */
    String getScenarioCode();

    /**
     * Execute the scenario reactively (non-blocking).
     * @param request scenario request containing parameters
     * @return Mono of scenario result with data
     */
    Mono<ScenarioResult> executeReactive(ScenarioRequest request);

    /**
     * Get maximum execution time in milliseconds.
     * Execution will be terminated if it exceeds this time.
     * @return max execution time in ms
     */
    default long getMaxExecutionTimeMs() {
        return 30000; // 30 seconds default
    }

    /**
     * Get timeout for database queries in milliseconds.
     * @return db timeout in ms
     */
    default long getDbTimeoutMs() {
        return 5000; // 5 seconds default
    }
}
