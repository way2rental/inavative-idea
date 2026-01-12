package com.enterprise.ai.data.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Banking Concept Dictionary Entity.
 * 
 * Represents canonical banking concepts with synonyms and relationships.
 * Used for enhanced concept extraction in DLM (Domain Language Model).
 * 
 * Example:
 * - concept_code: "CREDIT_CARD"
 * - concept_name: "Credit Card"
 * - synonyms: ["CC", "credit card", "card"]
 * - parent_concept_code: null (top-level)
 * 
 * This enables semantic concept matching:
 * - "CC" → CREDIT_CARD
 * - "credit card balance" → CREDIT_CARD + ACCOUNT_BALANCE
 */
@Entity
@Table(name = "ai_concept_dictionary", indexes = {
    @Index(name = "idx_concept_code", columnList = "concept_code"),
    @Index(name = "idx_parent_concept", columnList = "parent_concept_code"),
    @Index(name = "idx_concept_type", columnList = "concept_type"),
    @Index(name = "idx_active_priority", columnList = "active, priority")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BankingConcept {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "concept_code", nullable = false, unique = true, length = 100)
    private String conceptCode;

    @Column(name = "concept_name", nullable = false, length = 200)
    private String conceptName;

    @Column(name = "parent_concept_code", length = 100)
    private String parentConceptCode;

    @Column(name = "concept_type", length = 50)
    private String conceptType; // ACCOUNT, TRANSACTION, CARD, LOAN, etc.

    @Column(name = "synonyms", columnDefinition = "TEXT")
    private String synonyms; // JSON array of synonyms

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "related_concepts", columnDefinition = "JSON")
    private String relatedConcepts; // JSON array of related concept codes

    @Column(name = "priority")
    @Builder.Default
    private Integer priority = 0;

    @Column(name = "active")
    @Builder.Default
    private Boolean active = true;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    /**
     * Get synonyms as list (parsed from JSON).
     */
    public List<String> getSynonymsList() {
        if (synonyms == null || synonyms.trim().isEmpty()) {
            return List.of();
        }
        try {
            // Simple JSON array parsing (can be enhanced with ObjectMapper if needed)
            String cleaned = synonyms.trim();
            if (cleaned.startsWith("[") && cleaned.endsWith("]")) {
                cleaned = cleaned.substring(1, cleaned.length() - 1);
                return List.of(cleaned.split(","))
                        .stream()
                        .map(s -> s.trim().replaceAll("^\"|\"$", ""))
                        .filter(s -> !s.isEmpty())
                        .toList();
            }
            return List.of();
        } catch (Exception e) {
            return List.of();
        }
    }

    /**
     * Check if a term matches this concept (concept code, name, or synonyms).
     */
    public boolean matches(String term) {
        if (term == null) {
            return false;
        }
        String termLower = term.toLowerCase().trim();
        
        // Match concept code
        if (conceptCode != null && conceptCode.equalsIgnoreCase(term)) {
            return true;
        }
        
        // Match concept name
        if (conceptName != null && conceptName.toLowerCase().equals(termLower)) {
            return true;
        }
        
        // Match synonyms
        List<String> synonymsList = getSynonymsList();
        for (String synonym : synonymsList) {
            if (synonym.toLowerCase().equals(termLower)) {
                return true;
            }
            // Also check if term contains synonym or vice versa (for multi-word)
            if (termLower.contains(synonym.toLowerCase()) || synonym.toLowerCase().contains(termLower)) {
                return true;
            }
        }
        
        return false;
    }
}
