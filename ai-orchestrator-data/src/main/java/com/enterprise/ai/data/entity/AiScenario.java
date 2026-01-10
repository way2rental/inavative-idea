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
 * NO HARDCODING: All AI-related configuration comes from database.
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

    /**
     * Unique scenario code (e.g., ACCOUNT_BALANCE, TRANSACTION_HISTORY).
     */
    @Column(name = "scenario_code", unique = true, nullable = false, length = 100)
    private String scenarioCode;

    /**
     * Human-readable name for display.
     */
    @Column(name = "scenario_name", length = 255)
    private String scenarioName;

    /**
     * Description for AI context and admin display.
     */
    @Column(length = 500)
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
     * HTTP headers as JSON (e.g., {"Authorization": "Bearer {token}"})
     */
    @Column(name = "http_headers", columnDefinition = "TEXT")
    private String httpHeaders;

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
     * Used for backward compatibility and quick reference.
     */
    @Column(name = "required_params", columnDefinition = "JSON")
    private String requiredParams;

    /**
     * LLM prompt template for formatting responses.
     * Can reference scenario variables with {{variableName}}.
     */
    @Column(name = "llm_prompt_template", columnDefinition = "TEXT")
    private String llmPromptTemplate;

    /**
     * Is this scenario active and usable?
     */
    @Column(name = "active")
    @Builder.Default
    private Boolean active = true;

    // =====================================================================
    // AI INTENT DETECTION FIELDS (NO HARDCODING)
    // =====================================================================

    /**
     * Trigger phrases that help AI detect this scenario.
     * JSON array of phrases (e.g., ["check balance", "how much money", "account balance"])
     * Previously hardcoded in DynamicPromptBuilder - now from DB.
     */
    @Column(name = "trigger_phrases", columnDefinition = "JSON")
    private String triggerPhrases;

    /**
     * Example user queries for AI training/context.
     * JSON array (e.g., ["What's my balance?", "Show me my account balance for ACC001"])
     */
    @Column(name = "example_queries", columnDefinition = "JSON")
    private String exampleQueries;

    /**
     * Category for grouping (e.g., "Account", "Transaction", "Payment", "Loan")
     */
    @Column(name = "category", length = 100)
    private String category;

    /**
     * Display order within category.
     */
    @Column(name = "display_order")
    @Builder.Default
    private Integer displayOrder = 0;

    /**
     * Icon name for UI display (e.g., "wallet", "credit-card", "receipt")
     */
    @Column(name = "icon", length = 50)
    private String icon;

    // =====================================================================
    // MULTI-FILTER ENGINE FIELDS
    // =====================================================================

    /**
     * JSON array defining all available filters for this scenario.
     * Each filter object contains:
     * - name: Filter parameter name (e.g., "accountId", "dateFrom", "minAmount")
     * - displayName: Human-readable name for prompts (e.g., "Account ID")
     * - description: Description for AI context
     * - type: Data type - STRING, NUMBER, DECIMAL, DATE, DATETIME, BOOLEAN, ENUM
     * - dbColumn: Database column name for WHERE clause (e.g., "account_id")
     * - operator: SQL operator - =, !=, <, >, <=, >=, LIKE, IN, BETWEEN
     * - mandatory: Boolean - if true, user MUST provide this filter
     * - defaultValue: Default value if not provided
     * - validationPattern: Regex for validation
     * - validationError: Error message on validation failure
     * - enumValues: Array of allowed values for ENUM type
     */
    @Column(name = "filter_definitions", columnDefinition = "JSON")
    private String filterDefinitions;

    /**
     * JSON object defining security-level filters applied automatically.
     * Used for Row-Level Security (RLS) enforcement.
     */
    @Column(name = "security_filters", columnDefinition = "JSON")
    private String securityFilters;

    /**
     * Maximum number of results to return (pagination).
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

    /**
     * Check if this scenario has trigger phrases configured.
     */
    public boolean hasTriggerPhrases() {
        return triggerPhrases != null && !triggerPhrases.isBlank();
    }
}
