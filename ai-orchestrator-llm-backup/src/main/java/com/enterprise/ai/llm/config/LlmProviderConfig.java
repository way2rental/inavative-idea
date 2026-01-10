package com.enterprise.ai.llm.config;

import com.enterprise.ai.common.dto.IntentResult;
import com.enterprise.ai.common.dto.ScenarioResult;
import com.enterprise.ai.llm.client.LlmClient;
import com.enterprise.ai.llm.client.ReactiveLlmClient;
import com.enterprise.ai.llm.client.SpringAiLlmClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.List;

/**
 * LLM Provider Configuration.
 * Automatically selects the appropriate LLM client based on configuration.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class LlmProviderConfig {

    private final SpringAiLlmClient springAiLlmClient;

    @Value("${spring.ai.active-provider:ollama}")
    private String activeProvider;

    /**
     * Primary LLM Client bean (Blocking adapter).
     * Wraps the reactive Spring AI client for backward compatibility.
     */
    @Bean
    @Primary
    public LlmClient llmClient() {
        log.info("🤖 Active LLM Provider: {}", activeProvider.toUpperCase());
        log.info("✅ Using Spring AI for provider abstraction");

        // Create blocking adapter
        return new LlmClient() {
            @Override
            public IntentResult detectIntent(String userInput, String sessionContext) {
                return springAiLlmClient.detectIntent(userInput, sessionContext).block();
            }

            @Override
            public String generateFollowUpQuestion(String scenarioCode, List<String> missingParams) {
                return springAiLlmClient.generateFollowUpQuestion(scenarioCode, missingParams).block();
            }

            @Override
            public String formatResponse(String scenarioCode, ScenarioResult result, String userQuery) {
                return springAiLlmClient.formatResponse(scenarioCode, result, userQuery).block();
            }
        };
    }

    /**
     * Primary Reactive LLM Client bean.
     */
    @Bean
    @Primary
    public ReactiveLlmClient reactiveLlmClient() {
        log.info("🔄 Reactive LLM Client configured with provider: {}", activeProvider.toUpperCase());
        return springAiLlmClient;
    }
}

