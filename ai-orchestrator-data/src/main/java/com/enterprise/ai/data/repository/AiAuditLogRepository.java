package com.enterprise.ai.data.repository;

import com.enterprise.ai.data.entity.AiAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * Repository for audit logs.
 */
@Repository
public interface AiAuditLogRepository extends JpaRepository<AiAuditLog, Long> {

    List<AiAuditLog> findByUserId(String userId);

    List<AiAuditLog> findByScenarioCode(String scenarioCode);

    List<AiAuditLog> findByRequestTimeBetween(Instant start, Instant end);

    List<AiAuditLog> findBySuccessFalse();

    // Pagination methods for admin panel
    Page<AiAuditLog> findByUserIdContaining(String userId, Pageable pageable);

    Page<AiAuditLog> findByScenarioCodeContaining(String scenarioCode, Pageable pageable);

    Page<AiAuditLog> findByUserIdContainingAndScenarioCodeContaining(String userId, String scenarioCode, Pageable pageable);

    // Analytics methods

    /**
     * Count logs between time range
     */
    long countByRequestTimeBetween(Instant start, Instant end);

    /**
     * Count successful logs between time range
     */
    @Query("SELECT COUNT(a) FROM AiAuditLog a WHERE a.requestTime >= :start AND a.requestTime < :end AND a.success = true")
    long countSuccessfulByRequestTimeBetween(@Param("start") Instant start, @Param("end") Instant end);

    /**
     * Get hourly request counts for analytics
     */
    @Query(value = "SELECT HOUR(request_time) as hour, COUNT(*) as count " +
            "FROM ai_audit_logs " +
            "WHERE request_time >= :start AND request_time < :end " +
            "GROUP BY HOUR(request_time) " +
            "ORDER BY hour", nativeQuery = true)
    List<Object[]> countByHourlyInterval(@Param("start") Instant start, @Param("end") Instant end);

    /**
     * Get top scenarios by usage
     */
    @Query("SELECT a.scenarioCode, COUNT(a) as usage " +
            "FROM AiAuditLog a " +
            "GROUP BY a.scenarioCode " +
            "ORDER BY usage DESC")
    List<Object[]> findTopScenariosByUsage(Pageable pageable);

    /**
     * Count logs by execution time range (for response distribution analytics)
     */
    @Query("SELECT COUNT(a) FROM AiAuditLog a WHERE a.executionTimeMs >= :min AND a.executionTimeMs < :max")
    long countByExecutionTimeBetween(@Param("min") int min, @Param("max") int max);

    /**
     * Count logs with execution time greater than threshold
     */
    @Query("SELECT COUNT(a) FROM AiAuditLog a WHERE a.executionTimeMs >= :threshold")
    long countByExecutionTimeGreaterThan(@Param("threshold") int threshold);

    /**
     * Get average execution time for analytics
     */
    @Query("SELECT AVG(a.executionTimeMs) FROM AiAuditLog a WHERE a.requestTime >= :start")
    Double getAverageExecutionTime(@Param("start") Instant start);
}
