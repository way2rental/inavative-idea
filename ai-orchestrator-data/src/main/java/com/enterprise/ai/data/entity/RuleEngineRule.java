package com.enterprise.ai.data.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Entity for rule engine rules (DB-driven).
 * Defines deterministic rules for intent detection fallback.
 * 
 * NO HARDCODING - All rules come from database.
 * Rules can be created/updated via Admin Panel.
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "ai_rule_engine_rules")
public class RuleEngineRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Unique rule code
     */
    @Column(name = "rule_code", unique = true, nullable = false, length = 100)
    private String ruleCode;

    /**
     * Human-readable rule name
     */
    @Column(name = "rule_name", nullable = false, length = 255)
    private String ruleName;

    /**
     * Target scenario code (null = general rule)
     */
    @Column(name = "scenario_code", length = 100)
    private String scenarioCode;

    /**
     * Condition type: KEYWORD, PATTERN, REGEX, EMBEDDING, HYBRID
     */
    @Column(name = "condition_type", length = 50)
    private String conditionType;

    /**
     * Rule conditions as JSON
     * Example: {
     *   "keywords": ["balance", "account balance"],
     *   "patterns": ["show balance", "check balance"],
     *   "regex": "balance.*account|account.*balance",
     *   "required_params": ["accountId"]
     * }
     */
    @Column(name = "conditions", nullable = false, columnDefinition = "JSON")
    private String conditions;

    /**
     * Rule confidence score (0.0 to 1.0)
     */
    @Column(name = "confidence")
    @Builder.Default
    private Double confidence = 0.75;

    /**
     * Priority for matching (higher = more important)
     */
    @Column(name = "priority")
    @Builder.Default
    private Integer priority = 0;

    /**
     * Is this rule active?
     */
    @Column(name = "active")
    @Builder.Default
    private Boolean active = true;

    /**
     * Human-readable description
     */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /**
     * Example matches as JSON array
     */
    @Column(name = "examples", columnDefinition = "JSON")
    private String examples;

    @Column(name = "created_at")
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }

    // Relationship to scenario (optional, using scenario_code as foreign key)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scenario_code", referencedColumnName = "scenario_code", insertable = false, updatable = false)
    private AiScenario scenario;
}
