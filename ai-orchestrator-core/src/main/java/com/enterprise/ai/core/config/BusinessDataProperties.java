package com.enterprise.ai.core.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Configuration properties for business data service endpoints.
 * Allows dynamic configuration of API URLs for each scenario.
 */
@Data
@Component
@ConfigurationProperties(prefix = "business-data")
public class BusinessDataProperties {

    /**
     * Base URL for the business data service.
     */
    private String baseUrl = "http://localhost:8081";

    /**
     * Request timeout in milliseconds.
     */
    private int timeoutMs = 5000;

    /**
     * Connection timeout in milliseconds.
     */
    private int connectionTimeoutMs = 2000;

    /**
     * Enable/disable mock mode (returns mock data instead of calling API).
     */
    private boolean mockMode = true;

    /**
     * Endpoint configurations per scenario.
     * Key: scenario code, Value: endpoint config
     */
    private Map<String, EndpointConfig> endpoints;

    @Data
    public static class EndpointConfig {
        /**
         * API endpoint path (appended to baseUrl).
         */
        private String path;

        /**
         * HTTP method (GET, POST, etc.).
         */
        private String method = "GET";

        /**
         * Custom timeout for this endpoint (overrides default).
         */
        private Integer timeoutMs;

        /**
         * Request headers to include.
         */
        private Map<String, String> headers;

        /**
         * Request body template (for POST requests).
         * Use placeholders like ${txnId} that will be replaced with actual params.
         */
        private String bodyTemplate;
    }
}
