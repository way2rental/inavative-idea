package com.enterprise.ai.api.service;

import com.enterprise.ai.common.dto.ScenarioRequest;
import com.enterprise.ai.common.dto.ScenarioResult;
import com.enterprise.ai.core.router.DynamicScenarioRouter;
import com.enterprise.ai.data.entity.AiScenario;
import com.enterprise.ai.data.entity.ScenarioTestResult;
import com.enterprise.ai.data.repository.ScenarioTestResultRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Scenario Sandbox Tester service.
 * Allows testing scenarios without affecting production data.
 * 
 * Features:
 * - Test any scenario with sample params
 * - See request built, executor chosen, response mapping
 * - Dry-run mode (no real backend calls)
 * - Save test results for debugging
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScenarioSandboxTesterService {

    private final DynamicScenarioRouter scenarioRouter;
    private final ScenarioTestResultRepository testResultRepository;
    private final ObjectMapper objectMapper;

    /**
     * Test a scenario with sample parameters.
     */
    public TestResult testScenario(TestRequest request) {
        String testId = UUID.randomUUID().toString();
        long startTime = System.currentTimeMillis();

        try {
            // Get scenario configuration
            AiScenario scenario = scenarioRouter.getScenario(request.getScenarioCode());
            
            // Build scenario request
            ScenarioRequest scenarioRequest = ScenarioRequest.builder()
                    .scenario(request.getScenarioCode())
                    .params(request.getSampleParams())
                    .userId("test-user")
                    .build();

            // Execute (dry-run or real)
            ScenarioResult result;
            if (request.isDryRun()) {
                result = scenarioRouter.route(scenarioRequest, true);
            } else {
                result = scenarioRouter.route(scenarioRequest, false);
            }

            long executionTime = System.currentTimeMillis() - startTime;

            // Build test result
            TestResult testResult = TestResult.builder()
                    .testId(testId)
                    .scenarioCode(request.getScenarioCode())
                    .executorType(scenario.getExecutionType())
                    .requestBuilt(buildRequestInfo(scenarioRequest, scenario))
                    .responseResult(result.getData())
                    .success(result.isSuccess())
                    .errorMessage(result.getErrorMessage())
                    .executionTimeMs(executionTime)
                    .dryRun(request.isDryRun())
                    .scenarioConfig(buildScenarioConfig(scenario))
                    .build();

            // Save test result
            saveTestResult(testResult);

            log.info("Scenario test {} completed in {}ms, success={}", testId, executionTime, result.isSuccess());
            return testResult;

        } catch (Exception e) {
            log.error("Scenario test failed: {}", e.getMessage());
            
            TestResult testResult = TestResult.builder()
                    .testId(testId)
                    .scenarioCode(request.getScenarioCode())
                    .success(false)
                    .errorMessage(e.getMessage())
                    .executionTimeMs(System.currentTimeMillis() - startTime)
                    .dryRun(request.isDryRun())
                    .build();

            saveTestResult(testResult);
            return testResult;
        }
    }

    /**
     * Get recent test results.
     */
    public List<ScenarioTestResult> getRecentTests() {
        return testResultRepository.findTop20ByOrderByCreatedAtDesc();
    }

    /**
     * Get test results for a specific scenario.
     */
    public List<ScenarioTestResult> getTestsForScenario(String scenarioCode) {
        return testResultRepository.findByScenarioCodeOrderByCreatedAtDesc(scenarioCode);
    }

    /**
     * Get a specific test result.
     */
    public ScenarioTestResult getTestResult(String testId) {
        return testResultRepository.findByTestId(testId).orElse(null);
    }

    private Map<String, Object> buildRequestInfo(ScenarioRequest request, AiScenario scenario) {
        return Map.of(
                "scenario", request.getScenario(),
                "params", request.getParams() != null ? request.getParams() : Map.of(),
                "executionType", scenario.getExecutionType() != null ? scenario.getExecutionType() : "DB_QUERY",
                "httpMethod", scenario.getHttpMethod() != null ? scenario.getHttpMethod() : "N/A",
                "httpUrl", scenario.getHttpUrl() != null ? scenario.getHttpUrl() : "N/A",
                "sqlQuery", scenario.getSqlQuery() != null ? scenario.getSqlQuery() : "N/A",
                "requestMapping", scenario.getRequestMapping() != null ? scenario.getRequestMapping() : "{}",
                "responseMapping", scenario.getResponseMapping() != null ? scenario.getResponseMapping() : "{}"
        );
    }

    private Map<String, Object> buildScenarioConfig(AiScenario scenario) {
        return Map.of(
                "scenarioCode", scenario.getScenarioCode(),
                "description", scenario.getDescription() != null ? scenario.getDescription() : "",
                "executionType", scenario.getExecutionType() != null ? scenario.getExecutionType() : "DB_QUERY",
                "active", Boolean.TRUE.equals(scenario.getActive()),
                "timeoutMs", scenario.getTimeoutMs() != null ? scenario.getTimeoutMs() : 5000,
                "requiredParams", scenario.getRequiredParams() != null ? scenario.getRequiredParams() : "[]"
        );
    }

    private void saveTestResult(TestResult result) {
        try {
            ScenarioTestResult entity = ScenarioTestResult.builder()
                    .testId(result.getTestId())
                    .scenarioCode(result.getScenarioCode())
                    .testParams(objectMapper.writeValueAsString(
                            result.getRequestBuilt() != null ? result.getRequestBuilt().get("params") : Map.of()))
                    .executorType(result.getExecutorType())
                    .requestBuilt(objectMapper.writeValueAsString(result.getRequestBuilt()))
                    .responseResult(objectMapper.writeValueAsString(result.getResponseResult()))
                    .success(result.isSuccess())
                    .errorMessage(result.getErrorMessage())
                    .executionTimeMs(result.getExecutionTimeMs())
                    .dryRun(result.isDryRun())
                    .build();

            testResultRepository.save(entity);
        } catch (Exception e) {
            log.error("Failed to save test result: {}", e.getMessage());
        }
    }

    @Data
    @Builder
    public static class TestRequest {
        private String scenarioCode;
        private Map<String, Object> sampleParams;
        private boolean dryRun;
    }

    @Data
    @Builder
    public static class TestResult {
        private String testId;
        private String scenarioCode;
        private String executorType;
        private Map<String, Object> requestBuilt;
        private Map<String, Object> responseResult;
        private boolean success;
        private String errorMessage;
        private long executionTimeMs;
        private boolean dryRun;
        private Map<String, Object> scenarioConfig;
    }
}
