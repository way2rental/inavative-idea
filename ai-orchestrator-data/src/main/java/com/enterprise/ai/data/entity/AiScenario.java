package com.enterprise.ai.data.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entity for scenario registry.
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "ai_scenarios")
public class AiScenario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "scenario_code", unique = true, length = 100)
    private String scenarioCode;

    @Column(length = 255)
    private String description;

    @Column(name = "executor_bean", length = 255)
    private String executorBean;

    @Column(name = "security_level", length = 50)
    private String securityLevel;

    @Column(name = "required_params", columnDefinition = "JSON")
    private String requiredParams;

    @Column(name = "optional_params", columnDefinition = "JSON")
    private String optionalParams;

    @Column(name = "llm_prompt_template", columnDefinition = "TEXT")
    private String llmPromptTemplate;

    @Column(name = "active")
    private Boolean active = true;
}
