package com.enterprise.ai.core.mapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Example service demonstrating JsonPathResponseMapper usage.
 *
 * This shows various ways to use the mapper for DB result → AI request transformation.
 * Per ENTERPRISE_AI_RESPONSE_MAPPING_AND_SSE_SPEC.md requirements.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResponseMappingExampleService {

    private final ResponseMappingService responseMappingService;
    private final JsonPathResponseMapper jsonPathMapper;

    /**
     * Example 1: Basic single-row DB result mapping.
     *
     * Use Case: Transaction status check
     */
    public Map<String, Object> exampleSingleRowMapping() {
        // Simulate raw DB result
        Map<String, Object> dbResult = Map.of(
                "txn_id", "TXN123456",
                "status_code", "S",
                "amount", 5000.00,
                "account_number", "123456789012",
                "created_at", "2025-11-29T10:30:00"
        );

        // Map using ResponseMappingService (reads from ai_response_mappings table)
        String scenarioCode = "TXN_STATUS";
        Map<String, Object> aiReadyJson = responseMappingService.mapDbResultToAiRequest(scenarioCode, dbResult);

        log.info("Single row mapped result: {}", aiReadyJson);
        return aiReadyJson;
    }

    /**
     * Example 2: Multiple rows DB result mapping.
     *
     * Use Case: Recent transactions list
     */
    public Map<String, Object> exampleMultipleRowsMapping() {
        // Simulate raw DB result with multiple rows
        List<Map<String, Object>> dbResults = List.of(
                Map.of("txn_id", "TXN001", "amount", 1000, "account_number", "123456789012"),
                Map.of("txn_id", "TXN002", "amount", 2000, "account_number", "987654321098"),
                Map.of("txn_id", "TXN003", "amount", 3000, "account_number", "555555555555")
        );

        // Map using ResponseMappingService
        String scenarioCode = "RECENT_TRANSACTIONS";
        Map<String, Object> aiReadyJson = responseMappingService.mapDbResultToAiRequest(scenarioCode, dbResults);

        log.info("Multiple rows mapped result: {}", aiReadyJson);
        return aiReadyJson;
    }

    /**
     * Example 3: Building complete AI Formatter payload.
     *
     * This is the format that the AI Formatter expects.
     */
    public Map<String, Object> exampleBuildAiFormatterPayload() {
        // Simulate DB result
        Map<String, Object> dbResult = Map.of(
                "balance", 50000.00,
                "account_number", "123456789012",
                "account_type", "SAVINGS"
        );

        // Build complete payload
        String scenarioCode = "ACCOUNT_BALANCE";
        String userQuery = "What is my account balance?";

        Map<String, Object> formatterPayload = responseMappingService.buildAiFormatterPayload(
                scenarioCode,
                userQuery,
                dbResult
        );

        log.info("AI Formatter payload: {}", formatterPayload);
        return formatterPayload;
    }

    /**
     * Example 4: Direct JsonPathResponseMapper usage (programmatic configuration).
     *
     * Use this when you don't want to store mappings in database.
     */
    public Map<String, Object> exampleDirectMapperUsage() {
        // Simulate raw data
        Map<String, Object> rawData = Map.of(
                "customer_name", "John Doe",
                "pan_number", "ABCDE1234F",
                "account_balance", 100000.00,
                "account_no", "9876543210"
        );

        // Define mappings programmatically
        List<JsonPathResponseMapper.ResponseMappingConfig> mappings = List.of(
                JsonPathResponseMapper.ResponseMappingConfig.builder()
                        .sourceField("customer_name")
                        .targetField("customerName")
                        .jsonPath("$.customer_name")
                        .maskingType("NONE")
                        .build(),
                JsonPathResponseMapper.ResponseMappingConfig.builder()
                        .sourceField("pan_number")
                        .targetField("panNumber")
                        .jsonPath("$.pan_number")
                        .maskingType("PAN")
                        .build(),
                JsonPathResponseMapper.ResponseMappingConfig.builder()
                        .sourceField("account_balance")
                        .targetField("balance")
                        .jsonPath("$.account_balance")
                        .maskingType("NONE")
                        .build(),
                JsonPathResponseMapper.ResponseMappingConfig.builder()
                        .sourceField("account_no")
                        .targetField("accountNumber")
                        .jsonPath("$.account_no")
                        .maskingType("ACCOUNT")
                        .build()
        );

        // Apply mapping
        Map<String, Object> mapped = jsonPathMapper.mapResponse(rawData, mappings);

        log.info("Direct mapper result: {}", mapped);
        return mapped;
    }

    /**
     * Example 5: Nested object mapping.
     *
     * Use Case: Complex JSON structures from APIs
     */
    public Map<String, Object> exampleNestedMapping() {
        // Simulate nested JSON from API
        Map<String, Object> apiResponse = Map.of(
                "customer", Map.of(
                        "name", "Jane Doe",
                        "email", "jane@example.com"
                ),
                "account", Map.of(
                        "number", "123456789012",
                        "balance", 75000.00
                )
        );

        // Define mappings for nested structure
        List<JsonPathResponseMapper.ResponseMappingConfig> mappings = List.of(
                JsonPathResponseMapper.ResponseMappingConfig.builder()
                        .sourceField("customer.name")
                        .targetField("customerName")
                        .jsonPath("$.customer.name")
                        .maskingType("NONE")
                        .build(),
                JsonPathResponseMapper.ResponseMappingConfig.builder()
                        .sourceField("customer.email")
                        .targetField("email")
                        .jsonPath("$.customer.email")
                        .maskingType("EMAIL")
                        .build(),
                JsonPathResponseMapper.ResponseMappingConfig.builder()
                        .sourceField("account.number")
                        .targetField("accountNumber")
                        .jsonPath("$.account.number")
                        .maskingType("ACCOUNT")
                        .build(),
                JsonPathResponseMapper.ResponseMappingConfig.builder()
                        .sourceField("account.balance")
                        .targetField("balance")
                        .jsonPath("$.account.balance")
                        .maskingType("NONE")
                        .build()
        );

        // Apply mapping
        Map<String, Object> mapped = jsonPathMapper.mapResponse(apiResponse, mappings);

        log.info("Nested mapping result: {}", mapped);
        return mapped;
    }

    /**
     * Example 6: Demonstrating all masking types.
     */
    public Map<String, Object> exampleMaskingTypes() {
        // Simulate sensitive data
        Map<String, Object> sensitiveData = Map.of(
                "account_number", "123456789012",
                "pan_card", "ABCDE1234F",
                "aadhaar_number", "123456789012",
                "card_number", "1234567890123456",
                "email", "user@example.com",
                "phone", "9876543210"
        );

        // Define mappings with different masking types
        List<JsonPathResponseMapper.ResponseMappingConfig> mappings = List.of(
                JsonPathResponseMapper.ResponseMappingConfig.builder()
                        .sourceField("account_number")
                        .targetField("accountNumber")
                        .jsonPath("$.account_number")
                        .maskingType("ACCOUNT")
                        .build(),
                JsonPathResponseMapper.ResponseMappingConfig.builder()
                        .sourceField("pan_card")
                        .targetField("panCard")
                        .jsonPath("$.pan_card")
                        .maskingType("PAN")
                        .build(),
                JsonPathResponseMapper.ResponseMappingConfig.builder()
                        .sourceField("aadhaar_number")
                        .targetField("aadhaarNumber")
                        .jsonPath("$.aadhaar_number")
                        .maskingType("AADHAAR")
                        .build(),
                JsonPathResponseMapper.ResponseMappingConfig.builder()
                        .sourceField("card_number")
                        .targetField("cardNumber")
                        .jsonPath("$.card_number")
                        .maskingType("CARD")
                        .build(),
                JsonPathResponseMapper.ResponseMappingConfig.builder()
                        .sourceField("email")
                        .targetField("email")
                        .jsonPath("$.email")
                        .maskingType("EMAIL")
                        .build(),
                JsonPathResponseMapper.ResponseMappingConfig.builder()
                        .sourceField("phone")
                        .targetField("phone")
                        .jsonPath("$.phone")
                        .maskingType("PHONE")
                        .build()
        );

        // Apply mapping
        Map<String, Object> masked = jsonPathMapper.mapResponse(sensitiveData, mappings);

        log.info("Masking types result: {}", masked);

        // Expected output:
        // {
        //   "accountNumber": "XXXX-XXXX-9012",
        //   "panCard": "AB******F",
        //   "aadhaarNumber": "XXXX-XXXX-9012",
        //   "cardNumber": "XXXX-XXXX-XXXX-3456",
        //   "email": "u***@example.com",
        //   "phone": "XXXXX43210"
        // }

        return masked;
    }

    /**
     * Example 7: Handling missing fields gracefully.
     */
    public Map<String, Object> exampleMissingFields() {
        // Simulate DB result with missing optional fields
        Map<String, Object> dbResult = Map.of(
                "txn_id", "TXN123",
                "amount", 1000
                // Note: status_code, account_number are missing
        );

        // Define mappings including missing fields
        List<JsonPathResponseMapper.ResponseMappingConfig> mappings = List.of(
                JsonPathResponseMapper.ResponseMappingConfig.builder()
                        .sourceField("txn_id")
                        .targetField("txnId")
                        .jsonPath("$.txn_id")
                        .maskingType("NONE")
                        .build(),
                JsonPathResponseMapper.ResponseMappingConfig.builder()
                        .sourceField("status_code")
                        .targetField("status")
                        .jsonPath("$.status_code")
                        .maskingType("NONE")
                        .build(),
                JsonPathResponseMapper.ResponseMappingConfig.builder()
                        .sourceField("amount")
                        .targetField("amount")
                        .jsonPath("$.amount")
                        .maskingType("NONE")
                        .build(),
                JsonPathResponseMapper.ResponseMappingConfig.builder()
                        .sourceField("account_number")
                        .targetField("accountNumber")
                        .jsonPath("$.account_number")
                        .maskingType("ACCOUNT")
                        .build()
        );

        // Apply mapping - missing fields will be set to null
        Map<String, Object> mapped = jsonPathMapper.mapResponse(dbResult, mappings);

        log.info("Missing fields result: {}", mapped);

        // Expected output:
        // {
        //   "txnId": "TXN123",
        //   "status": null,
        //   "amount": 1000,
        //   "accountNumber": null
        // }

        return mapped;
    }
}

