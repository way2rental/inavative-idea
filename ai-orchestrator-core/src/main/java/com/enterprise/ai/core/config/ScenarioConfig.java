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
 * NOTE: QueryExecutor and HttpCallExecutor are now annotated with @Component
 * and will be auto-discovered. This config is kept for backward compatibility
 * but the beans are created via component scanning.
 * 
 * This enables:
 * - 150+ scenarios with only 2 executor implementations
 * - No code changes when adding new scenarios
 * - DB-driven configuration
 * - MANDATORY row-level security (QueryExecutor)
 * - MANDATORY PII masking before AI (both executors)
 */
@Configuration
public class ScenarioConfig {

    // NOTE: QueryExecutor and HttpCallExecutor are now @Component beans
    // They are auto-wired with all required dependencies through constructor injection
    // No manual bean definition needed
}
