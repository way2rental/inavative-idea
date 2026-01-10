package com.enterprise.ai.data.repository;

import com.enterprise.ai.data.entity.MlModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for MlModel entity operations.
 */
@Repository
public interface MlModelRepository extends JpaRepository<MlModel, Long> {

    /**
     * Find model by type and version
     */
    Optional<MlModel> findByModelTypeAndModelVersion(String modelType, String modelVersion);

    /**
     * Find active models by type
     */
    List<MlModel> findByModelTypeAndActiveTrueAndEnabledTrue(String modelType);

    /**
     * Find active models by type, ordered by version (descending - newest first)
     */
    @Query("SELECT m FROM MlModel m WHERE m.modelType = ?1 AND m.active = true AND m.enabled = true ORDER BY m.modelVersion DESC")
    List<MlModel> findActiveByTypeOrderByVersionDesc(String modelType);

    /**
     * Find all active and enabled models
     */
    List<MlModel> findByActiveTrueAndEnabledTrue();

    /**
     * Find models by fallback layer
     */
    List<MlModel> findByFallbackLayer_LayerCode(String layerCode);

    /**
     * Find active models by fallback layer
     */
    List<MlModel> findByFallbackLayer_LayerCodeAndActiveTrueAndEnabledTrue(String layerCode);
}
