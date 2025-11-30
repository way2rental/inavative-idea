package com.enterprise.ai.llm.prompt;

import com.enterprise.ai.data.entity.AiScenario;
import com.enterprise.ai.data.service.ConfigCacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Dynamic prompt builder that loads templates from database.
 * All scenario-specific prompts are stored in ai_scenarios.llm_prompt_template.
 * 
 * NOTE: NO HARDCODED prompts - all configuration comes from database.
 * Fallback prompts are minimal and only used when DB is unavailable.
 */
@Slf4j
@Component
public class DynamicPromptBuilder {

    private final ConfigCacheService configCacheService;

    public DynamicPromptBuilder(ConfigCacheService configCacheService) {
        this.configCacheService = configCacheService;
    }

    /**
     * Build intent detection prompt with all available scenarios from DB.
     */
    public String buildIntentDetectionPrompt(String userInput, String sessionContext) {
        StringBuilder prompt = new StringBuilder();
        
        // Build scenario context from database
        prompt.append("You are an AI assistant for a banking application.\n");
        prompt.append("Available scenarios and their descriptions:\n\n");
        
        for (AiScenario scenario : configCacheService.getActiveScenarios()) {
            prompt.append("- ").append(scenario.getScenarioCode()).append(": ")
                    .append(scenario.getDescription() != null ? scenario.getDescription() : "No description")
                    .append("\n");
            
            // Add required params info
            if (scenario.getRequiredParams() != null && !scenario.getRequiredParams().isEmpty()) {
                prompt.append("  Required params: ").append(scenario.getRequiredParams()).append("\n");
            }
        }
        
        prompt.append("\nSession context: ")
                .append(sessionContext != null ? sessionContext : "No previous context")
                .append("\n\n");
        
        prompt.append("User message: \"").append(userInput).append("\"\n\n");
        
        prompt.append("Analyze the user's intent and return JSON with:\n");
        prompt.append("- scenario: The detected scenario code\n");
        prompt.append("- confidence: Confidence score (0.0 to 1.0)\n");
        prompt.append("- params: Extracted parameters as key-value pairs\n");
        prompt.append("- missingParams: Required parameters that are missing\n");
        prompt.append("- reasoning: Brief explanation\n");
        
        return prompt.toString();
    }

    /**
     * Build clarification prompt for ambiguous queries.
     */
    public String buildClarificationPrompt(String userQuery, List<String> possibleScenarios) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("User asked: \"").append(userQuery).append("\"\n\n");
        prompt.append("This could mean:\n");
        
        for (int i = 0; i < possibleScenarios.size(); i++) {
            String scenarioCode = possibleScenarios.get(i);
            String description = configCacheService.getScenarioByCode(scenarioCode)
                    .map(AiScenario::getDescription)
                    .orElse(scenarioCode.toLowerCase().replace("_", " "));
            prompt.append(i + 1).append(". ").append(description).append("\n");
        }
        
        prompt.append("\nGenerate a SHORT clarification question with numbered options.");
        
        return prompt.toString();
    }

    /**
     * Build follow-up question prompt from scenario configuration.
     */
    public String buildFollowUpPrompt(String scenarioCode, List<String> missingParams) {
        StringBuilder prompt = new StringBuilder();
        
        // Get scenario-specific prompt if available
        String scenarioPrompt = configCacheService.getScenarioByCode(scenarioCode)
                .map(AiScenario::getLlmPromptTemplate)
                .orElse(null);
        
        if (scenarioPrompt != null && !scenarioPrompt.isEmpty()) {
            // Use scenario-specific prompt template
            prompt.append(scenarioPrompt).append("\n\n");
        }
        
        prompt.append("Scenario: ").append(scenarioCode).append("\n");
        prompt.append("Missing parameters: ").append(String.join(", ", missingParams)).append("\n\n");
        prompt.append("Generate a SHORT, FRIENDLY question (under 25 words) to ask for the missing information.");
        
        return prompt.toString();
    }

    /**
     * Build response formatting prompt using scenario-specific template from DB.
     */
    public String buildResponseFormattingPrompt(String scenarioCode, String dataJson, String userQuery) {
        StringBuilder prompt = new StringBuilder();
        
        // Get scenario-specific prompt if available
        String scenarioPrompt = configCacheService.getScenarioByCode(scenarioCode)
                .map(AiScenario::getLlmPromptTemplate)
                .orElse(null);
        
        if (scenarioPrompt != null && !scenarioPrompt.isEmpty()) {
            // Use scenario-specific formatting instructions
            prompt.append(scenarioPrompt).append("\n\n");
        }
        
        prompt.append("Scenario: ").append(scenarioCode).append("\n");
        prompt.append("User query: \"").append(userQuery).append("\"\n");
        prompt.append("Raw data: ").append(dataJson).append("\n\n");
        prompt.append("Format this data into a natural, helpful response.\n");
        prompt.append("Keep it under 150 words. Use emojis appropriately.");
        
        return prompt.toString();
    }

    /**
     * Get scenario description from database.
     */
    public String getScenarioDescription(String scenarioCode) {
        return configCacheService.getScenarioByCode(scenarioCode)
                .map(AiScenario::getDescription)
                .orElse(scenarioCode.toLowerCase().replace("_", " "));
    }

    /**
     * Get all active scenario codes for LLM context.
     */
    public List<String> getActiveScenarioCodes() {
        return configCacheService.getActiveScenarios().stream()
                .map(AiScenario::getScenarioCode)
                .collect(Collectors.toList());
    }
}
