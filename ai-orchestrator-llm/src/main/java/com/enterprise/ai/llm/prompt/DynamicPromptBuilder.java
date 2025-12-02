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
        return buildIntentDetectionPrompt(userInput, sessionContext, null);
    }

    /**
     * Build intent detection prompt with all available scenarios from DB.
     * Uses caching to avoid performance issues with 200+ scenarios.
     * Includes last used params for context resolution.
     */
    public String buildIntentDetectionPrompt(String userInput, String sessionContext, String lastUsedParamsJson) {
        StringBuilder prompt = new StringBuilder();
        
        // Use cached scenario context (rebuilt every 5 mins or on cache refresh)
        String scenarioContext = getScenarioContext();
        
        prompt.append("You are an AI assistant for a banking application.\n");
        prompt.append("Available scenarios and their descriptions:\n\n");
        prompt.append(scenarioContext);
        
        prompt.append("\nSession context (IMPORTANT - read this to understand conversation history):\n")
                .append(sessionContext != null ? sessionContext : "No previous context")
                .append("\n\n");

        // Add last used params context for reference resolution
        if (lastUsedParamsJson != null && !lastUsedParamsJson.isEmpty()) {
            prompt.append("RECENTLY USED PARAMETERS (use these to resolve references like 'same account', 'that account', 'the same one'):\n");
            prompt.append(lastUsedParamsJson).append("\n\n");
        }
        
        prompt.append("User message: \"").append(userInput).append("\"\n\n");
        
        prompt.append("Analyze the user's intent and return JSON with:\n");
        prompt.append("- scenario: The detected scenario code (or UNKNOWN if no match)\n");
        prompt.append("- confidence: Confidence score (0.0 to 1.0)\n");
        prompt.append("- params: Extracted parameters as key-value pairs\n");
        prompt.append("- missingParams: Required parameters that are missing\n");
        prompt.append("- reasoning: Brief explanation\n\n");

        prompt.append("IMPORTANT CONTEXT RULES:\n");
        prompt.append("- Return ONLY valid JSON, no markdown or extra text\n");
        prompt.append("- Read the session context CAREFULLY - if the assistant previously asked for a parameter (like accountId), and the user's current message looks like a value/answer, extract it as that parameter\n");
        prompt.append("- If user provides just a value like 'ACC001' or '12345' after being asked for an ID, that IS the parameter value\n");
        prompt.append("- Match user input to the closest scenario from the list above\n");
        prompt.append("- Extract any parameter values mentioned by the user\n");
        prompt.append("- **CRITICAL**: If user says 'same account', 'that account', 'this one', 'for the same', use the accountId from RECENTLY USED PARAMETERS\n");
        prompt.append("- **CRITICAL**: If a reference like 'same' or 'that' is used and we have recent params, REUSE them - don't mark as missing\n");
        prompt.append("- If no scenario matches well, use UNKNOWN with low confidence\n\n");

        prompt.append("Example JSON format:\n");
        prompt.append("{\n");
        prompt.append("  \"scenario\": \"ACCOUNT_BALANCE\",\n");
        prompt.append("  \"confidence\": 0.95,\n");
        prompt.append("  \"params\": {\"accountId\": \"123456\"},\n");
        prompt.append("  \"missingParams\": [],\n");
        prompt.append("  \"reasoning\": \"User wants to check account balance\"\n");
        prompt.append("}\n");

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
        List<AiScenario> scenarios = configCacheService.getActiveScenarios();

        if (scenarios.isEmpty()) {
            log.warn("No active scenarios found in configuration!");
            return "No scenarios configured.\n";
        }

        for (AiScenario scenario : scenarios) {
            context.append(scenario.getScenarioCode()).append(": ")
                    .append(scenario.getDescription() != null ? scenario.getDescription() : "No description");

            // Add required params info
            if (scenario.getRequiredParams() != null && !scenario.getRequiredParams().isEmpty()) {
                context.append(" (requires: ").append(scenario.getRequiredParams()).append(")");
            }
            context.append("\n");
        }

        log.debug("Built scenario context with {} scenarios", scenarios.size());
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
        prompt.append("Add Missing parameters as proper formatted in json missingParams field.");
        appendStatic(prompt);
        return prompt.toString();
    }

    private void appendStatic(StringBuilder prompt) {
        prompt.append("""
                
                You are a response formatting engine for a corporate banking system.
                
                You MUST return ONLY valid JSON.
                You MUST return exactly ONE object.
                You MUST choose exactly ONE type from this list:
                
                TEXT, BULLET, TABLE, KV, MIXED, FOLLOW_UP, ERROR
                
                Never return:
                - Markdown
                - Free-form text
                - Explanations outside JSON
                - Partial JSON
                - Multiple JSON objects
                
                Response Schema (STRICT):
                
                {
                  "type": "TEXT | BULLET | TABLE | KV | MIXED | FOLLOW_UP | ERROR",
                  "title": "Optional",
                  "confidence": 1.0,
                  "payload": {},
                  "footer": "Optional"
                }
                
                Rules:
                - If data is single info → use KV
                - If data is bulk rows → use TABLE
                - If user is missing required params → use FOLLOW_UP
                - If user is not authorized → use ERROR
                - If intent is unknown → use ERROR with suggestions
                - If answer is plain explanation → use TEXT
                - If answer is list of options → use BULLET
                - If answer needs text + table → use MIXED
                
                FOR TABLE TYPE - CRITICAL:
                The payload MUST have this EXACT structure (use "columns" NOT "headers"):
                {
                  "type": "TABLE",
                  "title": "Transaction History for ACC001",
                  "confidence": 1.0,
                  "payload": {
                    "columns": ["Date", "Description", "Amount"],
                    "rows": [
                      ["2024-01-15", "Salary", "5000.00"],
                      ["2024-01-14", "Shopping", "-150.00"]
                    ]
                  }
                }
                
                CRITICAL FIELD NAMES:
                - Use "columns" (NOT "headers") for the column headers array
                - Use "rows" for the data rows (array of arrays)
                - Each row must match the column count and order
                - Extract column names from the data object keys
                - Convert each data object into a row array matching the column order
                
                Return ONLY the JSON. No markdown. No commentary. No extra text.
                
                """);
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
        appendStatic(prompt);
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
