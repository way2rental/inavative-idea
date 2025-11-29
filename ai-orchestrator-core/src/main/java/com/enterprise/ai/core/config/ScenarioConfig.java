package com.enterprise.ai.core.config;

import com.enterprise.ai.core.client.BusinessDataClient;
import com.enterprise.ai.core.scenario.AccountSummaryExecutor;
import com.enterprise.ai.core.scenario.FileStatusExecutor;
import com.enterprise.ai.core.scenario.ScenarioExecutor;
import com.enterprise.ai.core.scenario.TxnStatusExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for scenario executors.
 * Executors are injected with BusinessDataClient for real API calls.
 */
@Configuration
public class ScenarioConfig {

    @Bean
    public ScenarioExecutor txnStatusExecutor(BusinessDataClient businessDataClient) {
        return new TxnStatusExecutor(businessDataClient);
    }

    @Bean
    public ScenarioExecutor fileStatusExecutor(BusinessDataClient businessDataClient) {
        return new FileStatusExecutor(businessDataClient);
    }

    @Bean
    public ScenarioExecutor accountSummaryExecutor(BusinessDataClient businessDataClient) {
        return new AccountSummaryExecutor(businessDataClient);
    }
}
