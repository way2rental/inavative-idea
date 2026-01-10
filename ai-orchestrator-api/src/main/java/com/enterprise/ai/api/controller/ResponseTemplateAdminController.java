package com.enterprise.ai.api.controller;

import com.enterprise.ai.data.entity.ResponseTemplate;
import com.enterprise.ai.data.repository.ResponseTemplateRepository;
import com.enterprise.ai.data.service.ConfigCacheService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Admin controller for response template management.
 * Manages Freemarker templates for formatting scenario responses.
 * 
 * NO HARDCODING - All templates from database.
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/response-templates")
@RequiredArgsConstructor
@Tag(name = "Response Template Admin", description = "Response template management")
@PreAuthorize("hasRole('ADMIN')")
public class ResponseTemplateAdminController {

    private final ResponseTemplateRepository templateRepository;
    private final ConfigCacheService configCacheService;

    // ===================== LIST =====================

    @GetMapping
    @Operation(summary = "Get all response templates")
    public ResponseEntity<List<ResponseTemplateDTO>> getAllTemplates() {
        log.info("Fetching all response templates");
        List<ResponseTemplate> templates = templateRepository.findAll();
        List<ResponseTemplateDTO> dtos = templates.stream()
                .map(this::toDTO)
                .toList();
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/active")
    @Operation(summary = "Get all active templates")
    public ResponseEntity<List<ResponseTemplateDTO>> getActiveTemplates() {
        log.info("Fetching active response templates");
        List<ResponseTemplate> templates = templateRepository.findAllActiveOrderByPriorityDesc();
        List<ResponseTemplateDTO> dtos = templates.stream()
                .map(this::toDTO)
                .toList();
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/scenario/{scenarioCode}")
    @Operation(summary = "Get templates for a scenario")
    public ResponseEntity<List<ResponseTemplateDTO>> getTemplatesByScenario(@PathVariable String scenarioCode) {
        log.info("Fetching templates for scenario: {}", scenarioCode);
        List<ResponseTemplate> templates = templateRepository.findByScenarioCode(scenarioCode);
        List<ResponseTemplateDTO> dtos = templates.stream()
                .map(this::toDTO)
                .toList();
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/scenario/{scenarioCode}/active")
    @Operation(summary = "Get active templates for a scenario")
    public ResponseEntity<List<ResponseTemplateDTO>> getActiveTemplatesByScenario(@PathVariable String scenarioCode) {
        log.info("Fetching active templates for scenario: {}", scenarioCode);
        List<ResponseTemplate> templates = templateRepository.findByScenarioCodeAndActiveTrueOrderByPriorityDesc(scenarioCode);
        List<ResponseTemplateDTO> dtos = templates.stream()
                .map(this::toDTO)
                .toList();
        return ResponseEntity.ok(dtos);
    }

    // ===================== CRUD =====================

    @GetMapping("/{id}")
    @Operation(summary = "Get template by ID")
    public ResponseEntity<ResponseTemplateDTO> getTemplateById(@PathVariable Long id) {
        log.info("Fetching template with id: {}", id);
        return templateRepository.findById(id != null ? id : 0L)
                .map(this::toDTO)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @Operation(summary = "Create new response template")
    public ResponseEntity<ResponseTemplateDTO> createTemplate(@RequestBody ResponseTemplateFormDTO form) {
        log.info("Creating new response template for scenario: {}", form.scenarioCode);
        
        // Verify scenario exists
        if (!configCacheService.getScenarioByCode(form.scenarioCode).isPresent()) {
            log.warn("Scenario not found: {}", form.scenarioCode);
            return ResponseEntity.badRequest().build();
        }

        ResponseTemplate template = ResponseTemplate.builder()
                .scenarioCode(form.scenarioCode)
                .responseType(form.responseType != null ? form.responseType : "TEXT")
                .templateContent(form.templateContent)
                .templateVariables(form.templateVariables)
                .conditions(form.conditions)
                .priority(form.priority != null ? form.priority : 0)
                .templateVersion(form.templateVersion != null ? form.templateVersion : 1)
                .active(form.active != null ? form.active : true)
                .build();

        ResponseTemplate saved = templateRepository.save(template);
        log.info("Response template created: id={}, scenario={}", saved.getId(), saved.getScenarioCode());
        return ResponseEntity.ok(toDTO(saved));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update response template")
    public ResponseEntity<ResponseTemplateDTO> updateTemplate(@PathVariable Long id, @RequestBody ResponseTemplateFormDTO form) {
        log.info("Updating template with id: {}", id);
        
        if (id == null) {
            return ResponseEntity.badRequest().build();
        }
        
        return templateRepository.findById(id)
                .map(template -> {
                    template.setScenarioCode(form.scenarioCode);
                    template.setResponseType(form.responseType != null ? form.responseType : "TEXT");
                    template.setTemplateContent(form.templateContent);
                    template.setTemplateVariables(form.templateVariables);
                    template.setConditions(form.conditions);
                    template.setPriority(form.priority != null ? form.priority : 0);
                    template.setTemplateVersion(form.templateVersion != null ? form.templateVersion : template.getTemplateVersion());
                    if (form.active != null) {
                        template.setActive(form.active);
                    }
                    template.setUpdatedAt(Instant.now());
                    
                    ResponseTemplate saved = templateRepository.save(template);
                    log.info("Response template updated: id={}, scenario={}", saved.getId(), saved.getScenarioCode());
                    return ResponseEntity.ok(toDTO(saved));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete response template")
    public ResponseEntity<Void> deleteTemplate(@PathVariable Long id) {
        log.info("Deleting template with id: {}", id);
        
        if (id == null) {
            return ResponseEntity.badRequest().build();
        }
        
        return templateRepository.findById(id)
                .map(template -> {
                    templateRepository.delete(template);
                    log.info("Response template deleted: id={}, scenario={}", template.getId(), template.getScenarioCode());
                    return ResponseEntity.ok().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ===================== STATUS =====================

    @PatchMapping("/{id}/toggle")
    @Operation(summary = "Toggle template active status")
    public ResponseEntity<ResponseTemplateDTO> toggleTemplate(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        log.info("Toggling template status for id: {}", id);
        
        if (id == null) {
            return ResponseEntity.badRequest().build();
        }
        
        Boolean active = body.get("active");
        if (active == null) {
            return ResponseEntity.badRequest().build();
        }
        
        return templateRepository.findById(id)
                .map(template -> {
                    template.setActive(active);
                    template.setUpdatedAt(Instant.now());
                    ResponseTemplate saved = templateRepository.save(template);
                    log.info("Template {} status set to: {}", saved.getId(), active);
                    return ResponseEntity.ok(toDTO(saved));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ===================== HELPERS =====================

    private ResponseTemplateDTO toDTO(ResponseTemplate template) {
        if (template == null) {
            return null;
        }
        
        ResponseTemplateDTO dto = new ResponseTemplateDTO();
        dto.id = template.getId();
        dto.scenarioCode = template.getScenarioCode();
        dto.responseType = template.getResponseType();
        dto.templateContent = template.getTemplateContent();
        dto.templateVariables = template.getTemplateVariables();
        dto.conditions = template.getConditions();
        dto.priority = template.getPriority();
        dto.templateVersion = template.getTemplateVersion();
        dto.active = template.getActive();
        dto.createdAt = template.getCreatedAt() != null ? template.getCreatedAt().toString() : null;
        dto.updatedAt = template.getUpdatedAt() != null ? template.getUpdatedAt().toString() : null;
        
        // Add scenario name if available
        if (template.getScenarioCode() != null) {
            configCacheService.getScenarioByCode(template.getScenarioCode())
                    .ifPresent(scenario -> dto.scenarioName = scenario.getScenarioName());
        }
        
        return dto;
    }

    // ===================== DTOs =====================

    @Data
    public static class ResponseTemplateDTO {
        public Long id;
        public String scenarioCode;
        public String scenarioName;
        public String responseType;
        public String templateContent;
        public String templateVariables;
        public String conditions;
        public Integer priority;
        public Integer templateVersion;
        public Boolean active;
        public String createdAt;
        public String updatedAt;
    }

    @Data
    public static class ResponseTemplateFormDTO {
        public String scenarioCode;
        public String responseType;
        public String templateContent;
        public String templateVariables;
        public String conditions;
        public Integer priority;
        public Integer templateVersion;
        public Boolean active;
    }
}
