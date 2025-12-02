package com.enterprise.ai.data.repository;

import com.enterprise.ai.data.entity.RoleScenarioMap;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for role-scenario mappings.
 */
@Repository
public interface RoleScenarioMapRepository extends JpaRepository<RoleScenarioMap, Long> {

    List<RoleScenarioMap> findByRoleName(String roleName);

    List<RoleScenarioMap> findByScenarioCode(String scenarioCode);

    Optional<RoleScenarioMap> findByRoleNameAndScenarioCode(String roleName, String scenarioCode);

    boolean existsByRoleNameAndScenarioCode(String roleName, String scenarioCode);

    /**
     * Delete all mappings for a role
     */
    void deleteByRoleName(String roleName);

    /**
     * Delete all mappings for a scenario
     */
    void deleteByScenarioCode(String scenarioCode);

    /**
     * Get distinct role names
     */
    @Query("SELECT DISTINCT r.roleName FROM RoleScenarioMap r ORDER BY r.roleName")
    List<String> findDistinctRoleNames();

    /**
     * Get distinct scenario codes
     */
    @Query("SELECT DISTINCT r.scenarioCode FROM RoleScenarioMap r ORDER BY r.scenarioCode")
    List<String> findDistinctScenarioCodes();
}
