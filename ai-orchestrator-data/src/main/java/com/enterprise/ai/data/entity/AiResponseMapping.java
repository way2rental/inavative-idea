package com.enterprise.ai.data.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entity for JSON Path Response Mappings.
 * Per ENTERPRISE_AI_RESPONSE_MAPPING_AND_SSE_SPEC.md Section 4.1.
 * 
 * Maps raw DB/API fields to structured AI-friendly JSON fields.
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "ai_response_mappings")
public class AiResponseMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Associated scenario code (e.g., TXN_STATUS)
     */
    @Column(name = "scenario_code", nullable = false, length = 100)
    private String scenarioCode;

    /**
     * Source type: DB_QUERY or HTTP_CALL
     */
    @Column(name = "source_type", length = 50)
    @Builder.Default
    private String sourceType = "DB_QUERY";

    /**
     * Raw DB column or JSON API path name
     */
    @Column(name = "source_field", length = 255)
    private String sourceField;

    /**
     * Final structured JSON field name
     */
    @Column(name = "target_field", nullable = false, length = 255)
    private String targetField;

    /**
     * JSON Path expression for extraction
     */
    @Column(name = "json_path", nullable = false, length = 255)
    private String jsonPath;

    /**
     * Masking type: NONE, ACCOUNT, PAN, AADHAAR, CARD
     */
    @Column(name = "masking_type", length = 50)
    @Builder.Default
    private String maskingType = "NONE";

    /**
     * Display order for structured output
     */
    @Column(name = "display_order")
    @Builder.Default
    private Integer displayOrder = 0;

    /**
     * Whether this mapping is active
     */
    @Column(name = "active")
    @Builder.Default
    private Boolean active = true;
}
