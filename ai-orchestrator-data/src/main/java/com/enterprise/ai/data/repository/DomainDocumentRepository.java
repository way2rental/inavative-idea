package com.enterprise.ai.data.repository;

import com.enterprise.ai.data.entity.DomainDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for DomainDocument entity operations.
 */
@Repository
public interface DomainDocumentRepository extends JpaRepository<DomainDocument, Long> {

    /**
     * Find all active documents
     */
    List<DomainDocument> findByActiveTrue();

    /**
     * Find active documents by category
     */
    List<DomainDocument> findByCategoryAndActiveTrue(String category);

    /**
     * Find active documents by category, ordered by priority (descending)
     */
    @Query("SELECT d FROM DomainDocument d WHERE d.category = ?1 AND d.active = true ORDER BY d.priority DESC, d.updatedAt DESC")
    List<DomainDocument> findByCategoryAndActiveTrueOrderByPriorityDesc(String category);

    /**
     * Find active documents, ordered by priority (descending)
     */
    @Query("SELECT d FROM DomainDocument d WHERE d.active = true ORDER BY d.priority DESC, d.updatedAt DESC")
    List<DomainDocument> findAllActiveOrderByPriorityDesc();
}
