package com.enterprise.ai.data.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Entity for response templates (Freemarker - DB-driven).
 * Used by ResponseFormatterService to format scenario responses.
 * 
 * NO HARDCODING - All templates from database.
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "ai_response_templates")
public class ResponseTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Scenario code this template is for
     */
    @Column(name = "scenario_code", nullable = false, length = 100)
    private String scenarioCode;

    /**
     * Response type: TEXT, TABLE, KV, MIXED, FOLLOW_UP, ERROR
     */
    @Column(name = "response_type", nullable = false, length = 20)
    private String responseType;

    /**
     * Freemarker template content
     */
    @Column(name = "template_content", nullable = false, columnDefinition = "TEXT")
    private String templateContent;

    /**
     * Template variables documentation as JSON
     * Example: {"variables": ["result", "data", "scenarioCode", "assistantName"]}
     */
    @Column(name = "template_variables", columnDefinition = "JSON")
    private String templateVariables;

    /**
     * Conditions as JSON (when to use this template)
     * Example: {"hasData": true, "dataType": "table"}
     */
    @Column(name = "conditions", columnDefinition = "JSON")
    private String conditions;

    /**
     * Priority (higher = more important)
     */
    @Column(name = "priority")
    @Builder.Default
    private Integer priority = 0;

    /**
     * Template version
     */
    @Column(name = "template_version")
    @Builder.Default
    private Integer templateVersion = 1;

    /**
     * Is this template active?
     */
    @Column(name = "active")
    @Builder.Default
    private Boolean active = true;

    @Column(name = "created_at")
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }

    // Relationship to scenario (using scenario_code as foreign key)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scenario_code", referencedColumnName = "scenario_code", insertable = false, updatable = false)
    private AiScenario scenario;
}
