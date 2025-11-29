package com.enterprise.ai.core.config;

import com.enterprise.ai.core.scenario.DynamicExecutor;
import com.enterprise.ai.core.scenario.HttpCallExecutor;
import com.enterprise.ai.core.scenario.QueryExecutor;
import com.enterprise.ai.core.security.ReadOnlyEnforcementService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Configuration for dynamic scenario executors.
 * 
 * REPLACED: Hardcoded TxnStatusExecutor, FileStatusExecutor, AccountSummaryExecutor
 * WITH: QueryExecutor and HttpCallExecutor (config-driven)
 * 
 * This enables:
 * - 150+ scenarios with only 2 executor implementations
 * - No code changes when adding new scenarios
 * - DB-driven configuration
 */
@Configuration
public class ScenarioConfig {

    /**
     * QueryExecutor handles all DB_QUERY execution types.
     * SELECT queries only - read-only enforcement.
     */
    @Bean
    @Primary
    public DynamicExecutor queryExecutor(
            NamedParameterJdbcTemplate jdbcTemplate,
            ReadOnlyEnforcementService readOnlyEnforcement,
            ObjectMapper objectMapper) {
        return new QueryExecutor(jdbcTemplate, readOnlyEnforcement, objectMapper);
    }

    /**
     * HttpCallExecutor handles all HTTP_CALL execution types.
     * GET/POST only to whitelisted URLs - read-only enforcement.
     */
    @Bean
    public DynamicExecutor httpCallExecutor(
            WebClient.Builder webClientBuilder,
            ReadOnlyEnforcementService readOnlyEnforcement,
            ObjectMapper objectMapper) {
        return new HttpCallExecutor(webClientBuilder, readOnlyEnforcement, objectMapper);
    }

    // REMOVED: TxnStatusExecutor - now handled by HttpCallExecutor via config
    // REMOVED: FileStatusExecutor - now handled by HttpCallExecutor via config
    // REMOVED: AccountSummaryExecutor - now handled by QueryExecutor via config
}
