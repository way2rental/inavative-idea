package com.enterprise.ai.core.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * JSON Path-based Response Mapper.
 * Converts raw DB/API results into AI-friendly structured JSON.
 * 
 * Per ENTERPRISE_AI_RESPONSE_MAPPING_AND_SSE_SPEC.md:
 * - AI MUST NEVER receive raw SQL rows or API payloads
 * - AI MUST ALWAYS receive clean, structured, masked JSON
 * - Fields not in mapping are discarded
 * - If mapped field is missing, null is passed
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JsonPathResponseMapper {

    private final ObjectMapper objectMapper;
    private final MaskingService maskingService;

    private static final Configuration JSON_PATH_CONFIG = Configuration.builder()
            .options(Option.DEFAULT_PATH_LEAF_TO_NULL)
            .options(Option.SUPPRESS_EXCEPTIONS)
            .build();

    /**
     * Map raw response to structured AI-friendly JSON.
     * 
     * @param rawData The raw DB result or API response
     * @param mappings List of field mappings for this scenario
     * @return Structured JSON ready for AI formatter
     */
    public Map<String, Object> mapResponse(Object rawData, List<ResponseMappingConfig> mappings) {
        try {
            String jsonString = rawData instanceof String 
                    ? (String) rawData 
                    : objectMapper.writeValueAsString(rawData);

            Object document = Configuration.defaultConfiguration().jsonProvider().parse(jsonString);
            ObjectNode result = objectMapper.createObjectNode();

            for (ResponseMappingConfig mapping : mappings) {
                Object extractedValue = extractValue(document, mapping.getJsonPath());
                Object maskedValue = maskingService.mask(extractedValue, mapping.getMaskingType());
                setNestedValue(result, mapping.getTargetField(), maskedValue);
            }

            return objectMapper.convertValue(result, Map.class);
        } catch (Exception e) {
            log.error("Error mapping response: {}", e.getMessage(), e);
            return Map.of("error", "Failed to process response");
        }
    }

    /**
     * Map multiple rows (e.g., from DB query returning list).
     * Applies mapping to each row and returns array.
     */
    public List<Map<String, Object>> mapMultiRowResponse(List<?> rawDataList, List<ResponseMappingConfig> mappings, int maxRows) {
        int limit = Math.min(rawDataList.size(), maxRows);
        return rawDataList.stream()
                .limit(limit)
                .map(row -> mapResponse(row, mappings))
                .toList();
    }

    /**
     * Extract value using JSON Path expression.
     */
    private Object extractValue(Object document, String jsonPath) {
        try {
            return JsonPath.using(JSON_PATH_CONFIG).parse(document).read(jsonPath);
        } catch (Exception e) {
            log.debug("Failed to extract path {}: {}", jsonPath, e.getMessage());
            return null;
        }
    }

    /**
     * Set value in result object, supporting nested paths like "account.balance".
     */
    private void setNestedValue(ObjectNode result, String path, Object value) {
        String[] parts = path.split("\\.");
        ObjectNode current = result;

        for (int i = 0; i < parts.length - 1; i++) {
            String part = parts[i];
            if (!current.has(part)) {
                current.set(part, objectMapper.createObjectNode());
            }
            current = (ObjectNode) current.get(part);
        }

        String finalKey = parts[parts.length - 1];
        if (value == null) {
            current.putNull(finalKey);
        } else if (value instanceof Number) {
            current.put(finalKey, ((Number) value).doubleValue());
        } else if (value instanceof Boolean) {
            current.put(finalKey, (Boolean) value);
        } else {
            current.put(finalKey, value.toString());
        }
    }

    /**
     * Build structured AI request payload.
     * Per spec: Formatter ALWAYS receives scenario, userQuery, and structuredData.
     */
    public Map<String, Object> buildFormatterPayload(String scenario, String userQuery, Map<String, Object> structuredData) {
        return Map.of(
                "scenario", scenario,
                "userQuery", userQuery,
                "structuredData", structuredData
        );
    }

    /**
     * Configuration for a single field mapping.
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ResponseMappingConfig {
        private String sourceField;     // Raw DB column or JSON API path
        private String targetField;     // Final structured JSON field name
        private String jsonPath;        // JSON Path expression
        private String maskingType;     // NONE, ACCOUNT, PAN, AADHAAR, CARD
    }
}
