package com.enterprise.ai.api.controller;

import com.enterprise.ai.data.entity.SystemConfig;
import com.enterprise.ai.data.service.SystemConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Admin controller for managing system configurations.
 * Enables full SaaS customization without hardcoded values.
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/config")
@RequiredArgsConstructor
@Tag(name = "System Config Admin", description = "System configuration management")
@PreAuthorize("hasRole('ADMIN')")
public class SystemConfigController {

    private final SystemConfigService configService;

    // ===================== LIST =====================

    @GetMapping
    @Operation(summary = "Get all configurations")
    public ResponseEntity<List<ConfigDTO>> getAllConfigs() {
        log.info("Fetching all system configurations");
        List<SystemConfig> configs = configService.getAllConfigs();
        List<ConfigDTO> dtos = configs.stream()
                .map(this::toDTO)
                .toList();
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/visible")
    @Operation(summary = "Get visible configurations only")
    public ResponseEntity<List<ConfigDTO>> getVisibleConfigs() {
        log.info("Fetching visible system configurations");
        List<SystemConfig> configs = configService.getVisibleConfigs();
        List<ConfigDTO> dtos = configs.stream()
                .map(this::toDTO)
                .toList();
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/category/{category}")
    @Operation(summary = "Get configurations by category")
    public ResponseEntity<List<ConfigDTO>> getConfigsByCategory(@PathVariable String category) {
        log.info("Fetching configurations for category: {}", category);
        List<SystemConfig> configs = configService.getConfigsByCategory(category);
        List<ConfigDTO> dtos = configs.stream()
                .map(this::toDTO)
                .toList();
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/categories")
    @Operation(summary = "Get all unique categories")
    public ResponseEntity<List<String>> getCategories() {
        log.info("Fetching all configuration categories");
        return ResponseEntity.ok(configService.getCategories());
    }

    // ===================== GET SINGLE =====================

    @GetMapping("/{id}")
    @Operation(summary = "Get configuration by ID")
    public ResponseEntity<ConfigDTO> getConfigById(@PathVariable Long id) {
        log.info("Fetching configuration with id: {}", id);
        return configService.getAllConfigs().stream()
                .filter(c -> c.getId().equals(id))
                .findFirst()
                .map(this::toDTO)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/key/{configKey}")
    @Operation(summary = "Get configuration by key")
    public ResponseEntity<ConfigDTO> getConfigByKey(@PathVariable String configKey) {
        log.info("Fetching configuration with key: {}", configKey);
        return configService.getConfig(configKey)
                .map(this::toDTO)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/value/{configKey}")
    @Operation(summary = "Get configuration value by key")
    public ResponseEntity<Map<String, Object>> getConfigValue(@PathVariable String configKey) {
        log.info("Fetching value for key: {}", configKey);
        return configService.getConfig(configKey)
                .map(config -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("key", configKey);
                    result.put("value", config.getConfigValue());
                    result.put("type", config.getValueType());
                    return ResponseEntity.ok(result);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ===================== CREATE/UPDATE =====================

    @PostMapping
    @Operation(summary = "Create new configuration")
    public ResponseEntity<ConfigDTO> createConfig(@RequestBody ConfigFormDTO form) {
        log.info("Creating new configuration: {}", form.configKey);
        
        if (configService.getConfig(form.configKey).isPresent()) {
            return ResponseEntity.badRequest().build();
        }

        SystemConfig config = SystemConfig.builder()
                .configKey(form.configKey)
                .configValue(form.configValue)
                .jsonValue(form.jsonValue)
                .category(form.category)
                .description(form.description)
                .defaultValue(form.defaultValue)
                .valueType(form.valueType != null ? form.valueType : "STRING")
                .editable(form.editable != null ? form.editable : true)
                .visible(form.visible != null ? form.visible : true)
                .tenantId(form.tenantId)
                .build();

        SystemConfig saved = configService.saveConfig(config);
        log.info("Configuration created: {}", saved.getConfigKey());
        return ResponseEntity.ok(toDTO(saved));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update configuration")
    public ResponseEntity<ConfigDTO> updateConfig(@PathVariable Long id, @RequestBody ConfigFormDTO form) {
        log.info("Updating configuration with id: {}", id);
        
        return configService.getAllConfigs().stream()
                .filter(c -> c.getId().equals(id))
                .findFirst()
                .map(config -> {
                    if (!config.getEditable()) {
                        log.warn("Attempted to update non-editable config: {}", config.getConfigKey());
                        return ResponseEntity.badRequest().<ConfigDTO>build();
                    }
                    
                    config.setConfigValue(form.configValue);
                    if (form.jsonValue != null) config.setJsonValue(form.jsonValue);
                    if (form.category != null) config.setCategory(form.category);
                    if (form.description != null) config.setDescription(form.description);
                    if (form.valueType != null) config.setValueType(form.valueType);
                    if (form.visible != null) config.setVisible(form.visible);
                    
                    SystemConfig saved = configService.saveConfig(config);
                    log.info("Configuration updated: {}", saved.getConfigKey());
                    return ResponseEntity.ok(toDTO(saved));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/key/{configKey}")
    @Operation(summary = "Update configuration value by key")
    public ResponseEntity<ConfigDTO> updateConfigByKey(
            @PathVariable String configKey, 
            @RequestBody Map<String, String> body) {
        log.info("Updating configuration value for key: {}", configKey);
        
        String value = body.get("value");
        if (value == null) {
            return ResponseEntity.badRequest().build();
        }
        
        return configService.getConfig(configKey)
                .map(config -> {
                    if (!config.getEditable()) {
                        log.warn("Attempted to update non-editable config: {}", configKey);
                        return ResponseEntity.badRequest().<ConfigDTO>build();
                    }
                    
                    config.setConfigValue(value);
                    SystemConfig saved = configService.saveConfig(config);
                    log.info("Configuration value updated: {}", saved.getConfigKey());
                    return ResponseEntity.ok(toDTO(saved));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ===================== DELETE =====================

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete configuration")
    public ResponseEntity<Void> deleteConfig(@PathVariable Long id) {
        log.info("Deleting configuration with id: {}", id);
        
        return configService.getAllConfigs().stream()
                .filter(c -> c.getId().equals(id))
                .findFirst()
                .map(config -> {
                    configService.deleteConfig(config.getConfigKey());
                    log.info("Configuration deleted: {}", config.getConfigKey());
                    return ResponseEntity.ok().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ===================== CACHE =====================

    @PostMapping("/cache/refresh")
    @Operation(summary = "Refresh configuration cache")
    public ResponseEntity<Map<String, String>> refreshCache() {
        log.info("Refreshing system configuration cache");
        configService.refreshCache();
        return ResponseEntity.ok(Map.of("message", "Configuration cache refreshed successfully"));
    }

    // ===================== RESET =====================

    @PostMapping("/key/{configKey}/reset")
    @Operation(summary = "Reset configuration to default value")
    public ResponseEntity<ConfigDTO> resetToDefault(@PathVariable String configKey) {
        log.info("Resetting configuration to default: {}", configKey);
        
        return configService.getConfig(configKey)
                .map(config -> {
                    if (config.getDefaultValue() != null) {
                        config.setConfigValue(config.getDefaultValue());
                        SystemConfig saved = configService.saveConfig(config);
                        log.info("Configuration reset to default: {}", saved.getConfigKey());
                        return ResponseEntity.ok(toDTO(saved));
                    }
                    return ResponseEntity.badRequest().<ConfigDTO>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ===================== BULK OPERATIONS =====================

    @PostMapping("/bulk")
    @Operation(summary = "Update multiple configurations at once")
    public ResponseEntity<List<ConfigDTO>> bulkUpdate(@RequestBody List<ConfigFormDTO> forms) {
        log.info("Bulk updating {} configurations", forms.size());
        
        List<ConfigDTO> results = new ArrayList<>();
        for (ConfigFormDTO form : forms) {
            configService.getConfig(form.configKey).ifPresent(config -> {
                if (config.getEditable()) {
                    config.setConfigValue(form.configValue);
                    if (form.jsonValue != null) config.setJsonValue(form.jsonValue);
                    SystemConfig saved = configService.saveConfig(config);
                    results.add(toDTO(saved));
                }
            });
        }
        
        log.info("Bulk updated {} configurations", results.size());
        return ResponseEntity.ok(results);
    }

    // ===================== HELPERS =====================

    private ConfigDTO toDTO(SystemConfig config) {
        ConfigDTO dto = new ConfigDTO();
        dto.id = config.getId();
        dto.configKey = config.getConfigKey();
        dto.configValue = config.getConfigValue();
        dto.jsonValue = config.getJsonValue();
        dto.category = config.getCategory();
        dto.description = config.getDescription();
        dto.defaultValue = config.getDefaultValue();
        dto.valueType = config.getValueType();
        dto.editable = config.getEditable();
        dto.visible = config.getVisible();
        dto.tenantId = config.getTenantId();
        dto.createdAt = config.getCreatedAt() != null ? config.getCreatedAt().toString() : null;
        dto.updatedAt = config.getUpdatedAt() != null ? config.getUpdatedAt().toString() : null;
        return dto;
    }

    // ===================== DTOs =====================

    @Data
    public static class ConfigDTO {
        public Long id;
        public String configKey;
        public String configValue;
        public String jsonValue;
        public String category;
        public String description;
        public String defaultValue;
        public String valueType;
        public Boolean editable;
        public Boolean visible;
        public String tenantId;
        public String createdAt;
        public String updatedAt;
    }

    @Data
    public static class ConfigFormDTO {
        public String configKey;
        public String configValue;
        public String jsonValue;
        public String category;
        public String description;
        public String defaultValue;
        public String valueType;
        public Boolean editable;
        public Boolean visible;
        public String tenantId;
    }
}
