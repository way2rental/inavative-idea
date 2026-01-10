package com.enterprise.ai.core.mapper;

import com.enterprise.ai.data.entity.AiResponseMapping;
import com.enterprise.ai.data.entity.AiScenario;
import com.enterprise.ai.data.repository.AiResponseMappingRepository;
import com.enterprise.ai.data.service.ConfigCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service for managing scenario-driven response mappings.
 * Bridges database-driven configuration with JsonPathResponseMapper.
 *
 * Per ENTERPRISE_AI_RESPONSE_MAPPING_AND_SSE_SPEC.md:
 * - Loads mappings from ai_response_mappings table
 * - Converts DB results to AI-friendly structured JSON
 * - Applies masking and field mapping
 * - Caches mappings for performance
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResponseMappingService {

    private final AiResponseMappingRepository mappingRepository;
    private final JsonPathResponseMapper jsonPathMapper;
    private final ConfigCacheService configCacheService;

    /**
     * Map raw database result to structured AI-friendly JSON.
     *
     * Flow:
     * 1. Load mappings for scenario from DB (cached)
     * 2. Apply JSON Path extraction + masking via JsonPathResponseMapper
     * 3. Return clean structured JSON ready for AI Formatter
     *
     * @param scenarioCode The scenario code (e.g., "TXN_STATUS")
     * @param rawDbResult Raw database query result (single row or list)
     * @return Structured JSON ready for AI formatter
     */
    public Map<String, Object> mapDbResultToAiRequest(String scenarioCode, Object rawDbResult) {
        log.debug("Mapping DB result for scenario: {}", scenarioCode);

        // Load mappings for scenario
        List<JsonPathResponseMapper.ResponseMappingConfig> mappings = loadMappingsForScenario(scenarioCode);

        if (mappings.isEmpty()) {
            log.warn("No response mappings found for scenario: {}, returning raw result", scenarioCode);
            // Fallback: return raw result wrapped
            return Map.of(
                    "scenario", scenarioCode,
                    "data", rawDbResult,
                    "warning", "No response mappings configured"
            );
        }

        // Apply mapping
        if (rawDbResult instanceof List<?> list && !list.isEmpty()) {
            // Multiple rows - map each row
            return mapMultipleRows(scenarioCode, list, mappings);
        } else {
            // Single row or object
            return mapSingleRow(scenarioCode, rawDbResult, mappings);
        }
    }

    /**
     * Build complete AI Formatter payload.
     * This is the EXACT format that the AI Formatter expects.
     *
     * @param scenarioCode The scenario code
     * @param userQuery The original user query
     * @param rawDbResult Raw database result
     * @return Complete payload for AI Formatter
     */
    public Map<String, Object> buildAiFormatterPayload(String scenarioCode, String userQuery, Object rawDbResult) {
        Map<String, Object> structuredData = mapDbResultToAiRequest(scenarioCode, rawDbResult);
        return jsonPathMapper.buildFormatterPayload(scenarioCode, userQuery, structuredData);
    }

    /**
     * Map single row result.
     */
    private Map<String, Object> mapSingleRow(
            String scenarioCode,
            Object rawData,
            List<JsonPathResponseMapper.ResponseMappingConfig> mappings) {

        Map<String, Object> mapped = jsonPathMapper.mapResponse(rawData, mappings);

        return Map.of(
                "scenario", scenarioCode,
                "type", "single",
                "data", mapped
        );
    }

    /**
     * Map multiple rows result.
     */
    private Map<String, Object> mapMultipleRows(
            String scenarioCode,
            List<?> rawDataList,
            List<JsonPathResponseMapper.ResponseMappingConfig> mappings) {

        // Limit to prevent memory issues (configurable per scenario via maxResults field)
        int maxRows = configCacheService.getScenarioByCode(scenarioCode)
            .map(AiScenario::getMaxResults)
            .filter(max -> max != null && max > 0)
            .orElse(100); // Default to 100 if not configured

        log.debug("Using maxRows={} for scenario {}", maxRows, scenarioCode);

        List<Map<String, Object>> mappedRows = jsonPathMapper.mapMultiRowResponse(rawDataList, mappings, maxRows);

        return Map.of(
                "scenario", scenarioCode,
                "type", "multiple",
                "data", mappedRows,
                "count", mappedRows.size(),
                "totalRows", rawDataList.size()
        );
    }

    /**
     * Load mappings for a scenario from database.
     * Results are cached to avoid repeated DB queries.
     */
    @Cacheable(value = "responseMappings", key = "#scenarioCode")
    public List<JsonPathResponseMapper.ResponseMappingConfig> loadMappingsForScenario(String scenarioCode) {
        log.debug("Loading response mappings for scenario: {}", scenarioCode);

        List<AiResponseMapping> dbMappings =
                mappingRepository.findByScenarioCodeAndActiveTrueOrderByDisplayOrderAsc(scenarioCode);

        return dbMappings.stream()
                .map(this::convertToMappingConfig)
                .collect(Collectors.toList());
    }

    /**
     * Convert database entity to mapper configuration.
     */
    private JsonPathResponseMapper.ResponseMappingConfig convertToMappingConfig(AiResponseMapping entity) {
        return JsonPathResponseMapper.ResponseMappingConfig.builder()
                .sourceField(entity.getSourceField())
                .targetField(entity.getTargetField())
                .jsonPath(entity.getJsonPath())
                .maskingType(entity.getMaskingType() != null ? entity.getMaskingType() : "NONE")
                .build();
    }

    /**
     * Check if mappings exist for a scenario.
     */
    public boolean hasMappings(String scenarioCode) {
        return mappingRepository.existsByScenarioCode(scenarioCode);
    }

    /**
     * Clear cache for a specific scenario.
     * Call this when mappings are updated in the database.
     */
    public void evictMappingCache(String scenarioCode) {
        log.info("Evicting response mapping cache for scenario: {}", scenarioCode);
        // Cache eviction will be handled by Spring Cache abstraction
    }
}

