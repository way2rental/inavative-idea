package com.enterprise.ai.api.controller.admin;

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
 * Supports versioning, rollback, and runtime updates.
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/prompts")
@RequiredArgsConstructor
@Tag(name = "Prompt Admin", description = "Prompt template management with versioning")
@PreAuthorize("hasRole('ADMIN')")
public class PromptAdminController {

    private final PromptTemplateRepository promptRepository;
    private final ObjectMapper objectMapper;

    // ===================== LIST =====================

    @GetMapping
    @Operation(summary = "Get all prompt templates")
    public ResponseEntity<List<PromptDTO>> getAllPrompts() {
        log.info("Fetching all prompt templates");
        List<PromptTemplate> prompts = promptRepository.findAll();
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
                .responseFormat(form.responseFormat)
                .temperature(form.temperature)
                .maxTokens(form.maxTokens)
                .enabled(form.enabled != null ? form.enabled : true)
                .version(1)
                .createdBy(form.createdBy)
                .build();

        PromptTemplate saved = promptRepository.save(prompt);
        log.info("Prompt created: {}", saved.getPromptKey());
        return ResponseEntity.ok(toDTO(saved));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update prompt template with versioning")
    public ResponseEntity<PromptDTO> updatePrompt(@PathVariable Long id, @RequestBody PromptFormDTO form) {
        log.info("Updating prompt with id: {}", id);
        
        return promptRepository.findById(id)
                .map(prompt -> {
                    // Save current version to history
                    saveToHistory(prompt);
                    
                    // Update fields
                    prompt.setCategory(form.category);
                    prompt.setSystemPrompt(form.systemPrompt);
                    prompt.setUserTemplate(form.userTemplate);
                    prompt.setResponseFormat(form.responseFormat);
                    prompt.setTemperature(form.temperature);
                    prompt.setMaxTokens(form.maxTokens);
                    prompt.setEnabled(form.enabled != null ? form.enabled : true);
                    prompt.setVersion(prompt.getVersion() + 1);
                    prompt.setUpdatedBy(form.updatedBy);
                    prompt.setUpdatedAt(Instant.now());
                    
                    PromptTemplate saved = promptRepository.save(prompt);
                    log.info("Prompt updated: {} (version {})", saved.getPromptKey(), saved.getVersion());
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

    // ===================== VERSIONING =====================

    @PostMapping("/{id}/rollback/{version}")
    @Operation(summary = "Rollback prompt to previous version")
    public ResponseEntity<PromptDTO> rollbackPrompt(@PathVariable Long id, @PathVariable int version) {
        log.info("Rolling back prompt {} to version {}", id, version);
        
        return promptRepository.findById(id)
                .map(prompt -> {
                    List<Map<String, Object>> history = parseHistory(prompt.getVersionHistory());
                    Optional<Map<String, Object>> targetVersion = history.stream()
                            .filter(h -> (Integer) h.get("version") == version)
                            .findFirst();
                    
                    if (targetVersion.isEmpty()) {
                        return ResponseEntity.badRequest().<PromptDTO>build();
                    }
                    
                    Map<String, Object> versionData = targetVersion.get();
                    
                    // Save current version to history
                    saveToHistory(prompt);
                    
                    // Restore from version
                    prompt.setSystemPrompt((String) versionData.get("systemPrompt"));
                    prompt.setUserTemplate((String) versionData.get("userTemplate"));
                    prompt.setVersion(prompt.getVersion() + 1);
                    prompt.setUpdatedAt(Instant.now());
                    
                    PromptTemplate saved = promptRepository.save(prompt);
                    log.info("Prompt rolled back: {} to version {} (now version {})", 
                            saved.getPromptKey(), version, saved.getVersion());
                    return ResponseEntity.ok(toDTO(saved));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/history")
    @Operation(summary = "Get prompt version history")
    public ResponseEntity<List<Map<String, Object>>> getPromptHistory(@PathVariable Long id) {
        log.info("Fetching history for prompt: {}", id);
        
        return promptRepository.findById(id)
                .map(prompt -> {
                    List<Map<String, Object>> history = parseHistory(prompt.getVersionHistory());
                    return ResponseEntity.ok(history);
                })
                .orElse(ResponseEntity.notFound().build());
    }

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

    // ===================== TEST =====================

    @PostMapping("/{id}/test")
    @Operation(summary = "Test prompt template with sample data")
    public ResponseEntity<Map<String, Object>> testPrompt(
            @PathVariable Long id, 
            @RequestBody Map<String, Object> testData) {
        log.info("Testing prompt: {}", id);
        
        return promptRepository.findById(id)
                .map(prompt -> {
                    String renderedPrompt = renderTemplate(prompt.getUserTemplate(), testData);
                    
                    Map<String, Object> result = new HashMap<>();
                    result.put("promptKey", prompt.getPromptKey());
                    result.put("systemPrompt", prompt.getSystemPrompt());
                    result.put("renderedUserPrompt", renderedPrompt);
                    result.put("testData", testData);
                    
                    return ResponseEntity.ok(result);
                })
                .orElse(ResponseEntity.notFound().build());
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
        dto.historyCount = parseHistory(prompt.getVersionHistory()).size();
        return dto;
    }

    private void saveToHistory(PromptTemplate prompt) {
        try {
            List<Map<String, Object>> history = parseHistory(prompt.getVersionHistory());
            
            Map<String, Object> versionData = new HashMap<>();
            versionData.put("version", prompt.getVersion());
            versionData.put("systemPrompt", prompt.getSystemPrompt());
            versionData.put("userTemplate", prompt.getUserTemplate());
            versionData.put("savedAt", Instant.now().toString());
            
            history.add(versionData);
            
            // Keep only last 10 versions
            if (history.size() > 10) {
                history = history.subList(history.size() - 10, history.size());
            }
            
            prompt.setVersionHistory(objectMapper.writeValueAsString(history));
        } catch (JsonProcessingException e) {
            log.error("Failed to save version history", e);
        }
    }

    private List<Map<String, Object>> parseHistory(String historyJson) {
        if (historyJson == null || historyJson.isEmpty()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(historyJson, 
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class));
        } catch (JsonProcessingException e) {
            log.error("Failed to parse version history", e);
            return new ArrayList<>();
        }
    }

    private String renderTemplate(String template, Map<String, Object> data) {
        if (template == null) return "";
        String rendered = template;
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            rendered = rendered.replace("{{" + entry.getKey() + "}}", 
                    entry.getValue() != null ? entry.getValue().toString() : "");
        }
        return rendered;
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
