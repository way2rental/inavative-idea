package com.enterprise.ai.intelligence.service.training;

import com.enterprise.ai.data.entity.MlModel;
import com.enterprise.ai.data.entity.TrainingData;
import com.enterprise.ai.data.repository.MlModelRepository;
import com.enterprise.ai.data.repository.TrainingDataRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * ML Training Service - Trains ML models using DJL (Deep Java Library).
 * Trains Intent Classifier and NER models in Java (no external Python scripts needed).
 * 
 * NO HARDCODING - All configuration from database.
 * Uses DJL for model training and ONNX export.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MlTrainingService {

    private final TrainingDataRepository trainingDataRepository;
    private final MlModelRepository modelRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Train Intent Classifier model.
     * Uses DJL to fine-tune BERT/DistilBERT for intent classification.
     * 
     * @param modelName Model name
     * @param modelVersion Model version
     * @param minLabeledData Minimum labeled data required (default: 100)
     * @return Training result with model path and metrics
     */
    @Transactional
    public TrainingResult trainIntentClassifier(String modelName, String modelVersion, int minLabeledData) {
        try {
            log.info("Starting Intent Classifier training: name={}, version={}", modelName, modelVersion);

            // Check if enough training data
            List<TrainingData> labeledData = trainingDataRepository.findLabeledUnusedTrainingData();
            long intentDataCount = labeledData.stream()
                    .filter(t -> t.getScenarioCode() != null)
                    .count();

            if (intentDataCount < minLabeledData) {
                throw new IllegalArgumentException(
                        String.format("Insufficient training data: %d labeled examples (minimum: %d)", 
                                intentDataCount, minLabeledData));
            }

            // Filter training data for intent classification (has scenario code)
            List<TrainingData> trainingData = labeledData.stream()
                    .filter(t -> t.getScenarioCode() != null)
                    .limit(10000) // Limit to 10k examples for training
                    .toList();

            log.info("Training Intent Classifier with {} examples", trainingData.size());

            // TODO: Implement DJL-based training
            // This requires:
            // 1. Load pre-trained BERT/DistilBERT model (using DJL ModelZoo)
            // 2. Prepare training data (queries -> scenario codes)
            // 3. Fine-tune model for classification
            // 4. Evaluate model
            // 5. Export to ONNX format
            // 
            // Example structure (actual implementation needed):
            // Model model = ModelZoo.loadModel("bert-base-uncased");
            // Trainer trainer = model.newTrainer(trainingConfig);
            // trainer.fit(dataset);
            // model.save(Paths.get("models/intent-classifier.onnx"), "onnx");

            log.warn("DJL-based Intent Classifier training not yet implemented - placeholder");
            log.info("To implement: Use DJL ModelZoo to load BERT, fine-tune for classification, export to ONNX");

            // Placeholder - return null for now
            // In actual implementation, return TrainingResult with model path and metrics
            return null;

        } catch (Exception e) {
            log.error("Failed to train Intent Classifier: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to train Intent Classifier", e);
        }
    }

    /**
     * Train NER model.
     * Uses DJL to fine-tune BERT for Named Entity Recognition.
     * 
     * @param modelName Model name
     * @param modelVersion Model version
     * @param minLabeledData Minimum labeled data required (default: 100)
     * @return Training result with model path and metrics
     */
    @Transactional
    public TrainingResult trainNerModel(String modelName, String modelVersion, int minLabeledData) {
        try {
            log.info("Starting NER model training: name={}, version={}", modelName, modelVersion);

            // Check if enough training data
            List<TrainingData> labeledData = trainingDataRepository.findLabeledUnusedTrainingData();
            long nerDataCount = labeledData.stream()
                    .filter(t -> t.getEntities() != null && !t.getEntities().isEmpty())
                    .count();

            if (nerDataCount < minLabeledData) {
                throw new IllegalArgumentException(
                        String.format("Insufficient training data: %d labeled examples (minimum: %d)", 
                                nerDataCount, minLabeledData));
            }

            // Filter training data for NER (has entity labels)
            List<TrainingData> trainingData = labeledData.stream()
                    .filter(t -> t.getEntities() != null && !t.getEntities().isEmpty())
                    .limit(10000) // Limit to 10k examples for training
                    .toList();

            log.info("Training NER model with {} examples", trainingData.size());

            // TODO: Implement DJL-based training
            // This requires:
            // 1. Load pre-trained BERT model (using DJL ModelZoo)
            // 2. Prepare training data (queries -> entity labels with BIO tagging)
            // 3. Fine-tune model for NER
            // 4. Evaluate model
            // 5. Export to ONNX format

            log.warn("DJL-based NER model training not yet implemented - placeholder");
            log.info("To implement: Use DJL ModelZoo to load BERT, fine-tune for NER, export to ONNX");

            // Placeholder - return null for now
            return null;

        } catch (Exception e) {
            log.error("Failed to train NER model: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to train NER model", e);
        }
    }

    /**
     * Check if training data is ready for model training.
     * 
     * @param modelType Model type (INTENT or NER)
     * @param minLabeledData Minimum labeled data required
     * @return True if enough data for training
     */
    public boolean isTrainingDataReady(String modelType, int minLabeledData) {
        try {
            List<TrainingData> labeledData = trainingDataRepository.findLabeledUnusedTrainingData();

            if ("INTENT".equals(modelType)) {
                long count = labeledData.stream()
                        .filter(t -> t.getScenarioCode() != null)
                        .count();
                return count >= minLabeledData;
            } else if ("NER".equals(modelType)) {
                long count = labeledData.stream()
                        .filter(t -> t.getEntities() != null && !t.getEntities().isEmpty())
                        .count();
                return count >= minLabeledData;
            }

            return false;
        } catch (Exception e) {
            log.error("Error checking training data readiness: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Get training data statistics.
     * 
     * @return Statistics map
     */
    public Map<String, Object> getTrainingDataStatistics() {
        Map<String, Object> stats = new HashMap<>();

        try {
            List<TrainingData> labeledData = trainingDataRepository.findLabeledUnusedTrainingData();

            long intentDataCount = labeledData.stream()
                    .filter(t -> t.getScenarioCode() != null)
                    .count();

            long nerDataCount = labeledData.stream()
                    .filter(t -> t.getEntities() != null && !t.getEntities().isEmpty())
                    .count();

            stats.put("totalLabeled", labeledData.size());
            stats.put("intentDataCount", intentDataCount);
            stats.put("nerDataCount", nerDataCount);
            stats.put("intentDataReady", intentDataCount >= 100);
            stats.put("nerDataReady", nerDataCount >= 100);

        } catch (Exception e) {
            log.error("Error getting training data statistics: {}", e.getMessage(), e);
        }

        return stats;
    }

    /**
     * Register trained model in database.
     * 
     * @param modelType Model type (INTENT or NER)
     * @param modelName Model name
     * @param modelVersion Model version
     * @param modelPath Path to ONNX model file
     * @param metrics Training metrics (accuracy, F1, etc.)
     * @return Registered ML model
     */
    @Transactional
    public MlModel registerModel(String modelType, String modelName, String modelVersion, 
                                 Path modelPath, Map<String, Object> metrics) {
        try {
            // Check if model already exists
            Optional<MlModel> existing = modelRepository.findByModelTypeAndModelVersion(modelType, modelVersion);
            if (existing.isPresent()) {
                throw new IllegalArgumentException(
                        String.format("Model already exists: %s version %s", modelType, modelVersion));
            }

            // Create ML model entity
            MlModel model = MlModel.builder()
                    .modelType(modelType)
                    .modelName(modelName)
                    .modelVersion(modelVersion)
                    .modelPath(modelPath.toString())
                    .provider("ONNX")
                    .format("ONNX")
                    .active(true)
                    .enabled(true)
                    .build();

            // Set metrics as JSON
            if (metrics != null && !metrics.isEmpty()) {
                try {
                    model.setAccuracyMetrics(objectMapper.writeValueAsString(metrics));
                } catch (Exception e) {
                    log.warn("Failed to serialize metrics to JSON: {}", e.getMessage());
                }
            }

            MlModel saved = modelRepository.save(model);
            log.info("Registered ML model: type={}, name={}, version={}, path={}", 
                    modelType, modelName, modelVersion, modelPath);

            return saved;

        } catch (Exception e) {
            log.error("Failed to register model: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to register model", e);
        }
    }

    /**
     * Training result DTO.
     */
    public static class TrainingResult {
        private String modelPath;
        private Map<String, Object> metrics;
        private long trainingExamples;
        private long trainingTimeMs;

        // Getters and setters
        public String getModelPath() { return modelPath; }
        public void setModelPath(String modelPath) { this.modelPath = modelPath; }
        public Map<String, Object> getMetrics() { return metrics; }
        public void setMetrics(Map<String, Object> metrics) { this.metrics = metrics; }
        public long getTrainingExamples() { return trainingExamples; }
        public void setTrainingExamples(long trainingExamples) { this.trainingExamples = trainingExamples; }
        public long getTrainingTimeMs() { return trainingTimeMs; }
        public void setTrainingTimeMs(long trainingTimeMs) { this.trainingTimeMs = trainingTimeMs; }
    }
}
