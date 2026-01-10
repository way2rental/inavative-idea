package com.enterprise.ai.data.repository;

import com.enterprise.ai.data.entity.SystemConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for SystemConfig entity operations.
 */
@Repository
public interface SystemConfigRepository extends JpaRepository<SystemConfig, Long> {

    /**
     * Find config by unique key
     */
    Optional<SystemConfig> findByConfigKey(String configKey);

    /**
     * Find config by key and tenant
     */
    Optional<SystemConfig> findByConfigKeyAndTenantId(String configKey, String tenantId);

    /**
     * Check if config key exists
     */
    boolean existsByConfigKey(String configKey);

    /**
     * Find all configs by category
     */
    List<SystemConfig> findByCategory(String category);

    /**
     * Find all visible configs
     */
    List<SystemConfig> findByVisibleTrue();

    /**
     * Find all editable configs
     */
    List<SystemConfig> findByEditableTrue();

    /**
     * Find configs by category and visibility
     */
    List<SystemConfig> findByCategoryAndVisibleTrue(String category);

    /**
     * Find all global configs (tenantId is null)
     */
    List<SystemConfig> findByTenantIdIsNull();

    /**
     * Find configs by tenant
     */
    List<SystemConfig> findByTenantId(String tenantId);

    /**
     * Find all configs ordered by category and key
     */
    List<SystemConfig> findAllByOrderByCategoryAscConfigKeyAsc();
}
