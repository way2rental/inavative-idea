package com.enterprise.ai.data.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entity for scenario registry with dynamic execution configuration.
 * Supports DB_QUERY, HTTP_CALL, FILE_READ, and KAFKA_CONSUME execution types.
 * All operations are READ-ONLY by design for enterprise security compliance.
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "ai_scenarios")
public class AiScenario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "scenario_code", unique = true, length = 100)
    private String scenarioCode;

    @Column(length = 255)
    private String description;

    /**
     * Execution type: DB_QUERY, HTTP_CALL, FILE_READ, KAFKA_CONSUME
     * Determines which dynamic executor handles this scenario.
     */
    @Column(name = "execution_type", length = 50)
    @Builder.Default
    private String executionType = "DB_QUERY";

    /**
     * Database key for multi-datasource routing.
     * Must match a registered datasource in DataSourceRegistryService.
     * Examples: internal, retail, pfms, upi, wallet, cbs
     */
    @Column(name = "db_key", length = 50)
    @Builder.Default
    private String dbKey = "internal";

    /**
     * HTTP method for HTTP_CALL type (GET, POST only - read-only enforcement).
     */
    @Column(name = "http_method", length = 10)
    private String httpMethod;

    /**
     * HTTP URL template with placeholders (e.g., /api/txn/{txnId}).
     * Must match URL whitelist for security.
     */
    @Column(name = "http_url", length = 500)
    private String httpUrl;

    /**
     * HTTP headers as JSON (e.g., {"Content-Type": "application/json"}).
     */
    @Column(name = "http_headers", columnDefinition = "JSON")
    private String httpHeaders;

    /**
     * SQL query for DB_QUERY type (SELECT only - read-only enforcement).
     * Uses named parameters (e.g., :accountId).
     */
    @Column(name = "sql_query", columnDefinition = "TEXT")
    private String sqlQuery;

    /**
     * JSON mapping from intent params to request params.
     * Uses JSONPath syntax (e.g., {"txnId": "$.params.txnId"}).
     */
    @Column(name = "request_mapping", columnDefinition = "JSON")
    private String requestMapping;

    /**
     * JSON mapping from raw response to formatted output.
     * Uses JSONPath syntax for field extraction.
     */
    @Column(name = "response_mapping", columnDefinition = "JSON")
    private String responseMapping;

    /**
     * Execution timeout in milliseconds.
     */
    @Column(name = "timeout_ms")
    @Builder.Default
    private Integer timeoutMs = 5000;

    @Column(name = "executor_bean", length = 255)
    private String executorBean;

    @Column(name = "security_level", length = 50)
    private String securityLevel;

    @Column(name = "required_params", columnDefinition = "JSON")
    private String requiredParams;

    @Column(name = "optional_params", columnDefinition = "JSON")
    private String optionalParams;

    @Column(name = "llm_prompt_template", columnDefinition = "TEXT")
    private String llmPromptTemplate;

    /**
     * Prompt version for rollback support.
     */
    @Column(name = "prompt_version")
    @Builder.Default
    private Integer promptVersion = 1;

    /**
     * History of previous prompt versions as JSON array.
     */
    @Column(name = "prompt_history", columnDefinition = "JSON")
    private String promptHistory;

    @Column(name = "active")
    @Builder.Default
    private Boolean active = true;
}
