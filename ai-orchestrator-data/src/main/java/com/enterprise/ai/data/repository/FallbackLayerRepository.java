package com.enterprise.ai.data.repository;

import com.enterprise.ai.data.entity.FallbackLayer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for FallbackLayer entity operations.
 */
@Repository
public interface FallbackLayerRepository extends JpaRepository<FallbackLayer, Long> {

    /**
     * Find layer by unique code
     */
    Optional<FallbackLayer> findByLayerCode(String layerCode);

    /**
     * Find all enabled layers ordered by priority (ascending - 1=first)
     */
    @Query("SELECT f FROM FallbackLayer f WHERE f.enabled = true ORDER BY f.priority ASC")
    List<FallbackLayer> findAllEnabledOrderByPriorityAsc();

    /**
     * Find layers by type
     */
    List<FallbackLayer> findByLayerType(String layerType);

    /**
     * Find enabled layers by type
     */
    List<FallbackLayer> findByLayerTypeAndEnabledTrue(String layerType);

    /**
     * Check if layer exists by code
     */
    boolean existsByLayerCode(String layerCode);
}
