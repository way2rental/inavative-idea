package com.enterprise.ai.data.repository;

import com.enterprise.ai.data.entity.TrainingData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for TrainingData entity operations.
 */
@Repository
public interface TrainingDataRepository extends JpaRepository<TrainingData, Long> {

    /**
     * Find all training data by scenario code.
     */
    List<TrainingData> findByScenarioCode(String scenarioCode);

    /**
     * Find training data that hasn't been used for training yet.
     */
    List<TrainingData> findByUsedForTrainingFalse();

    /**
     * Find labeled training data (has scenario code).
     */
    @Query("SELECT t FROM TrainingData t WHERE t.scenarioCode IS NOT NULL AND t.usedForTraining = false")
    List<TrainingData> findLabeledUnusedTrainingData();

    /**
     * Find training data by training batch ID.
     */
    List<TrainingData> findByTrainingBatchId(String trainingBatchId);

    /**
     * Count labeled training data by scenario.
     */
    @Query("SELECT t.scenarioCode, COUNT(t) FROM TrainingData t WHERE t.scenarioCode IS NOT NULL AND t.usedForTraining = false GROUP BY t.scenarioCode")
    List<Object[]> countLabeledByScenario();

    /**
     * Find training data with user feedback.
     */
    List<TrainingData> findByUserFeedbackIsNotNull();
}
