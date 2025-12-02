package com.enterprise.ai.data.service;

import com.enterprise.ai.data.entity.AiResponseMapping;
import com.enterprise.ai.data.repository.AiResponseMappingRepository;
import com.jayway.jsonpath.JsonPath;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Admin service for managing AI Response Mappings.
 * Handles CRUD operations and JSONPath validation for admin panel.
 *
 * Note: Different from core.mapper.ResponseMappingService which handles runtime mapping.
 */
@Slf4j
@Service("responseMappingAdminService")
@RequiredArgsConstructor
public class ResponseMappingAdminService {

    private final AiResponseMappingRepository repository;

    /**
     * Get all response mappings (cached).
     */
    @Cacheable(value = "responseMappings", key = "'all'")
    public List<AiResponseMapping> getAllMappings() {
        log.debug("Fetching all response mappings from database");
        return repository.findAll();
    }

    /**
     * Get mappings by scenario code (cached).
     */
    @Cacheable(value = "responseMappings", key = "#scenarioCode")
    public List<AiResponseMapping> getMappingsByScenario(String scenarioCode) {
        log.debug("Fetching response mappings for scenario: {}", scenarioCode);
        return repository.findByScenarioCode(scenarioCode);
    }

    /**
     * Get active mappings by scenario code (cached).
     */
    @Cacheable(value = "responseMappings", key = "#scenarioCode + '_active'")
    public List<AiResponseMapping> getActiveMappingsByScenario(String scenarioCode) {
        log.debug("Fetching active response mappings for scenario: {}", scenarioCode);
        return repository.findByScenarioCodeAndActiveTrue(scenarioCode);
    }

    /**
     * Get mapping by ID.
     */
    public Optional<AiResponseMapping> getMappingById(Long id) {
        return repository.findById(id);
    }

    /**
     * Create or update mapping (evicts cache).
     */
    @Transactional
    @CacheEvict(value = "responseMappings", allEntries = true)
    public AiResponseMapping saveMapping(AiResponseMapping mapping) {
        log.info("Saving response mapping: id={}, scenario={}, targetField={}",
                mapping.getId(), mapping.getScenarioCode(), mapping.getTargetField());

        // Validate JSONPath expression before saving
        if (mapping.getJsonPath() != null && !mapping.getJsonPath().isEmpty()) {
            try {
                JsonPath.compile(mapping.getJsonPath());
                log.debug("JSONPath expression validated: {}", mapping.getJsonPath());
            } catch (Exception e) {
                log.error("Invalid JSONPath expression: {}", mapping.getJsonPath(), e);
                throw new IllegalArgumentException("Invalid JSONPath expression: " + e.getMessage());
            }
        }

        return repository.save(mapping);
    }

    /**
     * Delete mapping (evicts cache).
     */
    @Transactional
    @CacheEvict(value = "responseMappings", allEntries = true)
    public void deleteMapping(Long id) {
        log.info("Deleting response mapping: id={}", id);
        repository.deleteById(id);
    }

    /**
     * Toggle mapping active status (evicts cache).
     */
    @Transactional
    @CacheEvict(value = "responseMappings", allEntries = true)
    public AiResponseMapping toggleMappingStatus(Long id, boolean active) {
        log.info("Toggling response mapping status: id={}, active={}", id, active);
        return repository.findById(id)
                .map(mapping -> {
                    mapping.setActive(active);
                    return repository.save(mapping);
                })
                .orElseThrow(() -> new IllegalArgumentException("Mapping not found: " + id));
    }

    /**
     * Test JSONPath expression against sample data.
     */
    public Map<String, Object> testJsonPath(String jsonPathExpression, String sampleJson) {
        try {
            Object result = JsonPath.read(sampleJson, jsonPathExpression);
            log.debug("JSONPath test successful: {}", jsonPathExpression);
            return Map.of(
                    "success", true,
                    "result", result,
                    "expression", jsonPathExpression
            );
        } catch (Exception e) {
            log.error("JSONPath test failed: {}", jsonPathExpression, e);
            return Map.of(
                    "success", false,
                    "error", e.getMessage(),
                    "expression", jsonPathExpression
            );
        }
    }

    /**
     * Refresh cache manually.
     */
    @CacheEvict(value = "responseMappings", allEntries = true)
    public void refreshCache() {
        log.info("Response mappings cache refreshed");
    }

    /**
     * Get distinct scenario codes that have mappings.
     */
    public List<String> getDistinctScenarioCodes() {
        return repository.findDistinctScenarioCodes();
    }

    /**
     * Delete all mappings for a scenario.
     */
    @Transactional
    @CacheEvict(value = "responseMappings", allEntries = true)
    public void deleteMappingsByScenario(String scenarioCode) {
        log.info("Deleting all response mappings for scenario: {}", scenarioCode);
        repository.deleteByScenarioCode(scenarioCode);
    }
}

