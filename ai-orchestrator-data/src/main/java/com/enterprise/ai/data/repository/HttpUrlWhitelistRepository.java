package com.enterprise.ai.data.repository;

import com.enterprise.ai.data.entity.HttpUrlWhitelist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for HTTP URL whitelist patterns.
 */
@Repository
public interface HttpUrlWhitelistRepository extends JpaRepository<HttpUrlWhitelist, Long> {

    /**
     * Find all active URL patterns.
     */
    List<HttpUrlWhitelist> findByActiveTrue();

    /**
     * Find by URL pattern.
     */
    Optional<HttpUrlWhitelist> findByUrlPattern(String urlPattern);

    /**
     * Check if a URL pattern exists.
     */
    boolean existsByUrlPattern(String urlPattern);
}
