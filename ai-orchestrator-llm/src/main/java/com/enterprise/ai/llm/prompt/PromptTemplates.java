package com.enterprise.ai.llm.prompt;

import java.util.List;
import java.util.Map;

/**
 * Lightweight prompt templates for LLM interactions.
 * Intent definitions have been moved to Ollama Modelfiles:
 * - enterprise-intent: For intent detection
 * - enterprise-formatter: For response formatting
 * 
 * This class provides helper methods for constructing prompts
 * when custom models are not available (fallback to base model).
 */
public final class PromptTemplates {

    private PromptTemplates() {
        // Utility class
    }

    /**
     * Build minimal prompt for intent detection.
     * Used when enterprise-intent model handles scenario definitions.
     */
    public static String buildIntentDetectionPrompt(String userInput, String sessionContext) {
        return """
                Session context: %s
                
                User message: "%s"
                
                Analyze the user's intent and return JSON with:
                - scenario: The detected scenario code
                - confidence: Confidence score (0.0 to 1.0)
                - params: Extracted parameters as key-value pairs
                - missingParams: Required parameters that are missing
                - reasoning: Brief explanation
                """.formatted(
                        sessionContext != null ? sessionContext : "No previous context",
                        userInput
                );
    }

    /**
     * Build prompt for clarification when intent is ambiguous.
     */
    public static String buildClarificationPrompt(String userQuery, List<String> possibleScenarios) {
        String scenarioDescriptions = possibleScenarios.stream()
                .map(s -> switch (s) {
                    case "TXN_STATUS" -> "1. Transaction status (payment/transfer status)";
                    case "FILE_STATUS" -> "2. File/batch processing status";
                    case "ACCOUNT_SUMMARY" -> "3. Account balance/summary";
                    default -> s;
                })
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");
        
        return """
                User asked: "%s"
                
                This could mean:
                %s
                
                Generate a SHORT clarification question with numbered options.
                """.formatted(userQuery, scenarioDescriptions);
    }

    /**
     * Build prompt for follow-up question generation.
     */
    public static String buildFollowUpPrompt(String scenarioCode, List<String> missingParams) {
        String contextualHelp = switch (scenarioCode) {
            case "TXN_STATUS" -> "Ask for transaction ID/UTR/reference number.";
            case "FILE_STATUS" -> "Ask for file name or date of upload.";
            case "ACCOUNT_SUMMARY" -> "Ask for account number or type.";
            default -> "Ask for the missing information politely.";
        };
        
        return """
                Scenario: %s
                Missing: %s
                
                %s
                
                Generate a SHORT, FRIENDLY question (under 25 words).
                """.formatted(scenarioCode, String.join(", ", missingParams), contextualHelp);
    }

    /**
     * Build minimal prompt for response formatting.
     * Used when enterprise-formatter model handles formatting rules.
     */
    public static String buildResponseFormattingPrompt(String scenarioCode, String dataJson, String userQuery) {
        return """
                Scenario: %s
                User query: "%s"
                Raw data: %s
                
                Format this data into a natural, helpful response.
                Keep it under 150 words. Use emojis appropriately.
                """.formatted(scenarioCode, userQuery, dataJson);
    }
}
