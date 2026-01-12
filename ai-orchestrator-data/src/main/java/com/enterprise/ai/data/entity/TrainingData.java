package com.enterprise.ai.data.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Entity for ML training data.
 * Stores labeled training examples for ML model training.
 * 
 * NO HARDCODING - All training data from database.
 * Supports auto-learning and continuous improvement.
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "ai_training_data", indexes = {
    @Index(name = "idx_scenario", columnList = "scenario_code"),
    @Index(name = "idx_used_for_training", columnList = "used_for_training"),
    @Index(name = "idx_labeled_at", columnList = "labeled_at"),
    @Index(name = "idx_created_at", columnList = "created_at")
})
public class TrainingData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * User query text
     */
    @Column(name = "user_query", nullable = false, columnDefinition = "TEXT")
    private String userQuery;

    /**
     * Session context (conversation history)
     */
    @Column(name = "session_context", columnDefinition = "TEXT")
    private String sessionContext;

    /**
     * Labeled scenario code
     */
    @Column(name = "scenario_code", length = 100)
    private String scenarioCode;

    /**
     * Labeled entities (JSON)
     */
    @Column(name = "entities", columnDefinition = "JSON")
    private String entities;

    /**
     * Labeled parameters (JSON)
     */
    @Column(name = "params", columnDefinition = "JSON")
    private String params;

    /**
     * What the system predicted (for comparison)
     */
    @Column(name = "predicted_scenario", length = 100)
    private String predictedScenario;

    /**
     * Prediction confidence
     */
    @Column(name = "predicted_confidence", precision = 3, scale = 2)
    private java.math.BigDecimal predictedConfidence;

    /**
     * User feedback (TRUE = correct, FALSE = incorrect, NULL = no feedback)
     */
    @Column(name = "user_feedback")
    private Boolean userFeedback;

    /**
     * Feedback notes
     */
    @Column(name = "feedback_notes", columnDefinition = "TEXT")
    private String feedbackNotes;

    /**
     * Who labeled this (admin/user ID)
     */
    @Column(name = "labeled_by", length = 100)
    private String labeledBy;

    /**
     * When this was labeled
     */
    @Column(name = "labeled_at")
    private Instant labeledAt;

    /**
     * Has this been used for training?
     */
    @Column(name = "used_for_training")
    @Builder.Default
    private Boolean usedForTraining = false;

    /**
     * Which training batch used this
     */
    @Column(name = "training_batch_id", length = 100)
    private String trainingBatchId;

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
