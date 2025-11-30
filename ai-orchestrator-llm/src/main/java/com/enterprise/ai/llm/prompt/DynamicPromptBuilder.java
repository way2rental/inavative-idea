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
    
    // Cache for intent detection prompt to avoid rebuilding on each call
    private volatile String cachedIntentPromptContext;
    private volatile long cacheTimestamp;
    private static final long CACHE_TTL_MS = 300000; // 5 minutes

    public DynamicPromptBuilder(ConfigCacheService configCacheService) {
        this.configCacheService = configCacheService;
    }

    /**
     * Build intent detection prompt with all available scenarios from DB.
     * Uses caching to avoid performance issues with 200+ scenarios.
     */
    public String buildIntentDetectionPrompt(String userInput, String sessionContext) {
        StringBuilder prompt = new StringBuilder();
        
        // Use cached scenario context (rebuilt every 5 mins or on cache refresh)
        String scenarioContext = getScenarioContext();
        
        prompt.append("You are an AI assistant for a banking application.\n");
        prompt.append("Available scenarios and their descriptions:\n\n");
        prompt.append(scenarioContext);
        
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
     * Get cached scenario context or rebuild if expired.
     */
    private String getScenarioContext() {
        long now = System.currentTimeMillis();
        if (cachedIntentPromptContext == null || (now - cacheTimestamp) > CACHE_TTL_MS) {
            synchronized (this) {
                if (cachedIntentPromptContext == null || (now - cacheTimestamp) > CACHE_TTL_MS) {
                    cachedIntentPromptContext = buildScenarioContext();
                    cacheTimestamp = now;
                    log.debug("Rebuilt scenario context cache with {} scenarios", 
                            configCacheService.getActiveScenarios().size());
                }
            }
        }
        return cachedIntentPromptContext;
    }

    /**
     * Build scenario context from active scenarios.
     */
    private String buildScenarioContext() {
        StringBuilder context = new StringBuilder();
        for (AiScenario scenario : configCacheService.getActiveScenarios()) {
            context.append("- ").append(scenario.getScenarioCode()).append(": ")
                    .append(scenario.getDescription() != null ? scenario.getDescription() : "No description")
                    .append("\n");
            
            // Add required params info
            if (scenario.getRequiredParams() != null && !scenario.getRequiredParams().isEmpty()) {
                context.append("  Required params: ").append(scenario.getRequiredParams()).append("\n");
            }
        }
        return context.toString();
    }

    /**
     * Invalidate the cached scenario context.
     * Should be called when scenarios are updated via admin panel.
     */
    public void invalidateCache() {
        cachedIntentPromptContext = null;
        log.info("Scenario context cache invalidated");
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
            String description = getScenarioDescription(scenarioCode);
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
     * Falls back to formatted scenario code if not found.
     */
    public String getScenarioDescription(String scenarioCode) {
        return configCacheService.getScenarioByCode(scenarioCode)
                .map(AiScenario::getDescription)
                .orElse(formatScenarioCodeAsDescription(scenarioCode));
    }

    /**
     * Format scenario code as human-readable description.
     * Example: "TXN_STATUS" -> "txn status"
     */
    private String formatScenarioCodeAsDescription(String scenarioCode) {
        return scenarioCode.toLowerCase().replace("_", " ");
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
