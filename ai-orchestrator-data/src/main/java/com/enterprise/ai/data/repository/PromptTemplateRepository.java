package com.enterprise.ai.data.repository;

import com.enterprise.ai.data.entity.PromptTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for prompt template management.
 */
@Repository
public interface PromptTemplateRepository extends JpaRepository<PromptTemplate, Long> {

    /**
     * Find by prompt key
     */
    Optional<PromptTemplate> findByPromptKey(String promptKey);

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
     * Check if prompt exists by key
     */
    boolean existsByPromptKey(String promptKey);
}
