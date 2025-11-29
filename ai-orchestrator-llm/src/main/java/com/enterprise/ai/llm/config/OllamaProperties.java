package com.enterprise.ai.llm.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for Ollama.
 */
@Data
@Component
@ConfigurationProperties(prefix = "ollama")
public class OllamaProperties {

    /**
     * Base URL for Ollama API.
     */
    private String baseUrl = "http://localhost:11434";

    /**
     * Model name to use.
     */
    private String model = "llama3:8b";

    /**
     * Request timeout in seconds.
     */
    private int timeoutSeconds = 60;

    /**
     * Enable/disable Ollama integration.
     */
    private boolean enabled = true;
}
