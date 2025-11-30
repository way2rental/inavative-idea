package com.enterprise.ai.data.service;

import com.enterprise.ai.data.entity.AiAuditLog;
import com.enterprise.ai.data.repository.AiAuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for audit log operations.
 * Centralizes all audit log data access with proper caching.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AiAuditLogRepository auditLogRepository;

    /**
     * Get paginated audit logs with optional filters.
     */
    public Page<AiAuditLog> getAuditLogs(int page, int size, String userId, String scenarioCode) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "requestTime"));
        
        // If both filters provided
        if (userId != null && scenarioCode != null) {
            return auditLogRepository.findByUserIdContainingAndScenarioCodeContaining(userId, scenarioCode, pageRequest);
        }
        // If only userId filter
        if (userId != null) {
            return auditLogRepository.findByUserIdContaining(userId, pageRequest);
        }
        // If only scenarioCode filter
        if (scenarioCode != null) {
            return auditLogRepository.findByScenarioCodeContaining(scenarioCode, pageRequest);
        }
        // No filters
        return auditLogRepository.findAll(pageRequest);
    }

    /**
     * Get today's statistics for dashboard.
     * Cached to avoid repeated calculations.
     */
    @Cacheable(value = "auditStats", key = "'today'")
    public Map<String, Object> getTodayStats() {
        Instant startOfDay = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant();
        List<AiAuditLog> todayLogs = auditLogRepository.findByRequestTimeBetween(startOfDay, Instant.now());
        
        long todayRequests = todayLogs.size();
        
        // Calculate average response time
        double avgResponseTime = todayLogs.stream()
                .filter(log -> log.getResponseTime() != null && log.getRequestTime() != null)
                .mapToLong(log -> log.getResponseTime().toEpochMilli() - log.getRequestTime().toEpochMilli())
                .average()
                .orElse(0);
        
        // Calculate success rate
        long successfulRequests = todayLogs.stream()
                .filter(log -> Boolean.TRUE.equals(log.getSuccess()))
                .count();
        double successRate = todayRequests > 0 ? (successfulRequests * 100.0 / todayRequests) : 100;
        
        Map<String, Object> stats = new HashMap<>();
        stats.put("todayRequests", todayRequests);
        stats.put("avgResponseTime", avgResponseTime);
        stats.put("successRate", successRate);
        
        log.debug("Today stats calculated: {} requests, {}ms avg, {}% success", todayRequests, avgResponseTime, successRate);
        return stats;
    }

    /**
     * Get recent audit logs (limit specified).
     */
    public List<AiAuditLog> getRecentLogs(int limit) {
        PageRequest pageRequest = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "requestTime"));
        return auditLogRepository.findAll(pageRequest).getContent();
    }

    /**
     * Get total count of audit logs.
     */
    public long getTotalCount() {
        return auditLogRepository.count();
    }

    /**
     * Save audit log.
     */
    public AiAuditLog save(AiAuditLog auditLog) {
        return auditLogRepository.save(auditLog);
    }
}
