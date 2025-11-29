package com.enterprise.ai.api.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Structured performance logging service for tracking execution times.
 * Logs intent detection, DB execution, formatting, and end-to-end times.
 */
@Slf4j
@Service
public class PerformanceLoggingService {

    // Metrics storage for analysis
    private final ConcurrentHashMap<String, ExecutionMetrics> metricsCache = new ConcurrentHashMap<>();

    /**
     * Start tracking an execution.
     *
     * @param executionId unique execution identifier
     * @return tracker for this execution
     */
    public ExecutionTracker startTracking(String executionId) {
        return new ExecutionTracker(executionId, this);
    }

    /**
     * Log complete execution metrics.
     */
    public void logMetrics(ExecutionMetrics metrics) {
        metricsCache.put(metrics.executionId, metrics);
        
        log.info("""
                
                ═══════════════════════════════════════════════════════════════
                PERFORMANCE METRICS - Execution: {}
                ═══════════════════════════════════════════════════════════════
                • Scenario:               {}
                • User:                   {}
                ───────────────────────────────────────────────────────────────
                • Intent Detection:       {} ms
                • Validation:             {} ms
                • DB/API Execution:       {} ms
                • LLM Formatting:         {} ms
                ───────────────────────────────────────────────────────────────
                • TOTAL END-TO-END:       {} ms
                • Status:                 {}
                ═══════════════════════════════════════════════════════════════
                """,
                metrics.executionId,
                metrics.scenario != null ? metrics.scenario : "N/A",
                metrics.userId != null ? metrics.userId : "anonymous",
                metrics.intentDetectionMs,
                metrics.validationMs,
                metrics.dbExecutionMs,
                metrics.formattingMs,
                metrics.totalMs,
                metrics.success ? "SUCCESS" : "FAILED"
        );

        // Warn if any phase exceeded thresholds
        if (metrics.intentDetectionMs > 5000) {
            log.warn("⚠️ SLOW INTENT DETECTION: {} ms (threshold: 5000ms)", metrics.intentDetectionMs);
        }
        if (metrics.dbExecutionMs > 3000) {
            log.warn("⚠️ SLOW DB EXECUTION: {} ms (threshold: 3000ms)", metrics.dbExecutionMs);
        }
        if (metrics.formattingMs > 5000) {
            log.warn("⚠️ SLOW FORMATTING: {} ms (threshold: 5000ms)", metrics.formattingMs);
        }
        if (metrics.totalMs > 15000) {
            log.warn("⚠️ SLOW END-TO-END: {} ms (threshold: 15000ms)", metrics.totalMs);
        }
    }

    /**
     * Get metrics for an execution.
     */
    public ExecutionMetrics getMetrics(String executionId) {
        return metricsCache.get(executionId);
    }

    /**
     * Get all recent metrics.
     */
    public Map<String, ExecutionMetrics> getAllMetrics() {
        return Map.copyOf(metricsCache);
    }

    /**
     * Clear metrics cache.
     */
    public void clearMetrics() {
        metricsCache.clear();
    }

    /**
     * Execution tracker for measuring individual phases.
     */
    public static class ExecutionTracker {
        private final String executionId;
        private final PerformanceLoggingService service;
        private final Instant startTime;
        
        private String scenario;
        private String userId;
        private boolean success = true;
        
        private Instant intentStart;
        private Instant intentEnd;
        private Instant validationStart;
        private Instant validationEnd;
        private Instant dbStart;
        private Instant dbEnd;
        private Instant formattingStart;
        private Instant formattingEnd;

        ExecutionTracker(String executionId, PerformanceLoggingService service) {
            this.executionId = executionId;
            this.service = service;
            this.startTime = Instant.now();
        }

        public ExecutionTracker withScenario(String scenario) {
            this.scenario = scenario;
            return this;
        }

        public ExecutionTracker withUserId(String userId) {
            this.userId = userId;
            return this;
        }

        public void startIntentDetection() {
            this.intentStart = Instant.now();
        }

        public void endIntentDetection() {
            this.intentEnd = Instant.now();
        }

        public void startValidation() {
            this.validationStart = Instant.now();
        }

        public void endValidation() {
            this.validationEnd = Instant.now();
        }

        public void startDbExecution() {
            this.dbStart = Instant.now();
        }

        public void endDbExecution() {
            this.dbEnd = Instant.now();
        }

        public void startFormatting() {
            this.formattingStart = Instant.now();
        }

        public void endFormatting() {
            this.formattingEnd = Instant.now();
        }

        public void markFailed() {
            this.success = false;
        }

        /**
         * Complete tracking and log metrics.
         */
        public void complete() {
            Instant endTime = Instant.now();
            
            ExecutionMetrics metrics = new ExecutionMetrics();
            metrics.executionId = executionId;
            metrics.scenario = scenario;
            metrics.userId = userId;
            metrics.success = success;
            
            metrics.intentDetectionMs = calculateDuration(intentStart, intentEnd);
            metrics.validationMs = calculateDuration(validationStart, validationEnd);
            metrics.dbExecutionMs = calculateDuration(dbStart, dbEnd);
            metrics.formattingMs = calculateDuration(formattingStart, formattingEnd);
            metrics.totalMs = Duration.between(startTime, endTime).toMillis();
            
            service.logMetrics(metrics);
        }

        private long calculateDuration(Instant start, Instant end) {
            if (start == null || end == null) {
                return 0;
            }
            return Duration.between(start, end).toMillis();
        }
    }

    /**
     * Container for execution metrics.
     */
    public static class ExecutionMetrics {
        public String executionId;
        public String scenario;
        public String userId;
        public boolean success;
        
        public long intentDetectionMs;
        public long validationMs;
        public long dbExecutionMs;
        public long formattingMs;
        public long totalMs;
    }
}
