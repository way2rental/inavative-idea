package com.enterprise.ai.llm.prompt;

import java.util.List;

/**
 * Prompt templates for LLM interactions.
 */
public final class PromptTemplates {

    private PromptTemplates() {
        // Utility class
    }

    /**
     * Build prompt for intent detection.
     */
    public static String buildIntentDetectionPrompt(String userInput, String sessionContext) {
        return """
                You are an intent detection engine for an enterprise system.
                You MUST output a strict JSON object with this schema:
                {
                  "scenario": "string",
                  "confidence": number,
                  "params": { },
                  "missingParams": []
                }
                
                Available scenarios:
                1. TXN_STATUS - Check the status of a transaction by its transaction ID.
                   Required params: ["txnId"]
                2. FILE_STATUS - Check file processing status by fileName and/or date.
                   Required params: ["fileName"]
                3. ACCOUNT_SUMMARY - Show account summary.
                   Required params: ["accountId"]
                
                Session context:
                %s
                
                User message:
                "%s"
                
                Now detect the best scenario and extract parameters.
                If any required parameter is missing, include it in "missingParams".
                Return ONLY the JSON, no extra text.
                """.formatted(sessionContext != null ? sessionContext : "No previous context", userInput);
    }

    /**
     * Build prompt for follow-up question generation.
     */
    public static String buildFollowUpPrompt(String scenarioCode, List<String> missingParams) {
        return """
                You are a helpful assistant speaking to an enterprise user.
                Scenario: %s
                Missing parameters: %s
                
                Ask the user to provide the missing information in one simple, friendly sentence.
                Return ONLY the question text, nothing else.
                """.formatted(scenarioCode, String.join(", ", missingParams));
    }

    /**
     * Build prompt for response formatting.
     */
    public static String buildResponseFormattingPrompt(String scenarioCode, String dataJson, String userQuery) {
        return """
                You are a professional assistant speaking to an enterprise user.
                You will be given:
                - Scenario code
                - Raw data (JSON)
                - Original user query
                
                Your job:
                - Explain the result in clear, friendly language.
                - Do not hallucinate or add information not in the data.
                - Do not mention internal field names.
                - If any important data is missing, say that clearly.
                
                Scenario: %s
                Data (JSON): %s
                User query: "%s"
                
                Now generate a single response message for the user.
                """.formatted(scenarioCode, dataJson, userQuery);
    }
}
