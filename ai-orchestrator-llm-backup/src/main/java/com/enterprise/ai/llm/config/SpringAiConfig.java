package com.enterprise.ai.llm.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.util.Map;

/**
 * Spring AI Configuration.
 * Provides abstraction over different LLM providers (Ollama, OpenAI, etc.)
 */
@Configuration
public class SpringAiConfig {

    /**
     * Create a ChatClient by resolving the appropriate ChatModel bean at runtime.
     * We avoid direct single-bean injection because multiple ChatModel beans may exist
     * (e.g. `ollamaChatModel` and `openAiChatModel`).
     */
    @Bean
    public ChatClient chatClient(ApplicationContext ctx, Environment env) {
        String active = env.getProperty("spring.ai.active-provider", "ollama").toLowerCase();

        // Common auto-configured bean names used by Spring AI starters
        String targetBeanName = null;
        if (active.contains("open")) {
            targetBeanName = "openAiChatModel"; // auto-configured name for OpenAI model
        } else if (active.contains("ollama")) {
            targetBeanName = "ollamaChatModel"; // auto-configured name for Ollama model
        }

        // If we have a provider-specific bean name and it's available, use it
        if (targetBeanName != null && ctx.containsBean(targetBeanName)) {
            ChatModel model = ctx.getBean(targetBeanName, ChatModel.class);
            return ChatClient.builder(model).build();
        }

        // Otherwise fall back to any available ChatModel bean
        Map<String, ChatModel> beans = ctx.getBeansOfType(ChatModel.class);
        if (beans.isEmpty()) {
            throw new IllegalStateException("No ChatModel bean available on the classpath. Ensure a Spring AI starter is present and configured (e.g. openai or ollama).");
        }

        // If there is exactly one ChatModel, use it. Otherwise use the first one as a best-effort.
        ChatModel chosen = beans.values().iterator().next();
        return ChatClient.builder(chosen).build();
    }
}
