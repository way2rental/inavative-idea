package com.enterprise.ai.llm.prompt;

import java.util.List;
import java.util.Map;

/**
 * Production-grade prompt templates for LLM interactions.
 * Includes rich intent definitions with synonyms, examples, and language variations
 * for improved natural language understanding (English + Hinglish support).
 */
public final class PromptTemplates {

    private PromptTemplates() {
        // Utility class
    }

    /**
     * Production-grade intent definitions with rich descriptions, synonyms, and examples.
     */
    private static final String INTENT_DEFINITIONS = """
            ===== SCENARIO DEFINITIONS (USE THESE TO DETECT INTENT) =====
            
            ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
            SCENARIO: TXN_STATUS
            ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
            PURPOSE:
            Used ONLY when the user is asking about the status of an individual 
            payment, transfer, or transaction.
            
            POSITIVE SIGNALS (Match these):
            - transaction, txn, transfer, payment, UPI, IMPS, NEFT, RTGS
            - status, pending, failed, success, completed, stuck
            - reference, UTR, TXN number, transaction ID
            - "paise aaye", "paise gaye", "payment hua", "transfer complete"
            - "money debited", "money credited", "amount transferred"
            
            EXAMPLE USER QUERIES (These should match TXN_STATUS):
            - "Check my transaction status"
            - "Is my payment successful?"
            - "Paise aaye ya nahi?"
            - "UPI transfer ka status kya hai?"
            - "My transaction is pending"
            - "Money debited but not credited"
            - "Why is my transaction stuck?"
            - "Status of TXN12345"
            - "Payment pending hai"
            - "Mera transaction fail ho gaya"
            
            NEGATIVE SIGNALS (DO NOT match if these are primary):
            - balance, account summary, total balance
            - file, batch, salary file, bulk upload
            - statement, mini statement
            
            REQUIRED PARAMS: ["txnId"]
            OPTIONAL PARAMS: ["date", "channel"]
            
            PARAM EXTRACTION RULES:
            - If user mentions TXN, UTR, reference number → Extract as txnId
            - If no txnId mentioned → Add "txnId" to missingParams
            
            ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
            SCENARIO: FILE_STATUS
            ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
            PURPOSE:
            Used when the user wants to check whether a batch file, bulk file, 
            or upload has been processed.
            
            POSITIVE SIGNALS (Match these):
            - file, batch, upload, bulk, records
            - processed, processing, uploaded, failed records
            - salary file, NEFT batch, reconciliation
            - "file ka status", "batch process hua"
            - csv, excel, data file
            
            EXAMPLE USER QUERIES (These should match FILE_STATUS):
            - "Has the salary file been processed?"
            - "File abhi tak process hui ya nahi?"
            - "NEFT batch ka status kya hai?"
            - "Is the upload completed?"
            - "How many records failed in the file?"
            - "Salary file for today"
            - "Reconciliation file status"
            - "Batch processing complete hua?"
            - "Upload status check karna hai"
            
            NEGATIVE SIGNALS (DO NOT match if these are primary):
            - single transaction, individual payment
            - account balance, summary
            - personal transfer
            
            REQUIRED PARAMS: ["fileName"]
            OPTIONAL PARAMS: ["date", "fileType"]
            
            PARAM EXTRACTION RULES:
            - If user mentions file name → Extract as fileName
            - If user mentions file type (salary, NEFT) but no name → Ask for date
            - If no fileName mentioned → Add "fileName" to missingParams
            
            ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
            SCENARIO: ACCOUNT_SUMMARY
            ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
            PURPOSE:
            Used when the user wants to see their account balance, summary, 
            or overall account information.
            
            POSITIVE SIGNALS (Match these):
            - balance, account balance, available balance
            - summary, account summary, overview
            - "kitne paise hain", "kitna balance hai"
            - savings, current account
            - "account details", "balance dikhao"
            
            EXAMPLE USER QUERIES (These should match ACCOUNT_SUMMARY):
            - "Show my account balance"
            - "Mere account me kitne paise hain?"
            - "Account summary dikhao"
            - "Available balance?"
            - "Savings account ka balance"
            - "Current balance kya hai?"
            - "Statement ka short summary"
            - "Mera balance batao"
            - "Account overview"
            
            NEGATIVE SIGNALS (DO NOT match if these are primary):
            - transaction status, payment status
            - file processing, batch status
            - specific txn ID, UTR
            
            REQUIRED PARAMS: ["accountId"]
            OPTIONAL PARAMS: ["dateRange"]
            
            PARAM EXTRACTION RULES:
            - If user mentions account number → Extract as accountId
            - If no accountId mentioned → Add "accountId" to missingParams
            
            ===== AMBIGUITY HANDLING =====
            
            If the query is AMBIGUOUS (could match multiple scenarios), set:
            - scenario: "AMBIGUOUS"
            - confidence: 0.5 or lower
            - Include "possibleScenarios" array in response
            
            AMBIGUOUS EXAMPLES:
            - "Mera balance pending hai" → Could be transaction or account
            - "Status check karna hai" → Could be any scenario
            - "Kuch update hai?" → Too vague
            
            ===== CONFIDENCE SCORING =====
            
            confidence score should be:
            - 0.95+ : Perfect match with clear keywords and params
            - 0.80-0.94 : Good match, clear intent
            - 0.60-0.79 : Moderate match, might need confirmation
            - 0.40-0.59 : Low confidence, likely ambiguous
            - Below 0.40 : Set scenario to "UNKNOWN"
            """;

    /**
     * Build prompt for intent detection with production-grade scenario definitions.
     */
    public static String buildIntentDetectionPrompt(String userInput, String sessionContext) {
        return """
                You are an ENTERPRISE INTENT DETECTION ENGINE.
                Your job is to accurately classify user queries and extract parameters.
                
                STRICT OUTPUT FORMAT - Return ONLY this JSON:
                {
                  "scenario": "string",
                  "confidence": number (0.0 to 1.0),
                  "params": { extracted parameters },
                  "missingParams": [ list of required but missing params ],
                  "possibleScenarios": [ only if ambiguous ],
                  "reasoning": "brief explanation of classification"
                }
                
                %s
                
                ===== SESSION CONTEXT =====
                %s
                
                ===== USER MESSAGE =====
                "%s"
                
                ===== INSTRUCTIONS =====
                1. Read the user message carefully
                2. Match against scenario definitions above
                3. Check positive and negative signals
                4. Extract any parameters mentioned
                5. Set appropriate confidence score
                6. If ambiguous, set scenario to "AMBIGUOUS"
                7. If no match, set scenario to "UNKNOWN"
                
                Return ONLY the JSON object, no explanations outside JSON.
                """.formatted(
                        INTENT_DEFINITIONS,
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
                You are a helpful assistant. The user's query was ambiguous.
                
                User asked: "%s"
                
                This could mean any of these:
                %s
                
                Generate a SHORT, FRIENDLY clarification question asking which one they want.
                Include numbered options. Be concise. Use simple language.
                
                Return ONLY the question text.
                """.formatted(userQuery, scenarioDescriptions);
    }

    /**
     * Build prompt for confirmation before executing sensitive scenarios.
     */
    public static String buildConfirmationPrompt(String scenarioCode, Map<String, Object> params, String userQuery) {
        String paramSummary = params.entrySet().stream()
                .map(e -> e.getKey() + ": " + e.getValue())
                .reduce((a, b) -> a + ", " + b)
                .orElse("none");
        
        return """
                You are a helpful assistant. Generate a brief confirmation message.
                
                User asked: "%s"
                Detected action: %s
                Parameters: %s
                
                Generate a SHORT confirmation question like:
                "I'll check the [action] for [param]. Should I proceed?"
                
                Keep it under 20 words. Be clear and professional.
                Return ONLY the confirmation question.
                """.formatted(userQuery, scenarioCode, paramSummary);
    }

    /**
     * Build prompt for follow-up question generation.
     */
    public static String buildFollowUpPrompt(String scenarioCode, List<String> missingParams) {
        String contextualHelp = switch (scenarioCode) {
            case "TXN_STATUS" -> """
                    For transaction status, the user needs to provide a transaction ID.
                    This could be: TXN number, UTR, Reference number, or any transaction identifier.
                    """;
            case "FILE_STATUS" -> """
                    For file status, the user needs to provide a file name.
                    This could be: exact file name, file type (like salary, NEFT), or date of upload.
                    """;
            case "ACCOUNT_SUMMARY" -> """
                    For account summary, the user needs to specify which account.
                    This could be: account number, account type (savings/current), or account alias.
                    """;
            default -> "Please ask for the missing information politely.";
        };
        
        return """
                You are a helpful enterprise assistant.
                
                Scenario: %s
                Missing parameters: %s
                
                Context:
                %s
                
                Generate a SHORT, FRIENDLY question asking for the missing information.
                - Be specific about what you need
                - Give examples if helpful
                - Keep it under 25 words
                - Use simple, professional language
                
                Return ONLY the question text.
                """.formatted(scenarioCode, String.join(", ", missingParams), contextualHelp);
    }

    /**
     * Build prompt for response formatting.
     */
    public static String buildResponseFormattingPrompt(String scenarioCode, String dataJson, String userQuery) {
        String formatGuidelines = switch (scenarioCode) {
            case "TXN_STATUS" -> """
                    For transaction status:
                    - Lead with the status (Success/Pending/Failed)
                    - Mention the amount and channel
                    - Include timestamp
                    - If failed, mention reason if available
                    """;
            case "FILE_STATUS" -> """
                    For file status:
                    - Lead with processing status
                    - Show total records vs processed vs failed
                    - Mention completion time if done
                    - Highlight any errors briefly
                    """;
            case "ACCOUNT_SUMMARY" -> """
                    For account summary:
                    - Show available balance prominently
                    - Mention account type
                    - Keep sensitive details masked
                    - Be concise
                    """;
            default -> "Present the information clearly and professionally.";
        };
        
        return """
                You are a professional enterprise assistant.
                
                User query: "%s"
                Scenario: %s
                Raw data: %s
                
                Formatting guidelines:
                %s
                
                RULES:
                - DO NOT hallucinate or add information not in the data
                - DO NOT expose internal field names
                - Use simple, clear language
                - Be concise (under 100 words)
                - If data is missing, say so clearly
                - Format numbers nicely (e.g., ₹5,000 not 5000)
                
                Generate a natural, helpful response.
                """.formatted(userQuery, scenarioCode, dataJson, formatGuidelines);
    }
}
