package com.enterprise.ai.data.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Entity for follow-up question templates.
 * DB-driven templates for asking missing parameters.
 * 
 * NO HARDCODING - All templates from database.
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "ai_followup_templates")
public class FollowUpTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Scenario code
     */
    @Column(name = "scenario_code", nullable = false, length = 100)
    private String scenarioCode;

    /**
     * Parameter name this template is for
     */
    @Column(name = "param_name", nullable = false, length = 100)
    private String paramName;

    /**
     * Template question (Freemarker format)
     */
    @Column(name = "template_question", nullable = false, columnDefinition = "TEXT")
    private String templateQuestion;

    /**
     * Context hints as JSON (when to use this question)
     */
    @Column(name = "context_hints", columnDefinition = "JSON")
    private String contextHints;

    /**
     * Priority (higher = more important)
     */
    @Column(name = "priority")
    @Builder.Default
    private Integer priority = 0;

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
}
