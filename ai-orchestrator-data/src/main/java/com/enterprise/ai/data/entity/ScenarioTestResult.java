package com.enterprise.ai.data.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Entity for scenario test results (sandbox tester).
 * Stores results of scenario test executions for debugging and validation.
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "scenario_test_results")
public class ScenarioTestResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "test_id", unique = true, nullable = false, length = 100)
    private String testId;

    @Column(name = "scenario_code", nullable = false, length = 100)
    private String scenarioCode;

    @Column(name = "test_params", columnDefinition = "JSON", nullable = false)
    private String testParams;

    @Column(name = "executor_type", length = 50)
    private String executorType;

    @Column(name = "request_built", columnDefinition = "JSON")
    private String requestBuilt;

    @Column(name = "response_result", columnDefinition = "JSON")
    private String responseResult;

    @Column(name = "success")
    private Boolean success;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "execution_time_ms")
    private Long executionTimeMs;

    @Column(name = "dry_run")
    @Builder.Default
    private Boolean dryRun = false;

    @Column(name = "created_at")
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
