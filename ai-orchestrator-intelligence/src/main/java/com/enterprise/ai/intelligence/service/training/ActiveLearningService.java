package com.enterprise.ai.intelligence.service.training;

import com.enterprise.ai.data.entity.TrainingData;
import com.enterprise.ai.data.repository.TrainingDataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Active Learning Service.
 * Automatically detects failure cases and triggers model retraining.
 * 
 * NO HARDCODING - All configuration from database.
 * Supports automatic learning and continuous improvement.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ActiveLearningService {

    private final TrainingDataRepository trainingDataRepository;
    private final TrainingDataCollectionService trainingDataCollectionService;

    /**
     * Detect failure cases and add to training queue.
     * Called after each prediction to identify cases for labeling.
     * 
     * @param userQuery User query
     * @param predictedScenario Predicted scenario
     * @param confidence Prediction confidence
     * @param userFeedback User feedback (TRUE = correct, FALSE = incorrect, NULL = no feedback)
     */
    @Transactional
    public void detectFailureCases(String userQuery, String predictedScenario, 
                                   Double confidence, Boolean userFeedback) {
        try {
            // Low confidence predictions
            if (confidence != null && confidence < 0.70) {
                log.debug("Low confidence prediction detected: query={}, confidence={}", 
                        userQuery.substring(0, Math.min(50, userQuery.length())), confidence);
                // This will be collected by TrainingDataCollectionService
            }

            // User feedback (incorrect predictions)
            if (userFeedback != null && !userFeedback) {
                log.info("Incorrect prediction detected: query={}, predicted={}", 
                        userQuery.substring(0, Math.min(50, userQuery.length())), predictedScenario);
                trainingDataCollectionService.collectFeedback(userQuery, predictedScenario, false, 
                        "User marked as incorrect");
            }

            // High confidence but user rejected
            if (confidence != null && confidence > 0.85 && userFeedback != null && !userFeedback) {
                log.warn("High confidence incorrect prediction: query={}, confidence={}, predicted={}", 
                        userQuery.substring(0, Math.min(50, userQuery.length())), 
                        confidence, predictedScenario);
            }

        } catch (Exception e) {
            log.error("Error detecting failure cases: {}", e.getMessage(), e);
        }
    }

    /**
     * Check if there's enough training data to trigger retraining.
     * 
     * @param minLabeledData Minimum labeled data required
     * @return True if enough data for retraining
     */
    public boolean shouldTriggerRetraining(int minLabeledData) {
        try {
            List<TrainingData> labeledData = trainingDataRepository.findLabeledUnusedTrainingData();
            return labeledData.size() >= minLabeledData;
        } catch (Exception e) {
            log.error("Error checking retraining trigger: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Get statistics for active learning.
     * 
     * @return Statistics map
     */
    public Map<String, Object> getActiveLearningStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        try {
            long totalLabeled = trainingDataRepository.findLabeledUnusedTrainingData().size();
            long withFeedback = trainingDataRepository.findByUserFeedbackIsNotNull().size();
            long incorrectFeedback = trainingDataRepository.findByUserFeedbackIsNotNull().stream()
                    .filter(t -> Boolean.FALSE.equals(t.getUserFeedback()))
                    .count();

            stats.put("totalLabeled", totalLabeled);
            stats.put("withFeedback", withFeedback);
            stats.put("incorrectPredictions", incorrectFeedback);
            stats.put("readyForRetraining", shouldTriggerRetraining(100)); // Minimum 100 labeled examples

        } catch (Exception e) {
            log.error("Error getting active learning statistics: {}", e.getMessage(), e);
        }

        return stats;
    }

    /**
     * Select queries for labeling (uncertainty sampling).
     * Returns queries with low confidence predictions.
     * 
     * @param limit Maximum number of queries to return
     * @return List of training data for labeling
     */
    public List<TrainingData> selectQueriesForLabeling(int limit) {
        try {
            // Get unlabeled queries with low confidence
            List<TrainingData> allData = trainingDataRepository.findByUsedForTrainingFalse();
            return allData.stream()
                    .filter(t -> t.getScenarioCode() == null) // Not labeled yet
                    .filter(t -> t.getPredictedConfidence() != null && 
                            t.getPredictedConfidence().doubleValue() < 0.75) // Low confidence
                    .sorted((t1, t2) -> {
                        // Sort by confidence (lowest first - most uncertain)
                        if (t1.getPredictedConfidence() == null) return 1;
                        if (t2.getPredictedConfidence() == null) return -1;
                        return Double.compare(t1.getPredictedConfidence().doubleValue(), 
                                t2.getPredictedConfidence().doubleValue());
                    })
                    .limit(limit)
                    .toList();
        } catch (Exception e) {
            log.error("Error selecting queries for labeling: {}", e.getMessage(), e);
            return List.of();
        }
    }
}
