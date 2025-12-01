package com.enterprise.ai.api.controller;

import com.enterprise.ai.core.mapper.JsonPathResponseMapper;
import com.enterprise.ai.core.mapper.ResponseMappingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Demo controller to test JsonPathResponseMapper integration.
 * Useful for testing and demonstrating the mapping functionality.
 *
 * Endpoints:
 * - POST /api/demo/map-single - Test single row mapping
 * - POST /api/demo/map-multiple - Test multiple rows mapping
 * - GET /api/demo/mappings/{scenarioCode} - View mappings for scenario
 * - POST /api/demo/test-masking - Test masking functionality
 */
@Slf4j
@RestController
@RequestMapping("/api/demo")
@RequiredArgsConstructor
public class ResponseMappingDemoController {

    private final ResponseMappingService responseMappingService;
    private final JsonPathResponseMapper jsonPathResponseMapper;

    /**
     * Test single row mapping.
     *
     * Example request:
     * POST /api/demo/map-single
     * {
     *   "scenarioCode": "TXN_STATUS",
     *   "rawData": {
     *     "txn_id": "TXN123",
     *     "status_code": "S",
     *     "amount": 5000,
     *     "account_number": "123456789012"
     *   }
     * }
     */
    @PostMapping("/map-single")
    public ResponseEntity<Map<String, Object>> testSingleRowMapping(@RequestBody TestMappingRequest request) {
        log.info("Testing single row mapping for scenario: {}", request.getScenarioCode());

        try {
            Map<String, Object> result = responseMappingService.mapDbResultToAiRequest(
                    request.getScenarioCode(),
                    request.getRawData()
            );

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "scenarioCode", request.getScenarioCode(),
                    "input", request.getRawData(),
                    "output", result
            ));
        } catch (Exception e) {
            log.error("Mapping failed: {}", e.getMessage(), e);
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * Test multiple rows mapping.
     *
     * Example request:
     * POST /api/demo/map-multiple
     * {
     *   "scenarioCode": "RECENT_TRANSACTIONS",
     *   "rawDataList": [
     *     {"txn_id": "TXN001", "amount": 1000},
     *     {"txn_id": "TXN002", "amount": 2000}
     *   ]
     * }
     */
    @PostMapping("/map-multiple")
    public ResponseEntity<Map<String, Object>> testMultipleRowsMapping(@RequestBody TestMultipleMappingRequest request) {
        log.info("Testing multiple rows mapping for scenario: {}", request.getScenarioCode());

        try {
            Map<String, Object> result = responseMappingService.mapDbResultToAiRequest(
                    request.getScenarioCode(),
                    request.getRawDataList()
            );

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "scenarioCode", request.getScenarioCode(),
                    "inputCount", request.getRawDataList().size(),
                    "output", result
            ));
        } catch (Exception e) {
            log.error("Mapping failed: {}", e.getMessage(), e);
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * View configured mappings for a scenario.
     *
     * GET /api/demo/mappings/TXN_STATUS
     */
    @GetMapping("/mappings/{scenarioCode}")
    public ResponseEntity<Map<String, Object>> viewMappings(@PathVariable String scenarioCode) {
        log.info("Viewing mappings for scenario: {}", scenarioCode);

        try {
            List<JsonPathResponseMapper.ResponseMappingConfig> mappings =
                    responseMappingService.loadMappingsForScenario(scenarioCode);

            boolean hasMappings = responseMappingService.hasMappings(scenarioCode);

            return ResponseEntity.ok(Map.of(
                    "scenarioCode", scenarioCode,
                    "hasMappings", hasMappings,
                    "mappingCount", mappings.size(),
                    "mappings", mappings
            ));
        } catch (Exception e) {
            log.error("Failed to load mappings: {}", e.getMessage(), e);
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * Test masking functionality.
     *
     * Example request:
     * POST /api/demo/test-masking
     * {
     *   "testData": {
     *     "account": "123456789012",
     *     "pan": "ABCDE1234F",
     *     "card": "1234567890123456",
     *     "email": "user@example.com",
     *     "phone": "9876543210"
     *   }
     * }
     */
    @PostMapping("/test-masking")
    public ResponseEntity<Map<String, Object>> testMasking(@RequestBody Map<String, Object> request) {
        log.info("Testing masking functionality");

        Map<String, Object> testData = (Map<String, Object>) request.get("testData");

        // Create test mappings with different masking types
        List<JsonPathResponseMapper.ResponseMappingConfig> mappings = List.of(
                JsonPathResponseMapper.ResponseMappingConfig.builder()
                        .jsonPath("$.account")
                        .targetField("maskedAccount")
                        .maskingType("ACCOUNT")
                        .build(),
                JsonPathResponseMapper.ResponseMappingConfig.builder()
                        .jsonPath("$.pan")
                        .targetField("maskedPan")
                        .maskingType("PAN")
                        .build(),
                JsonPathResponseMapper.ResponseMappingConfig.builder()
                        .jsonPath("$.card")
                        .targetField("maskedCard")
                        .maskingType("CARD")
                        .build(),
                JsonPathResponseMapper.ResponseMappingConfig.builder()
                        .jsonPath("$.email")
                        .targetField("maskedEmail")
                        .maskingType("EMAIL")
                        .build(),
                JsonPathResponseMapper.ResponseMappingConfig.builder()
                        .jsonPath("$.phone")
                        .targetField("maskedPhone")
                        .maskingType("PHONE")
                        .build()
        );

        Map<String, Object> result = jsonPathResponseMapper.mapResponse(testData, mappings);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "input", testData,
                "output", result
        ));
    }

    /**
     * Test programmatic mapping (without database configuration).
     *
     * Example request:
     * POST /api/demo/map-custom
     * {
     *   "rawData": {"field1": "value1", "field2": "value2"},
     *   "mappings": [
     *     {
     *       "jsonPath": "$.field1",
     *       "targetField": "output1",
     *       "maskingType": "NONE"
     *     }
     *   ]
     * }
     */
    @PostMapping("/map-custom")
    public ResponseEntity<Map<String, Object>> testCustomMapping(@RequestBody CustomMappingRequest request) {
        log.info("Testing custom programmatic mapping");

        try {
            Map<String, Object> result = jsonPathResponseMapper.mapResponse(
                    request.getRawData(),
                    request.getMappings()
            );

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "input", request.getRawData(),
                    "output", result
            ));
        } catch (Exception e) {
            log.error("Custom mapping failed: {}", e.getMessage(), e);
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }

    // Request DTOs

    @lombok.Data
    public static class TestMappingRequest {
        private String scenarioCode;
        private Map<String, Object> rawData;
    }

    @lombok.Data
    public static class TestMultipleMappingRequest {
        private String scenarioCode;
        private List<Map<String, Object>> rawDataList;
    }

    @lombok.Data
    public static class CustomMappingRequest {
        private Map<String, Object> rawData;
        private List<JsonPathResponseMapper.ResponseMappingConfig> mappings;
    }
}

