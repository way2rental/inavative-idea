package com.enterprise.ai.data.repository;

import com.enterprise.ai.data.entity.AiScenario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for AI scenarios.
 */
@Repository
public interface AiScenarioRepository extends JpaRepository<AiScenario, Long> {

    Optional<AiScenario> findByScenarioCode(String scenarioCode);

    List<AiScenario> findByActiveTrue();

    boolean existsByScenarioCode(String scenarioCode);
}
