package com.enterprise.ai.data.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Entity for fallback layer configuration.
 * Defines the multi-layer fallback architecture for intent detection.
 * 
 * NO HARDCODING - All fallback layer configuration from database.
 * Fully configurable and manageable from Admin Panel.
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "ai_fallback_layers")
public class FallbackLayer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Unique layer code (e.g., ML_INTENT_CLASSIFIER, EMBEDDING_SIMILARITY)
     */
    @Column(name = "layer_code", unique = true, nullable = false, length = 50)
    private String layerCode;

    /**
     * Human-readable layer name
     */
    @Column(name = "layer_name", nullable = false, length = 255)
    private String layerName;

    /**
     * Layer type: ML, EMBEDDING, RULES, KEYWORDS, CONVERSATIONAL
     */
    @Column(name = "layer_type", nullable = false, length = 50)
    private String layerType;

    /**
     * Execution priority (1=highest, 5=lowest)
     * Lower number = higher priority
     */
    @Column(name = "priority", nullable = false)
    private Integer priority;

    /**
     * Is this layer enabled?
     */
    @Column(name = "enabled")
    @Builder.Default
    private Boolean enabled = true;

    /**
     * Minimum confidence threshold to accept result from this layer
     */
    @Column(name = "confidence_threshold")
    @Builder.Default
    private Double confidenceThreshold = 0.85;

    /**
     * Maximum uncertainty allowed (for ML models)
     */
    @Column(name = "max_uncertainty")
    @Builder.Default
    private Double maxUncertainty = 0.10;

    /**
     * Timeout in milliseconds
     */
    @Column(name = "timeout_ms")
    @Builder.Default
    private Integer timeoutMs = 5000;

    /**
     * Human-readable description
     */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /**
     * Layer-specific configuration (JSON)
     * Example for ML: {"model_type": "INTENT", "provider": "ONNX"}
     * Example for EMBEDDING: {"embedding_model": "all-MiniLM-L6-v2", "top_k": 5}
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

    // =====================================================================
    // HELPER METHODS
    // =====================================================================

    /**
     * Check if this layer is ML-based
     */
    public boolean isMlLayer() {
        return "ML".equalsIgnoreCase(layerType);
    }

    /**
     * Check if this layer is embedding-based
     */
    public boolean isEmbeddingLayer() {
        return "EMBEDDING".equalsIgnoreCase(layerType);
    }

    /**
     * Check if this layer is rule-based
     */
    public boolean isRuleLayer() {
        return "RULES".equalsIgnoreCase(layerType);
    }

    /**
     * Check if this layer is keyword-based
     */
    public boolean isKeywordLayer() {
        return "KEYWORDS".equalsIgnoreCase(layerType);
    }

    /**
     * Check if this layer is conversational (catch-all)
     */
    public boolean isConversationalLayer() {
        return "CONVERSATIONAL".equalsIgnoreCase(layerType);
    }
}
