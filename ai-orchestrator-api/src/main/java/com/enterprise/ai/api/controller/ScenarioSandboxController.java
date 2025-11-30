package com.enterprise.ai.api.controller;

import com.enterprise.ai.api.service.ScenarioSandboxTesterService;
import com.enterprise.ai.data.entity.ScenarioTestResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Scenario Sandbox Tester API.
 * Allows testing scenarios without affecting production data.
 * 
 * Endpoints:
 * - POST /api/v2/scenario/test - Test a scenario with sample params
 * - GET /api/v2/scenario/tests - Get recent test results
 * - GET /api/v2/scenario/tests/{scenarioCode} - Get tests for a specific scenario
 * - GET /api/v2/scenario/test/{testId} - Get a specific test result
 */
@Slf4j
@RestController
@RequestMapping("/api/v2/scenario")
@RequiredArgsConstructor
@Tag(name = "Scenario Sandbox Tester", description = "Test scenarios without affecting production")
public class ScenarioSandboxController {

    private final ScenarioSandboxTesterService sandboxService;

    /**
     * Test a scenario with sample parameters.
     * 
     * Example request:
     * {
     *   "scenarioCode": "TXN_STATUS",
     *   "sampleParams": {"txnId": "TXN123456"},
     *   "dryRun": true
     * }
     */
    @PostMapping("/test")
    @Operation(summary = "Test a scenario", 
               description = "Execute a scenario with sample params. Use dryRun=true to see what would happen without executing.")
    public ResponseEntity<ScenarioSandboxTesterService.TestResult> testScenario(
            @RequestBody TestScenarioRequest request) {
        log.info("Testing scenario {} with dryRun={}", request.scenarioCode(), request.dryRun());
        
        ScenarioSandboxTesterService.TestRequest testRequest = ScenarioSandboxTesterService.TestRequest.builder()
                .scenarioCode(request.scenarioCode())
                .sampleParams(request.sampleParams())
                .dryRun(request.dryRun())
                .build();
        
        ScenarioSandboxTesterService.TestResult result = sandboxService.testScenario(testRequest);
        return ResponseEntity.ok(result);
    }

    /**
     * Get recent test results.
     */
    @GetMapping("/tests")
    @Operation(summary = "Get recent tests", description = "Get the 20 most recent test results")
    public ResponseEntity<List<ScenarioTestResult>> getRecentTests() {
        return ResponseEntity.ok(sandboxService.getRecentTests());
    }

    /**
     * Get test results for a specific scenario.
     */
    @GetMapping("/tests/{scenarioCode}")
    @Operation(summary = "Get tests for scenario", description = "Get all test results for a specific scenario")
    public ResponseEntity<List<ScenarioTestResult>> getTestsForScenario(
            @PathVariable String scenarioCode) {
        return ResponseEntity.ok(sandboxService.getTestsForScenario(scenarioCode));
    }

    /**
     * Get a specific test result by ID.
     */
    @GetMapping("/test/{testId}")
    @Operation(summary = "Get test result", description = "Get a specific test result by test ID")
    public ResponseEntity<ScenarioTestResult> getTestResult(@PathVariable String testId) {
        ScenarioTestResult result = sandboxService.getTestResult(testId);
        if (result == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(result);
    }

    /**
     * Request body for test scenario endpoint.
     */
    public record TestScenarioRequest(
            String scenarioCode,
            Map<String, Object> sampleParams,
            boolean dryRun
    ) {}
}
