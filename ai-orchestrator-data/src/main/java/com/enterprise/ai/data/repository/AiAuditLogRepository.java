package com.enterprise.ai.data.repository;

import com.enterprise.ai.data.entity.AiAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
