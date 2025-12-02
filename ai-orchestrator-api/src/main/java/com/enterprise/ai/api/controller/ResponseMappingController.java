package com.enterprise.ai.api.controller;

import com.enterprise.ai.data.entity.AiResponseMapping;
import com.enterprise.ai.data.service.ResponseMappingAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST controller for managing AI Response Mappings.
 * Provides CRUD operations for JSONPath field mappings.
 *
 * Security: Only ADMIN role can access these endpoints
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/response-mappings")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Response Mappings", description = "Admin API for managing JSONPath response mappings")
public class ResponseMappingController {

    private final ResponseMappingAdminService service;

    @GetMapping
    @Operation(summary = "Get all response mappings", description = "Returns all JSONPath mappings, optionally filtered by scenario")
    public ResponseEntity<List<AiResponseMapping>> getAllMappings(
            @RequestParam(required = false) String scenarioCode) {
        log.info("Fetching response mappings - scenarioCode: {}", scenarioCode);

        if (scenarioCode != null && !scenarioCode.isEmpty()) {
            return ResponseEntity.ok(service.getMappingsByScenario(scenarioCode));
        }

        return ResponseEntity.ok(service.getAllMappings());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get mapping by ID", description = "Returns a single mapping by its ID")
    public ResponseEntity<AiResponseMapping> getMappingById(@PathVariable Long id) {
        log.info("Fetching response mapping: id={}", id);
        return service.getMappingById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/scenarios")
    @Operation(summary = "Get distinct scenario codes", description = "Returns list of scenarios that have mappings")
    public ResponseEntity<List<String>> getDistinctScenarios() {
        log.info("Fetching distinct scenario codes with mappings");
        return ResponseEntity.ok(service.getDistinctScenarioCodes());
    }

    @PostMapping
    @Operation(summary = "Create new mapping", description = "Creates a new JSONPath mapping")
    public ResponseEntity<AiResponseMapping> createMapping(@RequestBody ResponseMappingDTO dto) {
        log.info("Creating new response mapping: scenario={}, targetField={}",
                dto.scenarioCode, dto.targetField);

        try {
            AiResponseMapping mapping = AiResponseMapping.builder()
                    .scenarioCode(dto.scenarioCode)
                    .sourceType(dto.sourceType != null ? dto.sourceType : "DB_QUERY")
                    .sourceField(dto.sourceField)
                    .targetField(dto.targetField)
                    .jsonPath(dto.jsonPath)
                    .maskingType(dto.maskingType != null ? dto.maskingType : "NONE")
                    .displayOrder(dto.displayOrder != null ? dto.displayOrder : 0)
                    .active(dto.active != null ? dto.active : true)
                    .build();

            AiResponseMapping saved = service.saveMapping(mapping);
            return ResponseEntity.ok(saved);
        } catch (IllegalArgumentException e) {
            log.error("Invalid mapping data: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update mapping", description = "Updates an existing JSONPath mapping")
    public ResponseEntity<AiResponseMapping> updateMapping(
            @PathVariable Long id, @RequestBody ResponseMappingDTO dto) {
        log.info("Updating response mapping: id={}", id);

        return service.getMappingById(id)
                .map(mapping -> {
                    mapping.setScenarioCode(dto.scenarioCode);
                    mapping.setSourceType(dto.sourceType != null ? dto.sourceType : mapping.getSourceType());
                    mapping.setSourceField(dto.sourceField);
                    mapping.setTargetField(dto.targetField);
                    mapping.setJsonPath(dto.jsonPath);
                    mapping.setMaskingType(dto.maskingType != null ? dto.maskingType : mapping.getMaskingType());
                    mapping.setDisplayOrder(dto.displayOrder != null ? dto.displayOrder : mapping.getDisplayOrder());
                    mapping.setActive(dto.active != null ? dto.active : mapping.getActive());

                    try {
                        AiResponseMapping updated = service.saveMapping(mapping);
                        return ResponseEntity.ok(updated);
                    } catch (IllegalArgumentException e) {
                        log.error("Invalid JSONPath expression: {}", e.getMessage());
                        return ResponseEntity.badRequest().<AiResponseMapping>build();
                    }
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete mapping", description = "Deletes a JSONPath mapping")
    public ResponseEntity<Void> deleteMapping(@PathVariable Long id) {
        log.info("Deleting response mapping: id={}", id);

        if (service.getMappingById(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        service.deleteMapping(id);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Toggle mapping status", description = "Enables or disables a mapping")
    public ResponseEntity<AiResponseMapping> toggleMappingStatus(
            @PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        log.info("Toggling mapping status: id={}", id);

        Boolean active = body.get("active");
        if (active == null) {
            return ResponseEntity.badRequest().build();
        }

        try {
            AiResponseMapping updated = service.toggleMappingStatus(id, active);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            log.error("Mapping not found: id={}", id);
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/test")
    @Operation(summary = "Test JSONPath expression", description = "Validates a JSONPath expression against sample data")
    public ResponseEntity<Map<String, Object>> testJsonPath(@RequestBody JsonPathTestDTO dto) {
        log.info("Testing JSONPath expression: {}", dto.jsonPath);

        Map<String, Object> result = service.testJsonPath(dto.jsonPath, dto.sampleJson);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/cache/refresh")
    @Operation(summary = "Refresh cache", description = "Refreshes the response mappings cache")
    public ResponseEntity<Map<String, String>> refreshCache() {
        log.info("Refreshing response mappings cache");
        service.refreshCache();

        Map<String, String> response = new HashMap<>();
        response.put("message", "Response mappings cache refreshed successfully");
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/scenario/{scenarioCode}")
    @Operation(summary = "Delete all mappings for scenario", description = "Deletes all mappings associated with a scenario")
    public ResponseEntity<Map<String, String>> deleteMappingsByScenario(@PathVariable String scenarioCode) {
        log.info("Deleting all mappings for scenario: {}", scenarioCode);
        service.deleteMappingsByScenario(scenarioCode);

        Map<String, String> response = new HashMap<>();
        response.put("message", "All mappings deleted for scenario: " + scenarioCode);
        return ResponseEntity.ok(response);
    }

    // DTO Classes
    public static class ResponseMappingDTO {
        public String scenarioCode;
        public String sourceType;
        public String sourceField;
        public String targetField;
        public String jsonPath;
        public String maskingType;
        public Integer displayOrder;
        public Boolean active;
    }

    public static class JsonPathTestDTO {
        public String jsonPath;
        public String sampleJson;
    }
}

