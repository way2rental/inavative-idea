package com.enterprise.ai.data.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Entity for ML model configuration.
 * Tracks all ML models used by the intelligence system.
 * 
 * NO HARDCODING - All model configuration from database.
 * Fully configurable and manageable from Admin Panel.
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "ai_ml_models")
public class MlModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Model type: INTENT, NER, COREFERENCE, DISAMBIGUATION, FORMATTING
     */
    @Column(name = "model_type", nullable = false, length = 50)
    private String modelType;

    /**
     * Model name (e.g., "distilbert-intent-classifier")
     */
    @Column(name = "model_name", nullable = false, length = 255)
    private String modelName;

    /**
     * Model version (e.g., "1.0.0", "2024-01-15")
     */
    @Column(name = "model_version", nullable = false, length = 50)
    private String modelVersion;

    /**
     * Path to model file (e.g., "/models/intent-classifier.onnx")
     */
    @Column(name = "model_path", nullable = false, length = 500)
    private String modelPath;

    /**
     * Model size in bytes
     */
    @Column(name = "model_size_bytes")
    private Long modelSizeBytes;

    /**
     * Model provider: ONNX, HUGGINGFACE, DJL
     */
    @Column(name = "provider", length = 50)
    private String provider;

    /**
     * Model format: ONNX, TORCH, TENSORFLOW
     */
    @Column(name = "format", length = 20)
    private String format;

    /**
     * Quantization: FP32, FP16, INT8
     */
    @Column(name = "quantization", length = 20)
    private String quantization;

    /**
     * Accuracy metrics (JSON)
     * Example: {"accuracy": 0.95, "f1_score": 0.94, "precision": 0.93}
     */
    @Column(name = "accuracy_metrics", columnDefinition = "JSON")
    private String accuracyMetrics;

    /**
     * Training date
     */
    @Column(name = "training_date")
    private LocalDate trainingDate;

    /**
     * Number of training examples
     */
    @Column(name = "training_examples_count")
    private Integer trainingExamplesCount;

    /**
     * Is this model active (loaded and ready)?
     */
    @Column(name = "active")
    @Builder.Default
    private Boolean active = true;

    /**
     * Is this model enabled (allowed to be used)?
     */
    @Column(name = "enabled")
    @Builder.Default
    private Boolean enabled = true;

    /**
     * Associated fallback layer
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fallback_layer_id")
    private FallbackLayer fallbackLayer;

    /**
     * Model-specific configuration (JSON)
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
     * Get unique identifier (type + version)
     */
    public String getUniqueId() {
        return modelType + ":" + modelVersion;
    }
}
