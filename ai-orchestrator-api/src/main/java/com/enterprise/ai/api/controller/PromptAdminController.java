package com.enterprise.ai.api.controller;

import com.enterprise.ai.data.entity.PromptTemplate;
import com.enterprise.ai.data.repository.PromptTemplateRepository;
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
 * Admin controller for prompt template management.
 * Supports versioning, testing, and runtime configuration.
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/prompts")
@RequiredArgsConstructor
@Tag(name = "Prompt Admin", description = "Prompt template management")
@PreAuthorize("hasRole('ADMIN')")
public class PromptAdminController {

    private final PromptTemplateRepository promptRepository;
    private final ObjectMapper objectMapper;

    // ===================== LIST =====================

    @GetMapping
    @Operation(summary = "Get all prompt templates")
    public ResponseEntity<List<PromptDTO>> getAllPrompts() {
        log.info("Fetching all prompt templates");
        List<PromptTemplate> prompts = promptRepository.findAllByOrderByCategoryAscPromptKeyAsc();
        List<PromptDTO> dtos = prompts.stream()
                .map(this::toDTO)
                .toList();
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/enabled")
    @Operation(summary = "Get all enabled prompts")
    public ResponseEntity<List<PromptDTO>> getEnabledPrompts() {
        log.info("Fetching enabled prompts");
        List<PromptTemplate> prompts = promptRepository.findByEnabledTrue();
        List<PromptDTO> dtos = prompts.stream()
                .map(this::toDTO)
                .toList();
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/category/{category}")
    @Operation(summary = "Get prompts by category")
    public ResponseEntity<List<PromptDTO>> getPromptsByCategory(@PathVariable String category) {
        log.info("Fetching prompts for category: {}", category);
        List<PromptTemplate> prompts = promptRepository.findByCategory(category);
        List<PromptDTO> dtos = prompts.stream()
                .map(this::toDTO)
                .toList();
        return ResponseEntity.ok(dtos);
    }

    // ===================== CRUD =====================

    @GetMapping("/{id}")
    @Operation(summary = "Get prompt by ID")
    public ResponseEntity<PromptDTO> getPromptById(@PathVariable Long id) {
        log.info("Fetching prompt with id: {}", id);
        return promptRepository.findById(id)
                .map(this::toDTO)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/key/{promptKey}")
    @Operation(summary = "Get prompt by key")
    public ResponseEntity<PromptDTO> getPromptByKey(@PathVariable String promptKey) {
        log.info("Fetching prompt with key: {}", promptKey);
        return promptRepository.findByPromptKey(promptKey)
                .map(this::toDTO)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @Operation(summary = "Create new prompt template")
    public ResponseEntity<PromptDTO> createPrompt(@RequestBody PromptFormDTO form) {
        log.info("Creating new prompt: {}", form.promptKey);
        
        if (promptRepository.existsByPromptKey(form.promptKey)) {
            return ResponseEntity.badRequest().build();
        }

        PromptTemplate prompt = PromptTemplate.builder()
                .promptKey(form.promptKey)
                .category(form.category)
                .systemPrompt(form.systemPrompt)
                .userTemplate(form.userTemplate)
                .responseFormat(form.responseFormat != null ? form.responseFormat : "TEXT")
                .temperature(form.temperature != null ? form.temperature : 0.7)
                .maxTokens(form.maxTokens != null ? form.maxTokens : 1024)
                .enabled(form.enabled != null ? form.enabled : true)
                .createdBy(form.createdBy)
                .build();

        PromptTemplate saved = promptRepository.save(prompt);
        log.info("Prompt created: {}", saved.getPromptKey());
        return ResponseEntity.ok(toDTO(saved));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update prompt template")
    public ResponseEntity<PromptDTO> updatePrompt(@PathVariable Long id, @RequestBody PromptFormDTO form) {
        log.info("Updating prompt with id: {}", id);
        
        return promptRepository.findById(id)
                .map(prompt -> {
                    // Save current version to history
                    saveVersionToHistory(prompt);
                    
                    prompt.setCategory(form.category);
                    prompt.setSystemPrompt(form.systemPrompt);
                    prompt.setUserTemplate(form.userTemplate);
                    prompt.setResponseFormat(form.responseFormat != null ? form.responseFormat : "TEXT");
                    prompt.setTemperature(form.temperature != null ? form.temperature : 0.7);
                    prompt.setMaxTokens(form.maxTokens != null ? form.maxTokens : 1024);
                    prompt.setEnabled(form.enabled != null ? form.enabled : true);
                    prompt.setUpdatedBy(form.updatedBy);
                    prompt.setVersion(prompt.getVersion() + 1);
                    prompt.setUpdatedAt(Instant.now());
                    
                    PromptTemplate saved = promptRepository.save(prompt);
                    log.info("Prompt updated: {}", saved.getPromptKey());
                    return ResponseEntity.ok(toDTO(saved));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete prompt template")
    public ResponseEntity<Void> deletePrompt(@PathVariable Long id) {
        log.info("Deleting prompt with id: {}", id);
        
        return promptRepository.findById(id)
                .map(prompt -> {
                    promptRepository.delete(prompt);
                    log.info("Prompt deleted: {}", prompt.getPromptKey());
                    return ResponseEntity.ok().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ===================== STATUS =====================

    @PatchMapping("/{id}/toggle")
    @Operation(summary = "Toggle prompt enabled status")
    public ResponseEntity<PromptDTO> togglePrompt(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        log.info("Toggling prompt status for id: {}", id);
        
        Boolean enabled = body.get("enabled");
        if (enabled == null) {
            return ResponseEntity.badRequest().build();
        }
        
        return promptRepository.findById(id)
                .map(prompt -> {
                    prompt.setEnabled(enabled);
                    prompt.setUpdatedAt(Instant.now());
                    PromptTemplate saved = promptRepository.save(prompt);
                    log.info("Prompt {} status set to: {}", saved.getPromptKey(), enabled);
                    return ResponseEntity.ok(toDTO(saved));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ===================== VERSION HISTORY =====================

    @GetMapping("/{id}/history")
    @Operation(summary = "Get prompt version history")
    public ResponseEntity<List<Map<String, Object>>> getPromptHistory(@PathVariable Long id) {
        log.info("Fetching history for prompt: {}", id);
        
        return promptRepository.findById(id)
                .map(prompt -> {
                    List<Map<String, Object>> history = parseVersionHistory(prompt.getVersionHistory());
                    return ResponseEntity.ok(history);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/rollback/{version}")
    @Operation(summary = "Rollback to a specific version")
    public ResponseEntity<PromptDTO> rollbackPrompt(@PathVariable Long id, @PathVariable int version) {
        log.info("Rolling back prompt {} to version {}", id, version);
        
        return promptRepository.findById(id)
                .map(prompt -> {
                    List<Map<String, Object>> history = parseVersionHistory(prompt.getVersionHistory());
                    Map<String, Object> targetVersion = history.stream()
                            .filter(h -> h.get("version").equals(version))
                            .findFirst()
                            .orElse(null);
                    
                    if (targetVersion == null) {
                        return ResponseEntity.notFound().<PromptDTO>build();
                    }
                    
                    // Save current version to history first
                    saveVersionToHistory(prompt);
                    
                    // Restore from history
                    prompt.setSystemPrompt((String) targetVersion.get("systemPrompt"));
                    prompt.setUserTemplate((String) targetVersion.get("userTemplate"));
                    prompt.setTemperature((Double) targetVersion.getOrDefault("temperature", 0.7));
                    prompt.setMaxTokens((Integer) targetVersion.getOrDefault("maxTokens", 1024));
                    prompt.setVersion(prompt.getVersion() + 1);
                    prompt.setUpdatedAt(Instant.now());
                    
                    PromptTemplate saved = promptRepository.save(prompt);
                    log.info("Prompt rolled back to version {}", version);
                    return ResponseEntity.ok(toDTO(saved));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ===================== TESTING =====================

    @PostMapping("/{id}/test")
    @Operation(summary = "Test prompt template with sample data")
    public ResponseEntity<Map<String, Object>> testPrompt(
            @PathVariable Long id, 
            @RequestBody Map<String, Object> testData) {
        log.info("Testing prompt with id: {}", id);
        
        return promptRepository.findById(id)
                .map(prompt -> {
                    Map<String, Object> result = new HashMap<>();
                    
                    // Replace placeholders in templates
                    String processedSystem = replacePlaceholders(prompt.getSystemPrompt(), testData);
                    String processedUser = replacePlaceholders(prompt.getUserTemplate(), testData);
                    
                    result.put("processedSystemPrompt", processedSystem);
                    result.put("processedUserTemplate", processedUser);
                    result.put("temperature", prompt.getTemperature());
                    result.put("maxTokens", prompt.getMaxTokens());
                    result.put("responseFormat", prompt.getResponseFormat());
                    result.put("success", true);
                    
                    return ResponseEntity.ok(result);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ===================== CATEGORIES =====================

    @GetMapping("/categories")
    @Operation(summary = "Get all unique categories")
    public ResponseEntity<List<String>> getCategories() {
        log.info("Fetching all prompt categories");
        List<PromptTemplate> prompts = promptRepository.findAll();
        List<String> categories = prompts.stream()
                .map(PromptTemplate::getCategory)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
        return ResponseEntity.ok(categories);
    }

    // ===================== HELPERS =====================

    private PromptDTO toDTO(PromptTemplate prompt) {
        PromptDTO dto = new PromptDTO();
        dto.id = prompt.getId();
        dto.promptKey = prompt.getPromptKey();
        dto.category = prompt.getCategory();
        dto.systemPrompt = prompt.getSystemPrompt();
        dto.userTemplate = prompt.getUserTemplate();
        dto.responseFormat = prompt.getResponseFormat();
        dto.temperature = prompt.getTemperature();
        dto.maxTokens = prompt.getMaxTokens();
        dto.version = prompt.getVersion();
        dto.enabled = prompt.getEnabled();
        dto.createdAt = prompt.getCreatedAt() != null ? prompt.getCreatedAt().toString() : null;
        dto.updatedAt = prompt.getUpdatedAt() != null ? prompt.getUpdatedAt().toString() : null;
        dto.createdBy = prompt.getCreatedBy();
        dto.updatedBy = prompt.getUpdatedBy();
        dto.historyCount = countHistoryVersions(prompt.getVersionHistory());
        return dto;
    }

    private void saveVersionToHistory(PromptTemplate prompt) {
        try {
            List<Map<String, Object>> history = parseVersionHistory(prompt.getVersionHistory());
            
            Map<String, Object> currentVersion = new HashMap<>();
            currentVersion.put("version", prompt.getVersion());
            currentVersion.put("systemPrompt", prompt.getSystemPrompt());
            currentVersion.put("userTemplate", prompt.getUserTemplate());
            currentVersion.put("temperature", prompt.getTemperature());
            currentVersion.put("maxTokens", prompt.getMaxTokens());
            currentVersion.put("savedAt", Instant.now().toString());
            
            history.add(0, currentVersion);
            
            // Keep only last 10 versions
            if (history.size() > 10) {
                history = history.subList(0, 10);
            }
            
            prompt.setVersionHistory(objectMapper.writeValueAsString(history));
        } catch (JsonProcessingException e) {
            log.error("Failed to save version history", e);
        }
    }

    private List<Map<String, Object>> parseVersionHistory(String json) {
        if (json == null || json.isEmpty()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, 
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class));
        } catch (JsonProcessingException e) {
            log.error("Failed to parse version history", e);
            return new ArrayList<>();
        }
    }

    private int countHistoryVersions(String json) {
        return parseVersionHistory(json).size();
    }

    private String replacePlaceholders(String template, Map<String, Object> data) {
        if (template == null) return "";
        String result = template;
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}", String.valueOf(entry.getValue()));
        }
        return result;
    }

    // ===================== DTOs =====================

    @Data
    public static class PromptDTO {
        public Long id;
        public String promptKey;
        public String category;
        public String systemPrompt;
        public String userTemplate;
        public String responseFormat;
        public Double temperature;
        public Integer maxTokens;
        public Integer version;
        public Boolean enabled;
        public String createdAt;
        public String updatedAt;
        public String createdBy;
        public String updatedBy;
        public int historyCount;
    }

    @Data
    public static class PromptFormDTO {
        public String promptKey;
        public String category;
        public String systemPrompt;
        public String userTemplate;
        public String responseFormat;
        public Double temperature;
        public Integer maxTokens;
        public Boolean enabled;
        public String createdBy;
        public String updatedBy;
    }
}
