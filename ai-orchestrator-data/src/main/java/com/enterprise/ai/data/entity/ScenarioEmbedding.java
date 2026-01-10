package com.enterprise.ai.data.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Entity for pre-computed scenario embeddings.
 * Used by the embedding-based fallback layer for fast similarity search.
 * 
 * NO HARDCODING - All embeddings come from database.
 * Embeddings can be generated and updated via Admin Panel.
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "ai_scenario_embeddings")
public class ScenarioEmbedding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Scenario code (unique, one embedding per scenario)
     */
    @Column(name = "scenario_code", unique = true, nullable = false, length = 100)
    private String scenarioCode;

    /**
     * Embedding vector as JSON array (e.g., [0.123, -0.456, ...])
     * Typically 384 dimensions for all-MiniLM-L6-v2
     */
    @Column(name = "embedding_vector", nullable = false, columnDefinition = "JSON")
    private String embeddingVector;

    /**
     * Model used to generate embeddings (e.g., "all-MiniLM-L6-v2")
     */
    @Column(name = "embedding_model", length = 100)
    private String embeddingModel;

    /**
     * Trigger phrases as JSON array
     */
    @Column(name = "trigger_phrases", columnDefinition = "JSON")
    private String triggerPhrases;

    /**
     * Training examples as JSON array
     */
    @Column(name = "training_examples", columnDefinition = "JSON")
    private String trainingExamples;

    /**
     * Semantic tags as JSON array (domain-specific tags)
     */
    @Column(name = "semantic_tags", columnDefinition = "JSON")
    private String semanticTags;

    /**
     * Priority for matching (higher = more important)
     */
    @Column(name = "priority")
    @Builder.Default
    private Integer priority = 0;

    /**
     * Is this embedding active?
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

    // Relationship to scenario (optional, using scenario_code as foreign key)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scenario_code", referencedColumnName = "scenario_code", insertable = false, updatable = false)
    private AiScenario scenario;
}
