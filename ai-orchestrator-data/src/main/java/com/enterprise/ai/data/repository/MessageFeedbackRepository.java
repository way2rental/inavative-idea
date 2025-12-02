package com.enterprise.ai.data.repository;

import com.enterprise.ai.data.entity.MessageFeedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * Repository for message feedback operations.
 */
@Repository
public interface MessageFeedbackRepository extends JpaRepository<MessageFeedback, Long> {

    /**
     * Find feedback by session ID
     */
    List<MessageFeedback> findBySessionId(String sessionId);

    /**
     * Find feedback by user ID
     */
    List<MessageFeedback> findByUserId(String userId);

    /**
     * Find all feedback within a date range
     */
    List<MessageFeedback> findByCreatedAtBetween(Instant start, Instant end);

    /**
     * Find feedback by type (like/dislike)
     */
    List<MessageFeedback> findByFeedbackType(String feedbackType);

    /**
     * Find feedback by scenario code
     */
    List<MessageFeedback> findByScenarioCode(String scenarioCode);

    /**
     * Count likes vs dislikes for a scenario
     */
    @Query("SELECT f.feedbackType, COUNT(f) FROM MessageFeedback f WHERE f.scenarioCode = :scenarioCode GROUP BY f.feedbackType")
    List<Object[]> countFeedbackByScenario(String scenarioCode);

    /**
     * Get overall feedback statistics
     */
    @Query("SELECT f.feedbackType, COUNT(f) FROM MessageFeedback f GROUP BY f.feedbackType")
    List<Object[]> getOverallFeedbackStats();

    /**
     * Get scenario-wise feedback breakdown
     */
    @Query("SELECT f.scenarioCode, f.feedbackType, COUNT(f) FROM MessageFeedback f GROUP BY f.scenarioCode, f.feedbackType ORDER BY f.scenarioCode")
    List<Object[]> getFeedbackByScenario();

    /**
     * Get recent negative feedback for review
     */
    @Query("SELECT f FROM MessageFeedback f WHERE f.feedbackType = 'dislike' ORDER BY f.createdAt DESC")
    List<MessageFeedback> getRecentNegativeFeedback();

    /**
     * Count feedback in last N days
     */
    @Query("SELECT f.feedbackType, COUNT(f) FROM MessageFeedback f WHERE f.createdAt >= :since GROUP BY f.feedbackType")
    List<Object[]> getFeedbackStatsSince(Instant since);
}
