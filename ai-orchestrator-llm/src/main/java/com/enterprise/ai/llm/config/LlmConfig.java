package com.enterprise.ai.llm.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Configuration for LLM module.
 */
@Configuration
public class LlmConfig {

    @Bean
    public WebClient ollamaWebClient(OllamaProperties properties) {
        return WebClient.builder()
                .baseUrl(properties.getBaseUrl())
                .build();
    }
}
