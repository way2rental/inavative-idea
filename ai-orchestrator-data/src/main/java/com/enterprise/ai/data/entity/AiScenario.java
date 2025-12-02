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
 * 
 * MULTI-FILTER ENGINE DESIGN:
 * - baseQuery: The core SELECT query without WHERE clause filters
 * - filterDefinitions: JSON defining all available filters with mandatory/optional flags
 * - securityFilters: JSON defining RLS filters (userId, orgId based)
 * 
 * AI extracts filter values → Backend builds dynamic WHERE clause → Database executes
 * AI NEVER generates business data - only extracts filters and formats responses.
 * 
 * CLEANUP: Removed unused fields (dbKey, httpHeaders, executorBean, 
 *          securityLevel, optionalParams, promptVersion, promptHistory)
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
     * SQL query for DB_QUERY type (SELECT only - read-only enforcement).
     * Uses named parameters (e.g., :accountId).
     * 
     * For MULTI-FILTER ENGINE: This is the BASE query.
     * Dynamic WHERE clauses are appended by FilterEngine based on extracted filters.
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

    /**
     * Required parameters (JSON array of strings).
     * DEPRECATED: Use filterDefinitions with mandatory=true instead.
     * Kept for backward compatibility.
     */
    @Column(name = "required_params", columnDefinition = "JSON")
    private String requiredParams;

    @Column(name = "llm_prompt_template", columnDefinition = "TEXT")
    private String llmPromptTemplate;

    @Column(name = "active")
    @Builder.Default
    private Boolean active = true;

    // =====================================================================
    // MULTI-FILTER ENGINE FIELDS
    // =====================================================================

    /**
     * JSON array defining all available filters for this scenario.
     * Each filter object contains:
     * - name: Filter parameter name (e.g., "accountId", "dateFrom", "minAmount")
     * - displayName: Human-readable name for prompts (e.g., "Account ID")
     * - description: Description for AI context (e.g., "Bank account identifier starting with ACC")
     * - type: Data type - STRING, NUMBER, DECIMAL, DATE, DATETIME, BOOLEAN, ENUM
     * - dbColumn: Database column name for WHERE clause (e.g., "account_id")
     * - operator: SQL operator - =, !=, <, >, <=, >=, LIKE, IN, BETWEEN
     * - mandatory: Boolean - if true, user MUST provide this filter
     * - defaultValue: Default value if not provided (for non-mandatory filters)
     * - validationPattern: Regex for validation (e.g., "^ACC[0-9]+$")
     * - validationError: Error message on validation failure
     * - enumValues: Array of allowed values for ENUM type
     * 
     * Example:
     * [
     *   {"name": "accountId", "displayName": "Account ID", "type": "STRING", 
     *    "dbColumn": "account_id", "operator": "=", "mandatory": true,
     *    "validationPattern": "^ACC[0-9]+$", "validationError": "Account ID must start with ACC"},
     *   {"name": "dateFrom", "displayName": "Start Date", "type": "DATE",
     *    "dbColumn": "transaction_date", "operator": ">=", "mandatory": false},
     *   {"name": "transactionType", "displayName": "Transaction Type", "type": "ENUM",
     *    "dbColumn": "txn_type", "operator": "=", "mandatory": false,
     *    "enumValues": ["CREDIT", "DEBIT", "TRANSFER"]}
     * ]
     */
    @Column(name = "filter_definitions", columnDefinition = "JSON")
    private String filterDefinitions;

    /**
     * JSON object defining security-level filters applied automatically.
     * These filters are ALWAYS applied and cannot be overridden by user.
     * Used for Row-Level Security (RLS) enforcement.
     * 
     * Structure:
     * {
     *   "userLevel": {"dbColumn": "user_id", "contextKey": "userId"},
     *   "orgLevel": {"dbColumn": "org_id", "contextKey": "orgId"},
     *   "tenantLevel": {"dbColumn": "tenant_id", "contextKey": "tenantId"}
     * }
     * 
     * If not specified, default RLS from RowLevelSecurityService is applied.
     */
    @Column(name = "security_filters", columnDefinition = "JSON")
    private String securityFilters;

    /**
     * Maximum number of results to return (pagination).
     * Prevents memory exhaustion from large result sets.
     */
    @Column(name = "max_results")
    @Builder.Default
    private Integer maxResults = 100;

    /**
     * Default sort order for results (e.g., "transaction_date DESC").
     */
    @Column(name = "default_sort", length = 255)
    private String defaultSort;

    // =====================================================================
    // HELPER METHODS
    // =====================================================================

    /**
     * Check if this scenario uses the multi-filter engine.
     * True if filterDefinitions is configured.
     */
    public boolean usesFilterEngine() {
        return filterDefinitions != null && !filterDefinitions.isBlank();
    }

    /**
     * Check if this scenario has custom security filters.
     */
    public boolean hasCustomSecurityFilters() {
        return securityFilters != null && !securityFilters.isBlank();
    }
}
