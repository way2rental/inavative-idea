package com.enterprise.ai.api.controller;

import com.enterprise.ai.data.entity.IntentConfig;
import com.enterprise.ai.data.repository.IntentConfigRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

/**
 * Admin controller for intent configuration management.
 * Supports dynamic intent detection without code changes.
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/intents")
@RequiredArgsConstructor
@Tag(name = "Intent Admin", description = "Intent configuration management")
@PreAuthorize("hasRole('ADMIN')")
public class IntentAdminController {

    private final IntentConfigRepository intentRepository;
    private final ObjectMapper objectMapper;

    // ===================== LIST =====================

    @GetMapping
    @Operation(summary = "Get all intent configurations")
    public ResponseEntity<List<IntentDTO>> getAllIntents() {
        log.info("Fetching all intent configurations");
        List<IntentConfig> intents = intentRepository.findAll();
        List<IntentDTO> dtos = intents.stream()
                .map(this::toDTO)
                .toList();
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/active")
    @Operation(summary = "Get all active intents")
    public ResponseEntity<List<IntentDTO>> getActiveIntents() {
        log.info("Fetching active intents");
        List<IntentConfig> intents = intentRepository.findByActiveTrue();
        List<IntentDTO> dtos = intents.stream()
                .map(this::toDTO)
                .toList();
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/category/{category}")
    @Operation(summary = "Get intents by category")
    public ResponseEntity<List<IntentDTO>> getIntentsByCategory(@PathVariable String category) {
        log.info("Fetching intents for category: {}", category);
        List<IntentConfig> intents = intentRepository.findByCategory(category);
        List<IntentDTO> dtos = intents.stream()
                .map(this::toDTO)
                .toList();
        return ResponseEntity.ok(dtos);
    }

    // ===================== CRUD =====================

    @GetMapping("/{id}")
    @Operation(summary = "Get intent by ID")
    public ResponseEntity<IntentDTO> getIntentById(@PathVariable Long id) {
        log.info("Fetching intent with id: {}", id);
        return intentRepository.findById(id)
                .map(this::toDTO)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/key/{intentKey}")
    @Operation(summary = "Get intent by key")
    public ResponseEntity<IntentDTO> getIntentByKey(@PathVariable String intentKey) {
        log.info("Fetching intent with key: {}", intentKey);
        return intentRepository.findByIntentKey(intentKey)
                .map(this::toDTO)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @Operation(summary = "Create new intent configuration")
    public ResponseEntity<IntentDTO> createIntent(@RequestBody IntentFormDTO form) {
        log.info("Creating new intent: {}", form.intentKey);
        
        if (intentRepository.existsByIntentKey(form.intentKey)) {
            return ResponseEntity.badRequest().build();
        }

        IntentConfig intent = IntentConfig.builder()
                .intentKey(form.intentKey)
                .intentName(form.intentName)
                .description(form.description)
                .trainingPhrases(toJsonArray(form.trainingPhrases))
                .confidenceThreshold(form.confidenceThreshold != null ? form.confidenceThreshold : 0.72)
                .followupGroup(form.followupGroup)
                .scenarioCode(form.scenarioCode)
                .category(form.category)
                .priority(form.priority != null ? form.priority : 0)
                .active(form.active != null ? form.active : true)
                .build();

        IntentConfig saved = intentRepository.save(intent);
        log.info("Intent created: {}", saved.getIntentKey());
        return ResponseEntity.ok(toDTO(saved));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update intent configuration")
    public ResponseEntity<IntentDTO> updateIntent(@PathVariable Long id, @RequestBody IntentFormDTO form) {
        log.info("Updating intent with id: {}", id);
        
        return intentRepository.findById(id)
                .map(intent -> {
                    intent.setIntentName(form.intentName);
                    intent.setDescription(form.description);
                    intent.setTrainingPhrases(toJsonArray(form.trainingPhrases));
                    intent.setConfidenceThreshold(form.confidenceThreshold != null ? form.confidenceThreshold : 0.72);
                    intent.setFollowupGroup(form.followupGroup);
                    intent.setScenarioCode(form.scenarioCode);
                    intent.setCategory(form.category);
                    intent.setPriority(form.priority != null ? form.priority : 0);
                    intent.setActive(form.active != null ? form.active : true);
                    intent.setUpdatedAt(Instant.now());
                    
                    IntentConfig saved = intentRepository.save(intent);
                    log.info("Intent updated: {}", saved.getIntentKey());
                    return ResponseEntity.ok(toDTO(saved));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete intent configuration")
    public ResponseEntity<Void> deleteIntent(@PathVariable Long id) {
        log.info("Deleting intent with id: {}", id);
        
        return intentRepository.findById(id)
                .map(intent -> {
                    intentRepository.delete(intent);
                    log.info("Intent deleted: {}", intent.getIntentKey());
                    return ResponseEntity.ok().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ===================== TRAINING PHRASES =====================

    @PostMapping("/{id}/training-phrases")
    @Operation(summary = "Add training phrases to intent")
    public ResponseEntity<IntentDTO> addTrainingPhrases(
            @PathVariable Long id, 
            @RequestBody List<String> phrases) {
        log.info("Adding training phrases to intent: {}", id);
        
        return intentRepository.findById(id)
                .map(intent -> {
                    List<String> existing = parseJsonArray(intent.getTrainingPhrases());
                    existing.addAll(phrases);
                    intent.setTrainingPhrases(toJsonArray(existing));
                    intent.setUpdatedAt(Instant.now());
                    
                    IntentConfig saved = intentRepository.save(intent);
                    log.info("Training phrases added to: {}", saved.getIntentKey());
                    return ResponseEntity.ok(toDTO(saved));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}/training-phrases")
    @Operation(summary = "Remove training phrases from intent")
    public ResponseEntity<IntentDTO> removeTrainingPhrases(
            @PathVariable Long id, 
            @RequestBody List<String> phrases) {
        log.info("Removing training phrases from intent: {}", id);
        
        return intentRepository.findById(id)
                .map(intent -> {
                    List<String> existing = parseJsonArray(intent.getTrainingPhrases());
                    existing.removeAll(phrases);
                    intent.setTrainingPhrases(toJsonArray(existing));
                    intent.setUpdatedAt(Instant.now());
                    
                    IntentConfig saved = intentRepository.save(intent);
                    log.info("Training phrases removed from: {}", saved.getIntentKey());
                    return ResponseEntity.ok(toDTO(saved));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ===================== STATUS =====================

    @PatchMapping("/{id}/toggle")
    @Operation(summary = "Toggle intent active status")
    public ResponseEntity<IntentDTO> toggleIntent(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        log.info("Toggling intent status for id: {}", id);
        
        Boolean active = body.get("active");
        if (active == null) {
            return ResponseEntity.badRequest().build();
        }
        
        return intentRepository.findById(id)
                .map(intent -> {
                    intent.setActive(active);
                    intent.setUpdatedAt(Instant.now());
                    IntentConfig saved = intentRepository.save(intent);
                    log.info("Intent {} status set to: {}", saved.getIntentKey(), active);
                    return ResponseEntity.ok(toDTO(saved));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ===================== CATEGORIES =====================

    @GetMapping("/categories")
    @Operation(summary = "Get all unique categories")
    public ResponseEntity<List<String>> getCategories() {
        log.info("Fetching all intent categories");
        List<IntentConfig> intents = intentRepository.findAll();
        List<String> categories = intents.stream()
                .map(IntentConfig::getCategory)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
        return ResponseEntity.ok(categories);
    }

    // ===================== HELPERS =====================

    private IntentDTO toDTO(IntentConfig intent) {
        IntentDTO dto = new IntentDTO();
        dto.id = intent.getId();
        dto.intentKey = intent.getIntentKey();
        dto.intentName = intent.getIntentName();
        dto.description = intent.getDescription();
        dto.trainingPhrases = parseJsonArray(intent.getTrainingPhrases());
        dto.confidenceThreshold = intent.getConfidenceThreshold();
        dto.followupGroup = intent.getFollowupGroup();
        dto.scenarioCode = intent.getScenarioCode();
        dto.category = intent.getCategory();
        dto.priority = intent.getPriority();
        dto.active = intent.getActive();
        dto.createdAt = intent.getCreatedAt() != null ? intent.getCreatedAt().toString() : null;
        dto.updatedAt = intent.getUpdatedAt() != null ? intent.getUpdatedAt().toString() : null;
        return dto;
    }

    private String toJsonArray(List<String> list) {
        if (list == null || list.isEmpty()) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(list);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    private List<String> parseJsonArray(String json) {
        if (json == null || json.isEmpty()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, 
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (JsonProcessingException e) {
            log.error("Failed to parse JSON array", e);
            return new ArrayList<>();
        }
    }

    // ===================== DTOs =====================

    @Data
    public static class IntentDTO {
        public Long id;
        public String intentKey;
        public String intentName;
        public String description;
        public List<String> trainingPhrases;
        public Double confidenceThreshold;
        public String followupGroup;
        public String scenarioCode;
        public String category;
        public Integer priority;
        public Boolean active;
        public String createdAt;
        public String updatedAt;
    }

    @Data
    public static class IntentFormDTO {
        public String intentKey;
        public String intentName;
        public String description;
        public List<String> trainingPhrases;
        public Double confidenceThreshold;
        public String followupGroup;
        public String scenarioCode;
        public String category;
        public Integer priority;
        public Boolean active;
    }
}
