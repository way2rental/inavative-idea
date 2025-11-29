package com.enterprise.ai.data.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Entity for audit logs.
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "ai_audit_logs")
public class AiAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "execution_id", length = 100)
    private String executionId;

    @Column(name = "user_id", length = 100)
    private String userId;

    @Column(name = "scenario_code", length = 100)
    private String scenarioCode;

    @Column(name = "request_time")
    private Instant requestTime;

    @Column(name = "response_time")
    private Instant responseTime;

    @Column(name = "success")
    private Boolean success;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "raw_intent_json", columnDefinition = "JSON")
    private String rawIntentJson;

    @Column(name = "raw_result_json", columnDefinition = "JSON")
    private String rawResultJson;
}
