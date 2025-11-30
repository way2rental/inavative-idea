package com.enterprise.ai.llm.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.ai.chat.model.ChatModel;

import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class SpringAiDiagnostics {

    private final Environment env;
    private final ApplicationContext ctx;

    @Value("${spring.ai.active-provider:ollama}")
    private String activeProvider;

    @PostConstruct
    public void check() {
        log.info("[Diagnostics] spring.ai.active-provider={}", activeProvider);

        // Check ChatModel bean
        Map<String, ChatModel> models = ctx.getBeansOfType(ChatModel.class);
        if (models.isEmpty()) {
            log.warn("[Diagnostics] No ChatModel bean found on the classpath. Check Spring AI starters and configuration.");
        } else {
            models.forEach((name, model) ->
                    log.info("[Diagnostics] ChatModel bean '{}' of type: {}", name, model.getClass().getName())
            );
        }

        // Check API key envs
        String openaiKey = Optional.ofNullable(env.getProperty("OPENAI_API_KEY"))
                .orElse(env.getProperty("OPEN_API_KEY"));
        if (openaiKey == null || openaiKey.isBlank()) {
            log.warn("[Diagnostics] OpenAI API key not found in env variables (OPENAI_API_KEY or OPEN_API_KEY).");
        } else {
            log.info("[Diagnostics] OpenAI API key found (length={}).", openaiKey.length());
        }
    }
}

