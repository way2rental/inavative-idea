package com.enterprise.ai.data.repository;

import com.enterprise.ai.data.entity.FollowUpGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for follow-up group management.
 */
@Repository
public interface FollowUpGroupRepository extends JpaRepository<FollowUpGroup, Long> {

    /**
     * Find by group key
     */
    Optional<FollowUpGroup> findByGroupKey(String groupKey);

    /**
     * Find all active groups
     */
    List<FollowUpGroup> findByActiveTrue();

    /**
     * Find groups containing a scenario code
     */
    List<FollowUpGroup> findByScenarioCodesContaining(String scenarioCode);

    /**
     * Check if group exists by key
     */
    boolean existsByGroupKey(String groupKey);
}
