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
     * Default model name (fallback).
     */
    private String model = "llama3:8b";

    /**
     * Model for intent detection (enterprise-intent Modelfile).
     */
    private String intentModel = "enterprise-intent";

    /**
     * Model for response formatting (enterprise-formatter Modelfile).
     */
    private String formatterModel = "enterprise-formatter";

    /**
     * Request timeout in seconds.
     */
    private int timeoutSeconds = 120;

    /**
     * Enable/disable Ollama integration.
     */
    private boolean enabled = true;

    /**
     * Enable two-stage intent detection for scaling to 150+ scenarios.
     */
    private boolean twoStageDetection = false;

    /**
     * Enable streaming responses.
     */
    private boolean streamingEnabled = true;

    /**
     * Connection timeout in milliseconds.
     */
    private int connectionTimeoutMs = 5000;

    /**
     * Read timeout in milliseconds.
     */
    private int readTimeoutMs = 120000;

    /**
     * Write timeout in milliseconds.
     */
    private int writeTimeoutMs = 60000;
}
