package com.enterprise.ai.llm.client;

import com.enterprise.ai.llm.config.OllamaProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Health check service for Ollama LLM.
 * Provides connectivity and availability checks.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OllamaHealthService {

    private final WebClient ollamaWebClient;
    private final OllamaProperties properties;

    /**
     * Check if Ollama is reachable and healthy.
     */
    public boolean isHealthy() {
        if (!properties.isEnabled()) {
            log.debug("Ollama is disabled");
            return false;
        }

        try {
            String response = ollamaWebClient.get()
                    .uri("/api/tags")
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(5))
                    .onErrorResume(e -> {
                        log.warn("Ollama health check failed: {}", e.getMessage());
                        return Mono.empty();
                    })
                    .block();

            return response != null && !response.isEmpty();
        } catch (Exception e) {
            log.warn("Ollama health check error: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Get the list of available models from Ollama.
     */
    @SuppressWarnings("unchecked")
    public List<String> getAvailableModels() {
        try {
            Map<String, Object> response = ollamaWebClient.get()
                    .uri("/api/tags")
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();

            if (response != null && response.containsKey("models")) {
                List<Map<String, Object>> models = (List<Map<String, Object>>) response.get("models");
                return models.stream()
                        .map(m -> (String) m.get("name"))
                        .toList();
            }
            return List.of();
        } catch (Exception e) {
            log.error("Error fetching available models: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Check if the configured model is available.
     */
    public boolean isModelAvailable() {
        List<String> models = getAvailableModels();
        String configuredModel = properties.getModel();
        
        // Check for exact match or prefix match (e.g., "llama3:8b" matches "llama3:8b-instruct-q4_0")
        return models.stream().anyMatch(m -> 
                m.equals(configuredModel) || 
                m.startsWith(configuredModel.split(":")[0]));
    }

    /**
     * Get detailed health status.
     */
    public Map<String, Object> getHealthDetails() {
        boolean healthy = isHealthy();
        List<String> models = healthy ? getAvailableModels() : List.of();
        boolean modelAvailable = healthy && isModelAvailable();

        return Map.of(
                "enabled", properties.isEnabled(),
                "healthy", healthy,
                "baseUrl", properties.getBaseUrl(),
                "configuredModel", properties.getModel(),
                "modelAvailable", modelAvailable,
                "availableModels", models
        );
    }
}
