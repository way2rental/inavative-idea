package com.enterprise.ai.llm.client;

import com.enterprise.ai.common.dto.IntentResult;
import com.enterprise.ai.common.dto.ScenarioResult;

import java.util.List;

/**
 * Interface for LLM client abstraction.
 * Allows switching between different LLM providers (Ollama, OpenAI, etc.).
 */
public interface LlmClient {

    /**
     * Detect intent from user input.
     * @param userInput user's query
     * @param sessionContext conversation context
     * @return intent detection result
     */
    IntentResult detectIntent(String userInput, String sessionContext);

    /**
     * Generate a follow-up question for missing parameters.
     * @param scenarioCode scenario code
     * @param missingParams list of missing parameters
     * @return follow-up question text
     */
    String generateFollowUpQuestion(String scenarioCode, List<String> missingParams);

    /**
     * Format the scenario result into natural language response.
     * @param scenarioCode scenario code
     * @param result scenario execution result
     * @param userQuery original user query
     * @return formatted response
     */
    String formatResponse(String scenarioCode, ScenarioResult result, String userQuery);
}
