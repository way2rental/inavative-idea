package com.enterprise.ai.data.repository;

import com.enterprise.ai.data.entity.BankingConcept;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for BankingConcept entity operations.
 */
@Repository
public interface BankingConceptRepository extends JpaRepository<BankingConcept, Long> {

    /**
     * Find concept by concept code.
     */
    Optional<BankingConcept> findByConceptCode(String conceptCode);

    /**
     * Find all active concepts, ordered by priority (descending).
     */
    @Query("SELECT c FROM BankingConcept c WHERE c.active = true ORDER BY c.priority DESC")
    List<BankingConcept> findAllActiveOrderByPriorityDesc();

    /**
     * Find concepts by parent concept code (for hierarchies).
     */
    List<BankingConcept> findByParentConceptCodeAndActiveTrue(String parentConceptCode);

    /**
     * Find concepts by concept type.
     */
    List<BankingConcept> findByConceptTypeAndActiveTrue(String conceptType);

    /**
     * Find concepts by concept type, ordered by priority.
     */
    @Query("SELECT c FROM BankingConcept c WHERE c.conceptType = ?1 AND c.active = true ORDER BY c.priority DESC")
    List<BankingConcept> findByConceptTypeAndActiveTrueOrderByPriorityDesc(String conceptType);

    /**
     * Check if concept exists by concept code.
     */
    boolean existsByConceptCode(String conceptCode);
}
