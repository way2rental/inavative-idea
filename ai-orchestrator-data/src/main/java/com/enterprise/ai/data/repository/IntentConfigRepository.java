package com.enterprise.ai.data.repository;

import com.enterprise.ai.data.entity.IntentConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for intent configuration management.
 */
@Repository
public interface IntentConfigRepository extends JpaRepository<IntentConfig, Long> {

    /**
     * Find by intent key
     */
    Optional<IntentConfig> findByIntentKey(String intentKey);

    /**
     * Find all active intents
     */
    List<IntentConfig> findByActiveTrue();

    /**
     * Find intents by category
     */
    List<IntentConfig> findByCategory(String category);

    /**
     * Find active intents by category
     */
    List<IntentConfig> findByCategoryAndActiveTrue(String category);

    /**
     * Find by scenario code
     */
    Optional<IntentConfig> findByScenarioCode(String scenarioCode);

    /**
     * Find intents ordered by priority
     */
    List<IntentConfig> findByActiveTrueOrderByPriorityDesc();

    /**
     * Check if intent exists by key
     */
    boolean existsByIntentKey(String intentKey);
}
