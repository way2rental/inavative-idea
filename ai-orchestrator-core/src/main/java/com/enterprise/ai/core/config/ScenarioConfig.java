package com.enterprise.ai.core.config;

import com.enterprise.ai.core.scenario.AccountSummaryExecutor;
import com.enterprise.ai.core.scenario.FileStatusExecutor;
import com.enterprise.ai.core.scenario.ScenarioExecutor;
import com.enterprise.ai.core.scenario.TxnStatusExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for scenario executors.
 */
@Configuration
public class ScenarioConfig {

    @Bean
    public ScenarioExecutor txnStatusExecutor() {
        return new TxnStatusExecutor();
    }

    @Bean
    public ScenarioExecutor fileStatusExecutor() {
        return new FileStatusExecutor();
    }

    @Bean
    public ScenarioExecutor accountSummaryExecutor() {
        return new AccountSummaryExecutor();
    }
}
