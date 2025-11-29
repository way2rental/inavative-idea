package com.enterprise.ai.core.scenario;

import com.enterprise.ai.common.dto.ScenarioRequest;
import com.enterprise.ai.common.dto.ScenarioResult;
import com.enterprise.ai.core.client.BusinessDataClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

/**
 * Account Summary executor with real HTTP calls to business data service.
 * Supports both sync and async execution modes.
 */
@Slf4j
@RequiredArgsConstructor
public class AccountSummaryExecutor implements ScenarioExecutor, ReactiveScenarioExecutor {

    public static final String SCENARIO_CODE = "ACCOUNT_SUMMARY";
    private static final long MAX_EXECUTION_TIME_MS = 30000; // 30 seconds
    private static final long DB_TIMEOUT_MS = 5000; // 5 seconds

    private final BusinessDataClient businessDataClient;

    @Override
    public String getScenarioCode() {
        return SCENARIO_CODE;
    }

    @Override
    public ScenarioResult execute(ScenarioRequest request) {
        // Blocking execution (for backwards compatibility)
        return executeReactive(request)
                .block(Duration.ofMillis(MAX_EXECUTION_TIME_MS));
    }

    @Override
    public Mono<ScenarioResult> executeReactive(ScenarioRequest request) {
        log.info("Executing ACCOUNT_SUMMARY scenario for accountId: {}", request.getParams().get("accountId"));
        
        return businessDataClient.fetchBusinessData(SCENARIO_CODE, request.getParams())
                .timeout(Duration.ofMillis(DB_TIMEOUT_MS))
                .map(data -> ScenarioResult.builder()
                        .scenario(SCENARIO_CODE)
                        .data(data)
                        .success(true)
                        .build())
                .doOnSuccess(r -> log.debug("ACCOUNT_SUMMARY executed successfully"))
                .doOnError(e -> log.error("ACCOUNT_SUMMARY execution failed: {}", e.getMessage()))
                .onErrorReturn(ScenarioResult.builder()
                        .scenario(SCENARIO_CODE)
                        .data(Map.of("error", "Failed to fetch account summary"))
                        .success(false)
                        .errorMessage("Service temporarily unavailable")
                        .build());
    }

    @Override
    public long getMaxExecutionTimeMs() {
        return MAX_EXECUTION_TIME_MS;
    }

    @Override
    public long getDbTimeoutMs() {
        return DB_TIMEOUT_MS;
    }
}
