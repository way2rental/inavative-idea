package com.enterprise.ai.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST controller for LLM health and status endpoints.
 * Migrated to use Spring AI ChatModel.
 */
@Slf4j
@RestController
@RequestMapping("/api/ollama")
@RequiredArgsConstructor
@Tag(name = "LLM Health", description = "LLM provider health and status endpoints")
public class OllamaController {

    private final ChatModel chatModel;

    @Value("${spring.ai.active-provider:ollama}")
    private String activeProvider;

    @Value("${spring.ai.ollama.base-url:}")
    private String ollamaBaseUrl;

    @Value("${spring.ai.ollama.chat.options.model:mistral:7b}")
    private String configuredModel;

    @GetMapping("/health")
    @Operation(summary = "Check LLM provider health", description = "Check if the active LLM provider is healthy")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> healthDetails = new HashMap<>();

        try {
            // Test the ChatModel by calling it with a simple prompt
            String testResponse = chatModel.call("test");
            boolean healthy = testResponse != null;

            healthDetails.put("provider", activeProvider.toUpperCase());
            healthDetails.put("healthy", healthy);
            healthDetails.put("status", healthy ? "UP" : "DOWN");
            healthDetails.put("message", healthy ? "LLM provider is responding" : "LLM provider is not responding");

            if ("ollama".equalsIgnoreCase(activeProvider)) {
                healthDetails.put("baseUrl", ollamaBaseUrl);
                healthDetails.put("configuredModel", configuredModel);

                // Get available models from Ollama
                List<String> models = getOllamaModels();
                healthDetails.put("availableModels", models);
                healthDetails.put("modelAvailable", models.contains(configuredModel));
            }

            if (healthy) {
                return ResponseEntity.ok(healthDetails);
            } else {
                return ResponseEntity.status(503).body(healthDetails);
            }
        } catch (Exception e) {
            log.error("Health check failed: {}", e.getMessage());
            healthDetails.put("provider", activeProvider.toUpperCase());
            healthDetails.put("healthy", false);
            healthDetails.put("status", "DOWN");
            healthDetails.put("error", e.getMessage());
            return ResponseEntity.status(503).body(healthDetails);
        }
    }

    @GetMapping("/models")
    @Operation(summary = "List available models", description = "Get list of models available in the LLM provider")
    public ResponseEntity<Map<String, Object>> models() {
        Map<String, Object> response = new HashMap<>();
        response.put("provider", activeProvider.toUpperCase());

        if ("ollama".equalsIgnoreCase(activeProvider)) {
            List<String> models = getOllamaModels();
            response.put("models", models);
            response.put("configuredModel", configuredModel);
        } else {
            response.put("message", "Model listing only available for Ollama provider");
            response.put("configuredModel", "Check provider-specific configuration");
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Get available models from Ollama API.
     * Only works when provider is Ollama.
     */
    private List<String> getOllamaModels() {
        if (!"ollama".equalsIgnoreCase(activeProvider)) {
            return List.of();
        }

        try {
            WebClient webClient = WebClient.builder()
                    .baseUrl(ollamaBaseUrl)
                    .build();

            Map<String, Object> response = webClient.get()
                    .uri("/api/tags")
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(5))
                    .onErrorResume(e -> {
                        log.warn("Failed to fetch Ollama models: {}", e.getMessage());
                        return Mono.just(Map.of());
                    })
                    .block();

            if (response != null && response.containsKey("models")) {
                List<Map<String, Object>> modelsList = (List<Map<String, Object>>) response.get("models");
                List<String> modelNames = new ArrayList<>();
                for (Map<String, Object> model : modelsList) {
                    if (model.containsKey("name")) {
                        modelNames.add(model.get("name").toString());
                    }
                }
                return modelNames;
            }
        } catch (Exception e) {
            log.error("Error fetching Ollama models: {}", e.getMessage());
        }

        return List.of();
    }
}
