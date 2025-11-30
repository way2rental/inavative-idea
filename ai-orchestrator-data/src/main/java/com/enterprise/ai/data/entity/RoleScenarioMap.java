package com.enterprise.ai.data.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entity for role-scenario mapping.
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "role_scenario_map",
       uniqueConstraints = @UniqueConstraint(columnNames = {"role_name", "scenario_code"}))
public class RoleScenarioMap {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "role_name", length = 100)
    private String roleName;

    @Column(name = "scenario_code", length = 100)
    private String scenarioCode;
}
