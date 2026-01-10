package com.enterprise.ai.data.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Entity for entity extraction patterns (DB-driven NER).
 * Defines patterns for extracting entities like account IDs, dates, amounts, etc.
 * 
 * NO HARDCODING - All patterns come from database.
 * Patterns can be REGEX, NER_MODEL, CONTEXT_BASED, or VALIDATION.
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "ai_entity_patterns")
public class EntityPattern {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Entity type (DB-driven - e.g., "ACCOUNT_ID", "TRANSACTION_ID", "AMOUNT", "DATE", etc.)
     * NOT hardcoded - can be any entity type
     */
    @Column(name = "entity_type", nullable = false, length = 50)
    private String entityType;

    /**
     * Pattern type: REGEX, NER_MODEL, CONTEXT_BASED, VALIDATION
     */
    @Column(name = "pattern_type", nullable = false, length = 20)
    private String patternType;

    /**
     * Pattern definition (regex pattern, model path, etc.)
     */
    @Column(name = "pattern_definition", nullable = false, columnDefinition = "TEXT")
    private String patternDefinition;

    /**
     * Human-readable display name
     */
    @Column(name = "display_name", length = 255)
    private String displayName;

    /**
     * Description of this pattern
     */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /**
     * Additional validation rule as JSON
     * Example: {"minLength": 10, "maxLength": 20, "format": "ALPHANUMERIC"}
     */
    @Column(name = "validation_rule", columnDefinition = "TEXT")
    private String validationRule;

    /**
     * Example matches as JSON array
     * Example: ["ACC123456", "ACC789012"]
     */
    @Column(name = "examples", columnDefinition = "JSON")
    private String examples;

    /**
     * Confidence boost (0.0 to 1.0) - adds to extraction confidence
     */
    @Column(name = "confidence_boost", precision = 3, scale = 2)
    @Builder.Default
    private BigDecimal confidenceBoost = BigDecimal.ZERO;

    /**
     * Priority (higher = tried first)
     */
    @Column(name = "priority")
    @Builder.Default
    private Integer priority = 0;

    /**
     * Is this pattern active?
     */
    @Column(name = "active")
    @Builder.Default
    private Boolean active = true;

    /**
     * Pattern-specific configuration as JSON
     * Example: {"caseSensitive": false, "multiline": true}
     */
    @Column(name = "config_json", columnDefinition = "JSON")
    private String configJson;

    @Column(name = "created_at")
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }
}
