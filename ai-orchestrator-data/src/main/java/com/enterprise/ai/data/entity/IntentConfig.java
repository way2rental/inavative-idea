package com.enterprise.ai.data.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Entity for dynamic intent configuration.
 * Supports runtime intent detection without code changes.
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "ai_intents")
public class IntentConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Unique intent key (e.g., TXN_STATUS)
     */
    @Column(name = "intent_key", unique = true, nullable = false, length = 100)
    private String intentKey;

    /**
     * Human-readable name
     */
    @Column(name = "intent_name", length = 200)
    private String intentName;

    /**
     * Intent description
     */
    @Column(name = "description", length = 500)
    private String description;

    /**
     * Training phrases as JSON array
     */
    @Column(name = "training_phrases", columnDefinition = "JSON")
    private String trainingPhrases;

    /**
     * Confidence threshold for this intent
     */
    @Column(name = "confidence_threshold")
    @Builder.Default
    private Double confidenceThreshold = 0.72;

    /**
     * Associated follow-up group key
     */
    @Column(name = "followup_group", length = 100)
    private String followupGroup;

    /**
     * Associated scenario code
     */
    @Column(name = "scenario_code", length = 100)
    private String scenarioCode;

    /**
     * Intent category for grouping
     */
    @Column(name = "category", length = 100)
    private String category;

    /**
     * Priority for disambiguation
     */
    @Column(name = "priority")
    @Builder.Default
    private Integer priority = 0;

    /**
     * Whether this intent is active
     */
    @Column(name = "active")
    @Builder.Default
    private Boolean active = true;

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
}
