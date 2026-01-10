package com.enterprise.ai.intelligence.service.fallback;

import com.enterprise.ai.common.dto.IntentResult;
import com.enterprise.ai.data.entity.FallbackLayer;
import reactor.core.publisher.Mono;

import java.util.Set;

/**
 * Interface for fallback layer services.
 * Each layer implements this interface to provide intent detection.
 * 
 * NO HARDCODING - All configuration comes from database.
 */
public interface FallbackLayerService {

    /**
     * Get the layer code this service handles
     */
    String getLayerCode();

    /**
     * Check if this service is enabled and ready
     */
    boolean isEnabled();

    /**
     * Detect intent using this layer.
     * Returns empty Optional if layer cannot provide confident result (fallback to next layer).
     * 
     * @param userInput user query
     * @param sessionContext conversation history
     * @param lastUsedParamsJson last used parameters for reference resolution
     * @param allowedScenarios scenarios user can access (RBAC filtering)
     * @param layerConfig fallback layer configuration from database
     * @return Mono of Optional IntentResult (empty = fallback to next layer)
     */
    Mono<java.util.Optional<IntentResult>> detectIntent(
            String userInput,
            String sessionContext,
            String lastUsedParamsJson,
            Set<String> allowedScenarios,
            FallbackLayer layerConfig
    );

    /**
     * Check if this layer is healthy and ready
     */
    Mono<Boolean> isHealthy();
}
