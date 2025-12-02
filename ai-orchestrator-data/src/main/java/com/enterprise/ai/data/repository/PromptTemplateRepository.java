package com.enterprise.ai.data.repository;

import com.enterprise.ai.data.entity.PromptTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for PromptTemplate entity operations.
 */
@Repository
public interface PromptTemplateRepository extends JpaRepository<PromptTemplate, Long> {

    /**
     * Find prompt by unique key
     */
    Optional<PromptTemplate> findByPromptKey(String promptKey);

    /**
     * Check if prompt key exists
     */
    boolean existsByPromptKey(String promptKey);

    /**
     * Find all enabled prompts
     */
    List<PromptTemplate> findByEnabledTrue();

    /**
     * Find prompts by category
     */
    List<PromptTemplate> findByCategory(String category);

    /**
     * Find enabled prompts by category
     */
    List<PromptTemplate> findByCategoryAndEnabledTrue(String category);

    /**
     * Find all prompts ordered by category and promptKey
     */
    List<PromptTemplate> findAllByOrderByCategoryAscPromptKeyAsc();
}
