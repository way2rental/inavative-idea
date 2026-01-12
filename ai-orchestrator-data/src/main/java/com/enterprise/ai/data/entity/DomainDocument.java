package com.enterprise.ai.data.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Entity for domain documents used by RAG Engine.
 * Stores FAQs, Policies, Product definitions, SOPs.
 * 
 * NO HARDCODING - All domain knowledge from database.
 * Fully configurable and manageable from Admin Panel.
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "ai_domain_documents")
public class DomainDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Document title
     */
    @Column(name = "title", nullable = false, length = 500)
    private String title;

    /**
     * Document content
     */
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    /**
     * Category: FAQ, POLICY, PRODUCT, SOP
     */
    @Column(name = "category", nullable = false, length = 50)
    private String category;

    /**
     * Optional subcategory
     */
    @Column(name = "subcategory", length = 100)
    private String subcategory;

    /**
     * Semantic tags as JSON array for filtering
     * Example: ["credit-card", "billing", "statement"]
     */
    @Column(name = "tags", columnDefinition = "JSON")
    private String tags;

    /**
     * Pre-computed embedding vector as JSON array (384 dimensions)
     */
    @Column(name = "embedding_vector", columnDefinition = "JSON")
    private String embeddingVector;

    /**
     * Model used to generate embeddings
     */
    @Column(name = "embedding_model", length = 100)
    private String embeddingModel;

    /**
     * Document version
     */
    @Column(name = "document_version")
    @Builder.Default
    private Integer documentVersion = 1;

    /**
     * Optional source URL
     */
    @Column(name = "source_url", length = 1000)
    private String sourceUrl;

    /**
     * Optional author
     */
    @Column(name = "author", length = 255)
    private String author;

    /**
     * Retrieval priority (higher = more important)
     */
    @Column(name = "priority")
    @Builder.Default
    private Integer priority = 0;

    /**
     * Is this document active?
     */
    @Column(name = "active")
    @Builder.Default
    private Boolean active = true;

    @Column(name = "created_at")
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }
}
