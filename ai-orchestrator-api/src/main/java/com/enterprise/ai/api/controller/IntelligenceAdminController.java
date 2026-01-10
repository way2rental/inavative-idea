package com.enterprise.ai.api.controller;

import com.enterprise.ai.data.entity.FallbackLayer;
import com.enterprise.ai.data.entity.RuleEngineRule;
import com.enterprise.ai.data.entity.KeywordPattern;
import com.enterprise.ai.data.entity.ScenarioEmbedding;
import com.enterprise.ai.data.repository.FallbackLayerRepository;
import com.enterprise.ai.data.repository.RuleEngineRuleRepository;
import com.enterprise.ai.data.repository.KeywordPatternRepository;
import com.enterprise.ai.data.repository.ScenarioEmbeddingRepository;
import com.enterprise.ai.intelligence.service.embedding.EmbeddingGenerationService;
import com.enterprise.ai.intelligence.service.FallbackLayerOrchestrator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for Intelligence System admin operations.
 * Manages fallback layers, rules, keywords, and embeddings.
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/intelligence")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Intelligence Admin", description = "Admin API for managing intelligence system configuration")
public class IntelligenceAdminController {

    private final FallbackLayerRepository layerRepository;
    private final RuleEngineRuleRepository ruleRepository;
    private final KeywordPatternRepository keywordRepository;
    private final ScenarioEmbeddingRepository embeddingRepository;
    private final EmbeddingGenerationService embeddingGenerationService;
    private final FallbackLayerOrchestrator orchestrator;

    // ===================== FALLBACK LAYERS =====================

    @GetMapping("/layers")
    @Operation(summary = "Get all fallback layers")
    public ResponseEntity<List<FallbackLayer>> getLayers() {
        log.info("Admin requested fallback layers");
        return ResponseEntity.ok(layerRepository.findAll());
    }

    @PutMapping("/layers/{id}")
    @Operation(summary = "Update fallback layer")
    public ResponseEntity<FallbackLayer> updateLayer(
            @PathVariable Long id,
            @RequestBody FallbackLayerUpdateRequest request) {
        log.info("Admin updating fallback layer: {}", id);
        
        return layerRepository.findById(id)
            .map(layer -> {
                if (request.getConfidenceThreshold() != null) {
                    layer.setConfidenceThreshold(request.getConfidenceThreshold());
                }
                if (request.getPriority() != null) {
                    layer.setPriority(request.getPriority());
                }
                if (request.getActive() != null) {
                    layer.setEnabled(request.getActive());
                }
                if (request.getConfigJson() != null) {
                    layer.setConfigJson(request.getConfigJson());
                }
                
                FallbackLayer saved = layerRepository.save(layer);
                orchestrator.refreshLayersFromDb();
                return ResponseEntity.ok(saved);
            })
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/layers/refresh")
    @Operation(summary = "Refresh fallback layers from database")
    public ResponseEntity<Map<String, String>> refreshLayers() {
        log.info("Admin refreshing fallback layers");
        orchestrator.refreshLayersFromDb();
        return ResponseEntity.ok(Map.of("message", "Fallback layers refreshed successfully"));
    }

    // ===================== RULES =====================

    @GetMapping("/rules")
    @Operation(summary = "Get all rules")
    public ResponseEntity<List<RuleEngineRule>> getRules(
            @RequestParam(required = false) String scenarioCode) {
        log.info("Admin requested rules, scenarioCode: {}", scenarioCode);
        
        List<RuleEngineRule> rules = scenarioCode != null
            ? ruleRepository.findByScenarioCode(scenarioCode)
            : ruleRepository.findAll();
        
        return ResponseEntity.ok(rules);
    }

    @PostMapping("/rules")
    @Operation(summary = "Create rule")
    public ResponseEntity<RuleEngineRule> createRule(@RequestBody RuleEngineRule rule) {
        log.info("Admin creating rule: {}", rule.getRuleCode());
        return ResponseEntity.ok(ruleRepository.save(rule));
    }

    @PutMapping("/rules/{id}")
    @Operation(summary = "Update rule")
    public ResponseEntity<RuleEngineRule> updateRule(
            @PathVariable Long id,
            @RequestBody RuleEngineRule rule) {
        log.info("Admin updating rule: {}", id);
        
        return ruleRepository.findById(id)
            .map(existing -> {
                rule.setId(id);
                return ResponseEntity.ok(ruleRepository.save(rule));
            })
            .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/rules/{id}")
    @Operation(summary = "Delete rule")
    public ResponseEntity<Void> deleteRule(@PathVariable Long id) {
        log.info("Admin deleting rule: {}", id);
        ruleRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // ===================== KEYWORDS =====================

    @GetMapping("/keywords")
    @Operation(summary = "Get keywords")
    public ResponseEntity<List<KeywordPattern>> getKeywords(
            @RequestParam(required = false) String scenarioCode) {
        log.info("Admin requested keywords, scenarioCode: {}", scenarioCode);
        
        List<KeywordPattern> keywords = scenarioCode != null
            ? keywordRepository.findByScenarioCode(scenarioCode)
            : keywordRepository.findAll();
        
        return ResponseEntity.ok(keywords);
    }

    @PostMapping("/keywords")
    @Operation(summary = "Create keyword")
    public ResponseEntity<KeywordPattern> createKeyword(@RequestBody KeywordPattern keyword) {
        log.info("Admin creating keyword: {}", keyword.getKeyword());
        return ResponseEntity.ok(keywordRepository.save(keyword));
    }

    @PutMapping("/keywords/{id}")
    @Operation(summary = "Update keyword")
    public ResponseEntity<KeywordPattern> updateKeyword(
            @PathVariable Long id,
            @RequestBody KeywordPattern keyword) {
        log.info("Admin updating keyword: {}", id);
        
        return keywordRepository.findById(id)
            .map(existing -> {
                keyword.setId(id);
                return ResponseEntity.ok(keywordRepository.save(keyword));
            })
            .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/keywords/{id}")
    @Operation(summary = "Delete keyword")
    public ResponseEntity<Void> deleteKeyword(@PathVariable Long id) {
        log.info("Admin deleting keyword: {}", id);
        keywordRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // ===================== EMBEDDINGS =====================

    @GetMapping("/embeddings")
    @Operation(summary = "Get all embeddings")
    public ResponseEntity<List<ScenarioEmbedding>> getEmbeddings() {
        log.info("Admin requested embeddings");
        return ResponseEntity.ok(embeddingRepository.findAll());
    }

    @PostMapping("/embeddings/{scenarioCode}")
    @Operation(summary = "Generate embedding for scenario")
    public ResponseEntity<ScenarioEmbedding> generateEmbedding(@PathVariable String scenarioCode) {
        log.info("Admin generating embedding for scenario: {}", scenarioCode);
        return embeddingGenerationService.generateEmbedding(scenarioCode)
            .map(ResponseEntity::ok)
            .block(); // Block for REST endpoint
    }

    @PostMapping("/embeddings/generate-all")
    @Operation(summary = "Generate embeddings for all scenarios")
    public ResponseEntity<Map<String, Object>> generateAllEmbeddings() {
        log.info("Admin generating embeddings for all scenarios");
        Integer count = embeddingGenerationService.generateAllEmbeddings().block();
        return ResponseEntity.ok(Map.of(
            "message", "Embeddings generated successfully",
            "count", count
        ));
    }

    @PostMapping("/embeddings/{scenarioCode}/refresh")
    @Operation(summary = "Refresh embedding for scenario")
    public ResponseEntity<ScenarioEmbedding> refreshEmbedding(@PathVariable String scenarioCode) {
        log.info("Admin refreshing embedding for scenario: {}", scenarioCode);
        return embeddingGenerationService.refreshEmbedding(scenarioCode)
            .map(ResponseEntity::ok)
            .block(); // Block for REST endpoint
    }

    // ===================== DTOs =====================

    @Data
    public static class FallbackLayerUpdateRequest {
        private Double confidenceThreshold;
        private Integer priority;
        private Boolean active;
        private String configJson;
    }
}
