package com.enterprise.ai.data.repository;

import com.enterprise.ai.data.entity.RoleScenarioMap;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
