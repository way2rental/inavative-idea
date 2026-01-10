package com.enterprise.ai.data.repository;

import com.enterprise.ai.data.entity.KeywordPattern;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for KeywordPattern entity operations.
 */
@Repository
public interface KeywordPatternRepository extends JpaRepository<KeywordPattern, Long> {

    /**
     * Find patterns by scenario code
     */
    List<KeywordPattern> findByScenarioCode(String scenarioCode);

    /**
     * Find active patterns by scenario code, ordered by weight (descending - highest first)
     */
    @Query("SELECT k FROM KeywordPattern k WHERE k.scenarioCode = ?1 AND k.active = true ORDER BY k.weight DESC")
    List<KeywordPattern> findActiveByScenarioCodeOrderByWeightDesc(String scenarioCode);

    /**
     * Find pattern by scenario code and keyword
     */
    Optional<KeywordPattern> findByScenarioCodeAndKeyword(String scenarioCode, String keyword);

    /**
     * Find patterns by keyword (across all scenarios)
     */
    List<KeywordPattern> findByKeywordAndActiveTrue(String keyword);

    /**
     * Find all active patterns
     */
    List<KeywordPattern> findByActiveTrue();
}
