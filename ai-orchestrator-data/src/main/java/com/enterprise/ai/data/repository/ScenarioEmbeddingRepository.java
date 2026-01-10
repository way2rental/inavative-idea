package com.enterprise.ai.data.repository;

import com.enterprise.ai.data.entity.ScenarioEmbedding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for ScenarioEmbedding entity operations.
 */
@Repository
public interface ScenarioEmbeddingRepository extends JpaRepository<ScenarioEmbedding, Long> {

    /**
     * Find embedding by scenario code
     */
    Optional<ScenarioEmbedding> findByScenarioCode(String scenarioCode);

    /**
     * Find all active embeddings
     */
    List<ScenarioEmbedding> findByActiveTrue();

    /**
     * Check if embedding exists for scenario
     */
    boolean existsByScenarioCode(String scenarioCode);
}
