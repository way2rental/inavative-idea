package com.enterprise.ai.data.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Entity for system-wide configuration settings.
 * Stores all configurable values that should not be hardcoded.
 * 
 * This supports SaaS deployments where each tenant (bank) can have
 * their own branding, settings, and behavior configurations.
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "ai_system_config")
public class SystemConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Unique configuration key (e.g., ORG_NAME, AI_ASSISTANT_NAME)
     */
    @Column(name = "config_key", unique = true, nullable = false, length = 100)
    private String configKey;

    /**
     * Configuration value
     */
    @Column(name = "config_value", columnDefinition = "TEXT")
    private String configValue;

    /**
     * Optional JSON value for complex configurations
     */
    @Column(name = "json_value", columnDefinition = "JSON")
    private String jsonValue;

    /**
     * Category for grouping (BRANDING, PERFORMANCE, SECURITY, PROMPTS, etc.)
     */
    @Column(name = "category", length = 50)
    private String category;

    /**
     * Human-readable description
     */
    @Column(name = "description", length = 500)
    private String description;

    /**
     * Default value (for documentation and reset purposes)
     */
    @Column(name = "default_value", length = 500)
    private String defaultValue;

    /**
     * Data type: STRING, INTEGER, LONG, DOUBLE, BOOLEAN, JSON
     */
    @Column(name = "value_type", length = 20)
    @Builder.Default
    private String valueType = "STRING";

    /**
     * Whether this config is editable via admin UI
     */
    @Column(name = "editable")
    @Builder.Default
    private Boolean editable = true;

    /**
     * Whether this config is visible in admin UI
     */
    @Column(name = "visible")
    @Builder.Default
    private Boolean visible = true;

    /**
     * Tenant ID for multi-tenant SaaS deployments (null = global/default)
     */
    @Column(name = "tenant_id", length = 100)
    private String tenantId;

    /**
     * Creation timestamp
     */
    @Column(name = "created_at")
    @Builder.Default
    private Instant createdAt = Instant.now();

    /**
     * Last update timestamp
     */
    @Column(name = "updated_at")
    private Instant updatedAt;

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }

    // ===================== HELPER METHODS =====================

    public String getStringValue() {
        return configValue;
    }

    public Integer getIntValue() {
        try {
            return configValue != null ? Integer.parseInt(configValue) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public Long getLongValue() {
        try {
            return configValue != null ? Long.parseLong(configValue) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public Double getDoubleValue() {
        try {
            return configValue != null ? Double.parseDouble(configValue) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public Boolean getBooleanValue() {
        return configValue != null && Boolean.parseBoolean(configValue);
    }
}
