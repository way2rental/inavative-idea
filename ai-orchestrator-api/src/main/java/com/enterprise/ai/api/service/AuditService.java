package com.enterprise.ai.api.service;

import com.enterprise.ai.common.dto.IntentResult;
import com.enterprise.ai.common.dto.ScenarioResult;
import com.enterprise.ai.data.entity.AiAuditLog;
import com.enterprise.ai.data.repository.AiAuditLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Async audit logging service.
 * 
 * All audit operations are executed asynchronously to avoid blocking
 * the main request thread and improving response latency.
 * 
 * SECURITY: Audit logs are MANDATORY for:
 * - RBI compliance
 * - Security incident investigation
 * - Usage analytics
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AiAuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    /**
     * Log an audit entry asynchronously.
     * This method returns immediately without blocking the caller.
     * 
     * @param executionId Unique execution ID for tracing
     * @param userId User who initiated the request
     * @param scenarioCode Scenario that was executed (nullable)
     * @param requestTime When the request was received
     * @param responseTime When the response was sent
     * @param success Whether the execution was successful
     * @param errorMessage Error message if failed (nullable)
     * @param intent Detected intent (nullable)
     * @param result Execution result (nullable)
     */
    @Async
    public void logAuditAsync(String executionId, String userId, String scenarioCode,
                              Instant requestTime, Instant responseTime, boolean success,
                              String errorMessage, IntentResult intent, ScenarioResult result) {
        try {
            AiAuditLog auditLog = AiAuditLog.builder()
                    .executionId(executionId)
                    .userId(userId)
                    .scenarioCode(scenarioCode)
                    .requestTime(requestTime)
                    .responseTime(responseTime)
                    .success(success)
                    .errorMessage(errorMessage)
                    .rawIntentJson(intent != null ? objectMapper.writeValueAsString(intent) : null)
                    .rawResultJson(result != null ? objectMapper.writeValueAsString(result) : null)
                    .build();
            
            auditLogRepository.save(auditLog);
            log.debug("Audit log saved: executionId={}, success={}", executionId, success);
        } catch (Exception e) {
            // NEVER fail the main request due to audit logging failure
            log.error("Failed to save audit log for executionId={}: {}", executionId, e.getMessage());
        }
    }

    /**
     * Log authorization failure audit.
     */
    @Async
    public void logAuthorizationFailure(String executionId, String userId, String scenarioCode,
                                         String roles) {
        try {
            AiAuditLog auditLog = AiAuditLog.builder()
                    .executionId(executionId)
                    .userId(userId)
                    .scenarioCode(scenarioCode)
                    .requestTime(Instant.now())
                    .responseTime(Instant.now())
                    .success(false)
                    .errorMessage("Authorization denied for roles: " + roles)
                    .build();
            
            auditLogRepository.save(auditLog);
            log.warn("Authorization failure logged: user={}, scenario={}, roles={}", 
                    userId, scenarioCode, roles);
        } catch (Exception e) {
            log.error("Failed to save authorization failure audit: {}", e.getMessage());
        }
    }

    /**
     * Log security violation audit.
     */
    @Async
    public void logSecurityViolation(String executionId, String userId, String violationType,
                                      String details) {
        try {
            AiAuditLog auditLog = AiAuditLog.builder()
                    .executionId(executionId)
                    .userId(userId)
                    .requestTime(Instant.now())
                    .responseTime(Instant.now())
                    .success(false)
                    .errorMessage("SECURITY VIOLATION: " + violationType + " - " + details)
                    .build();
            
            auditLogRepository.save(auditLog);
            log.error("SECURITY VIOLATION logged: user={}, type={}", userId, violationType);
        } catch (Exception e) {
            log.error("Failed to save security violation audit: {}", e.getMessage());
        }
    }
}
