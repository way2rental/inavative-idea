package com.enterprise.ai.data.repository;

import com.enterprise.ai.data.entity.AiResponseMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for AI Response Mappings.
 */
@Repository
public interface AiResponseMappingRepository extends JpaRepository<AiResponseMapping, Long> {

    /**
     * Find all mappings for a scenario
     */
    List<AiResponseMapping> findByScenarioCodeAndActiveTrueOrderByDisplayOrderAsc(String scenarioCode);

    /**
     * Find mappings by scenario code
     */
    List<AiResponseMapping> findByScenarioCode(String scenarioCode);

    /**
     * Find active mappings for scenario
     */
    List<AiResponseMapping> findByScenarioCodeAndActiveTrue(String scenarioCode);

    /**
     * Check if mappings exist for scenario
     */
    boolean existsByScenarioCode(String scenarioCode);

    /**
     * Delete all mappings for a scenario
     */
    void deleteByScenarioCode(String scenarioCode);
}
