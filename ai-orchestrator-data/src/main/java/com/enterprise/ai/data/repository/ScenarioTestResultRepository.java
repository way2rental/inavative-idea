package com.enterprise.ai.data.repository;

import com.enterprise.ai.data.entity.ScenarioTestResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for scenario test results (sandbox tester).
 */
@Repository
public interface ScenarioTestResultRepository extends JpaRepository<ScenarioTestResult, Long> {

    /**
     * Find test result by test ID.
     */
    Optional<ScenarioTestResult> findByTestId(String testId);

    /**
     * Find all test results for a scenario.
     */
    List<ScenarioTestResult> findByScenarioCodeOrderByCreatedAtDesc(String scenarioCode);

    /**
     * Find recent test results (last N).
     */
    List<ScenarioTestResult> findTop20ByOrderByCreatedAtDesc();
}
