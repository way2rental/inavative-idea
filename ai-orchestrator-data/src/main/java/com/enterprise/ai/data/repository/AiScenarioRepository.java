package com.enterprise.ai.data.repository;

import com.enterprise.ai.data.entity.AiScenario;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for AI scenarios.
 * 
 * PERFORMANCE: Scenario lookups are cached to avoid repeated DB queries.
 * Cache is invalidated via scheduled refresh in RbacService.
 */
@Repository
public interface AiScenarioRepository extends JpaRepository<AiScenario, Long> {

    @Cacheable(value = "scenarios", key = "#scenarioCode")
    Optional<AiScenario> findByScenarioCode(String scenarioCode);

    @Cacheable(value = "activeScenarios")
    List<AiScenario> findByActiveTrue();

    boolean existsByScenarioCode(String scenarioCode);
}
