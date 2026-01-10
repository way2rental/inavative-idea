package com.enterprise.ai.intelligence.service.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Represents an extracted entity match.
 * Contains the entity value, confidence, and metadata.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EntityMatch {

    /**
     * Entity type (e.g., "ACCOUNT_ID", "DATE", "AMOUNT")
     */
    private String entityType;

    /**
     * Extracted entity value
     */
    private String value;

    /**
     * Confidence score (0.0 to 1.0)
     */
    private BigDecimal confidence;

    /**
     * Pattern that matched (pattern ID or code)
     */
    private String matchedPattern;

    /**
     * Start position in query
     */
    private Integer startPosition;

    /**
     * End position in query
     */
    private Integer endPosition;

    /**
     * Additional metadata
     */
    private Map<String, Object> metadata;

    /**
     * Check if confidence is high enough
     */
    public boolean isConfident(BigDecimal threshold) {
        return confidence != null && confidence.compareTo(threshold) >= 0;
    }
}
