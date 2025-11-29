package com.enterprise.ai.data.repository;

import com.enterprise.ai.data.entity.RoleScenarioMap;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for role-scenario mappings.
 */
@Repository
public interface RoleScenarioMapRepository extends JpaRepository<RoleScenarioMap, Long> {

    List<RoleScenarioMap> findByRoleName(String roleName);

    List<RoleScenarioMap> findByScenarioCode(String scenarioCode);

    boolean existsByRoleNameAndScenarioCode(String roleName, String scenarioCode);
}
