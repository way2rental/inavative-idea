package com.enterprise.ai.intelligence.service.training;

import com.enterprise.ai.common.dto.IntentResult;
import com.enterprise.ai.data.entity.TrainingData;
import com.enterprise.ai.data.repository.TrainingDataRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for collecting training data for ML models.
 * Collects user queries, predictions, and feedback for auto-learning.
 * 
 * NO HARDCODING - All data stored in database.
 * Supports auto-learning and continuous model improvement.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrainingDataCollectionService {

    private final TrainingDataRepository repository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Collect training data from user query and prediction.
     * This is called automatically for each user query.
     * 
     * @param userQuery User query text
     * @param sessionContext Session context
     * @param intentResult Prediction result
     */
    @Transactional
    public void collectQuery(String userQuery, String sessionContext, IntentResult intentResult) {
        try {
            TrainingData.TrainingDataBuilder builder = TrainingData.builder()
                    .userQuery(userQuery)
                    .sessionContext(sessionContext)
                    .usedForTraining(false);
            
            if (intentResult != null) {
                builder.predictedScenario(intentResult.getScenario());
                builder.predictedConfidence(java.math.BigDecimal.valueOf(intentResult.getConfidence()));
            }
            
            TrainingData trainingData = builder.build();

            // Convert params to JSON if available
            if (intentResult != null && intentResult.getParams() != null) {
                try {
                    String paramsJson = objectMapper.writeValueAsString(intentResult.getParams());
                    trainingData.setParams(paramsJson);
                } catch (Exception e) {
                    log.warn("Failed to serialize params to JSON: {}", e.getMessage());
                }
            }

            repository.save(trainingData);
            log.debug("Collected training data: query={}, predicted={}", 
                    userQuery.substring(0, Math.min(50, userQuery.length())), 
                    intentResult != null ? intentResult.getScenario() : "null");
        } catch (Exception e) {
            log.error("Failed to collect training data: {}", e.getMessage(), e);
            // Don't throw - training data collection should not break the flow
        }
    }

    /**
     * Collect training data with user feedback.
     * Called when user provides feedback (correct/incorrect).
     * 
     * @param userQuery User query text
     * @param predictedScenario What system predicted
     * @param userFeedback TRUE = correct, FALSE = incorrect
     * @param feedbackNotes Optional feedback notes
     */
    @Transactional
    public void collectFeedback(String userQuery, String predictedScenario, Boolean userFeedback, String feedbackNotes) {
        try {
            TrainingData trainingData = TrainingData.builder()
                    .userQuery(userQuery)
                    .predictedScenario(predictedScenario)
                    .userFeedback(userFeedback)
                    .feedbackNotes(feedbackNotes)
                    .usedForTraining(false)
                    .build();

            repository.save(trainingData);
            log.info("Collected training feedback: query={}, predicted={}, feedback={}", 
                    userQuery.substring(0, Math.min(50, userQuery.length())), 
                    predictedScenario, userFeedback);
        } catch (Exception e) {
            log.error("Failed to collect training feedback: {}", e.getMessage(), e);
        }
    }

    /**
     * Label training data (admin action).
     * Admin labels queries with correct scenario and entities.
     * 
     * @param trainingDataId Training data ID
     * @param scenarioCode Correct scenario code
     * @param entities Labeled entities (JSON)
     * @param params Labeled parameters (JSON)
     * @param labeledBy Who labeled this (admin user ID)
     */
    @Transactional
    public void labelTrainingData(Long trainingDataId, String scenarioCode, String entities, String params, String labeledBy) {
        try {
            TrainingData trainingData = repository.findById(trainingDataId)
                    .orElseThrow(() -> new IllegalArgumentException("Training data not found: " + trainingDataId));

            trainingData.setScenarioCode(scenarioCode);
            trainingData.setEntities(entities);
            trainingData.setParams(params);
            trainingData.setLabeledBy(labeledBy);
            trainingData.setLabeledAt(Instant.now());
            trainingData.setUsedForTraining(false); // Reset - can be used for retraining

            repository.save(trainingData);
            log.info("Labeled training data: id={}, scenario={}, labeledBy={}", 
                    trainingDataId, scenarioCode, labeledBy);
        } catch (Exception e) {
            log.error("Failed to label training data: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Mark training data as used for training.
     * Called after model training completes.
     * 
     * @param trainingDataIds List of training data IDs
     * @param trainingBatchId Training batch ID
     */
    @Transactional
    public void markAsUsedForTraining(java.util.List<Long> trainingDataIds, String trainingBatchId) {
        try {
            List<TrainingData> trainingDataList = repository.findAllById(trainingDataIds);
            for (TrainingData trainingData : trainingDataList) {
                trainingData.setUsedForTraining(true);
                trainingData.setTrainingBatchId(trainingBatchId);
            }
            repository.saveAll(trainingDataList);
            log.info("Marked {} training data as used for training batch: {}", 
                    trainingDataList.size(), trainingBatchId);
        } catch (Exception e) {
            log.error("Failed to mark training data as used: {}", e.getMessage(), e);
        }
    }

    /**
     * Get labeled training data for model training.
     * Returns labeled data that hasn't been used for training yet.
     * 
     * @return List of labeled training data
     */
    public List<TrainingData> getLabeledTrainingData() {
        return repository.findLabeledUnusedTrainingData();
    }

    /**
     * Get training data statistics.
     * 
     * @return Statistics map
     */
    public Map<String, Object> getTrainingDataStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        long total = repository.count();
        long labeled = repository.findLabeledUnusedTrainingData().size();
        long used = repository.findByUsedForTrainingFalse().stream()
                .filter(t -> Boolean.TRUE.equals(t.getUsedForTraining()))
                .count();
        long withFeedback = repository.findByUserFeedbackIsNotNull().size();

        stats.put("total", total);
        stats.put("labeled", labeled);
        stats.put("unlabeled", total - labeled);
        stats.put("usedForTraining", used);
        stats.put("withFeedback", withFeedback);

        return stats;
    }
}
