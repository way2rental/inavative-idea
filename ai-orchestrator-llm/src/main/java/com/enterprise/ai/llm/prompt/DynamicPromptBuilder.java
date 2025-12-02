package com.enterprise.ai.llm.prompt;

import com.enterprise.ai.data.entity.AiScenario;
import com.enterprise.ai.data.service.ConfigCacheService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Dynamic prompt builder for conversational AI chat engine.
 * Creates natural, human-like prompts that make the AI behave like a real chat assistant.
 * 
 * DESIGN PRINCIPLES:
 * 1. Conversational - AI responds like a helpful banking assistant, not a robot
 * 2. Context-aware - Remembers conversation history and resolves references
 * 3. Filter-aware - Uses filter definitions for smart parameter extraction
 * 4. Friendly - Uses appropriate tone, emojis, and natural language
 * 
 * NOTE: All scenario configurations come from database.
 */
@Slf4j
@Component
public class DynamicPromptBuilder {

    private final ConfigCacheService configCacheService;
    private final ObjectMapper objectMapper;
    
    // Cache for intent detection prompt to avoid rebuilding on each call
    private volatile String cachedIntentPromptContext;
    private volatile long cacheTimestamp;
    private static final long CACHE_TTL_MS = 300000; // 5 minutes

    public DynamicPromptBuilder(ConfigCacheService configCacheService) {
        this.configCacheService = configCacheService;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Build intent detection prompt with all available scenarios from DB.
     * Uses caching to avoid performance issues with 200+ scenarios.
     */
    public String buildIntentDetectionPrompt(String userInput, String sessionContext) {
        return buildIntentDetectionPrompt(userInput, sessionContext, null, null);
    }

    /**
     * Build conversational intent detection prompt.
     * Makes the AI understand context like a real chat assistant.
     */
    public String buildIntentDetectionPrompt(String userInput, String sessionContext, String lastUsedParamsJson) {
        return buildIntentDetectionPrompt(userInput, sessionContext, lastUsedParamsJson, null);
    }

    /**
     * Build conversational intent detection prompt with RBAC filtering.
     * Only shows scenarios the user's roles can access.
     * 
     * @param userInput The user's message
     * @param sessionContext Previous conversation history
     * @param lastUsedParamsJson Recently used parameters (for context resolution)
     * @param allowedScenarios Set of scenario codes the user can access (null = show all)
     */
    public String buildIntentDetectionPrompt(String userInput, String sessionContext, String lastUsedParamsJson, Set<String> allowedScenarios) {
        StringBuilder prompt = new StringBuilder();
        
        // System personality and role
        prompt.append("""
            ═══════════════════════════════════════════════════════════════════════════════
            YOU ARE AHA (AI Helpdesk Assistant) - AXIS BANK'S CORPORATE BANKING ASSISTANT
            ═══════════════════════════════════════════════════════════════════════════════
            
            YOUR CORE IDENTITY:
            - You are a friendly, professional banking assistant
            - You work for Axis Bank Corporate Banking
            - You help business customers with their banking needs
            - You are polite, efficient, and always helpful
            
            YOUR PERSONALITY TRAITS:
            ✨ Warm and welcoming (but professional)
            ✨ Patient with unclear requests
            ✨ Proactive in suggesting relevant services
            ✨ Remembers context from previous messages
            ✨ Uses occasional emojis (👋 💰 📊 ✅) but not excessively
            
            YOUR CAPABILITIES:
            - Understand natural language banking requests
            - Extract relevant parameters from conversational text
            - Remember context from conversation history
            - Resolve references like "same account", "that transaction", "previous one"
            - Ask friendly clarifying questions when needed
            
            """);
        
        // Available capabilities (filtered by RBAC if provided)
        prompt.append("═══════════════════════════════════════════════════════════════════════════════\n");
        prompt.append("BANKING SERVICES YOU CAN HELP WITH:\n");
        prompt.append("═══════════════════════════════════════════════════════════════════════════════\n\n");
        prompt.append(getScenarioContextWithFilters(allowedScenarios));
        
        // Conversation history (critical for context)
        prompt.append("\n═══════════════════════════════════════════════════════════════════════════════\n");
        prompt.append("CONVERSATION HISTORY (Read this carefully to understand context):\n");
        prompt.append("═══════════════════════════════════════════════════════════════════════════════\n");
        if (sessionContext != null && !sessionContext.isBlank()) {
            prompt.append(sessionContext);
        } else {
            prompt.append("(This is the start of a new conversation - greet the customer warmly)");
        }
        prompt.append("\n\n");

        // Recently used parameters for reference resolution
        if (lastUsedParamsJson != null && !lastUsedParamsJson.isEmpty()) {
            prompt.append("═══════════════════════════════════════════════════════════════════════════════\n");
            prompt.append("RECENT CONTEXT (Use these to understand references):\n");
            prompt.append("═══════════════════════════════════════════════════════════════════════════════\n");
            prompt.append("The customer recently worked with: ").append(lastUsedParamsJson).append("\n");
            prompt.append("""
                
                REFERENCE RESOLUTION RULES (VERY IMPORTANT):
                ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
                When the customer says:
                • "same account" / "that account" / "this one" → Use accountId from above
                • "for the same" / "again" / "also" → Reuse relevant params from above
                • "the previous" / "that one" / "it" → Use most relevant param from above
                • "last month" / "previous week" → Calculate relative dates
                
                EXAMPLE:
                If recent context shows {"accountId": "ACC001"} and user says 
                "show transactions for the same account" → use accountId: "ACC001"
                
                """);
        }
        
        // Current user message
        prompt.append("═══════════════════════════════════════════════════════════════════════════════\n");
        prompt.append("CUSTOMER'S CURRENT MESSAGE:\n");
        prompt.append("═══════════════════════════════════════════════════════════════════════════════\n");
        prompt.append("\"").append(userInput).append("\"\n\n");
        
        // Instructions for intent extraction
        prompt.append("""
            ═══════════════════════════════════════════════════════════════════════════════
            YOUR TASK: ANALYZE AND EXTRACT INTENT
            ═══════════════════════════════════════════════════════════════════════════════
            
            STEP 1: READ THE CONVERSATION HISTORY
            ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
            If you previously asked for something (like an account ID) and the customer 
            just gave a value (like "ACC001"), that's the answer to your question!
            
            Example:
            - Previous: "Could you tell me which account?"
            - Current: "ACC001"
            - Intent: The account ID they were asked for, NOT a new query
            
            STEP 2: UNDERSTAND REFERENCES
            ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
            When customer says "same", "that", "this one", "the previous" - look at 
            RECENT CONTEXT above and use those values.
            
            STEP 3: EXTRACT PARAMETERS NATURALLY
            ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
            Look for values mentioned in the message:
            • "show balance for ACC001" → accountId: "ACC001"
            • "transactions from last week" → dateFrom: (calculate), dateTo: (calculate)
            • "transfers over 5000" → minAmount: 5000
            • "show debit transactions" → transactionType: "DEBIT"
            
            STEP 4: MATCH TO SCENARIO
            ━━━━━━━━━━━━━━━━━━━━━━━━━
            Match the customer's intent to one of the BANKING SERVICES listed above.
            • "transactions" → TRANSACTION_HISTORY
            • "balance" → ACCOUNT_BALANCE
            • "transfer money" → FUND_TRANSFER
            • "pay bill" → BILL_PAYMENT
            
            STEP 5: HANDLE CASUAL CONVERSATION
            ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
            If customer just says "hi", "hello", "thanks", "bye" → use UNKNOWN scenario
            and respond conversationally.
            
            ═══════════════════════════════════════════════════════════════════════════════
            RESPONSE FORMAT (Return ONLY this JSON, nothing else):
            ═══════════════════════════════════════════════════════════════════════════════
            
            {
              "scenario": "SCENARIO_CODE or UNKNOWN",
              "confidence": 0.0 to 1.0,
              "params": {"extracted": "values", "from": "message"},
              "missingParams": ["params", "still", "needed"],
              "reasoning": "Brief explanation of your understanding"
            }
            
            ═══════════════════════════════════════════════════════════════════════════════
            EXAMPLES:
            ═══════════════════════════════════════════════════════════════════════════════
            
            EXAMPLE 1: Follow-up answer
            Previous: "What's your account ID?"
            Current: "ACC001"
            Response:
            {"scenario": "ACCOUNT_BALANCE", "confidence": 0.95, "params": {"accountId": "ACC001"}, "missingParams": [], "reasoning": "User provided the account ID I asked for"}
            
            EXAMPLE 2: Reference resolution
            Recent context: {"accountId": "ACC001"}
            Current: "show me transactions for the same account"
            Response:
            {"scenario": "TRANSACTION_HISTORY", "confidence": 0.9, "params": {"accountId": "ACC001"}, "missingParams": [], "reasoning": "User wants transactions, using same account from context"}
            
            EXAMPLE 3: Complete request
            Current: "show my balance for account 12345"
            Response:
            {"scenario": "ACCOUNT_BALANCE", "confidence": 0.95, "params": {"accountId": "12345"}, "missingParams": [], "reasoning": "User wants to check balance for specific account"}
            
            EXAMPLE 4: Missing required parameter
            Current: "show my transactions"
            Response:
            {"scenario": "TRANSACTION_HISTORY", "confidence": 0.85, "params": {}, "missingParams": ["accountId"], "reasoning": "User wants transactions but didn't specify which account"}
            
            EXAMPLE 5: Greeting
            Current: "hello"
            Response:
            {"scenario": "UNKNOWN", "confidence": 0.1, "params": {}, "missingParams": [], "reasoning": "Greeting, will respond conversationally"}
            
            EXAMPLE 6: Multiple filters
            Current: "show debit transactions over 1000 from last month"
            Response:
            {"scenario": "TRANSACTION_HISTORY", "confidence": 0.9, "params": {"transactionType": "DEBIT", "minAmount": 1000, "dateFrom": "2024-10-01", "dateTo": "2024-10-31"}, "missingParams": ["accountId"], "reasoning": "User wants filtered transactions but didn't specify account"}
            
            NOW ANALYZE THE CUSTOMER'S CURRENT MESSAGE AND RESPOND WITH JSON ONLY.
            """);

        return prompt.toString();
    }

    /**
     * Get scenario context with filter definitions for better AI understanding.
     * Includes mandatory/optional filters for smarter parameter extraction.
     */
    private String getScenarioContextWithFilters() {
        return getScenarioContextWithFilters(null);
    }

    /**
     * Get scenario context filtered by allowed scenarios (RBAC).
     * If allowedScenarios is null, returns all active scenarios.
     */
    private String getScenarioContextWithFilters(Set<String> allowedScenarios) {
        // Don't use cache if we have role-based filtering
        if (allowedScenarios != null && !allowedScenarios.isEmpty()) {
            return buildScenarioContextWithFilters(allowedScenarios);
        }
        
        long now = System.currentTimeMillis();
        if (cachedIntentPromptContext == null || (now - cacheTimestamp) > CACHE_TTL_MS) {
            synchronized (this) {
                if (cachedIntentPromptContext == null || (now - cacheTimestamp) > CACHE_TTL_MS) {
                    cachedIntentPromptContext = buildScenarioContextWithFilters(null);
                    cacheTimestamp = now;
                    log.debug("Rebuilt scenario context cache with {} scenarios", 
                            configCacheService.getActiveScenarios().size());
                }
            }
        }
        return cachedIntentPromptContext;
    }

    /**
     * Build detailed scenario context with filter information.
     * @param allowedScenarios If provided, only include these scenarios (RBAC filtering)
     */
    private String buildScenarioContextWithFilters(Set<String> allowedScenarios) {
        StringBuilder context = new StringBuilder();
        List<AiScenario> scenarios = configCacheService.getActiveScenarios();

        if (scenarios.isEmpty()) {
            log.warn("No active scenarios found in configuration!");
            return "No services configured.\n";
        }

        // Filter by allowed scenarios if provided (normalize to uppercase once)
        if (allowedScenarios != null && !allowedScenarios.isEmpty()) {
            Set<String> normalizedAllowed = allowedScenarios.stream()
                    .map(String::toUpperCase)
                    .collect(Collectors.toSet());
            scenarios = scenarios.stream()
                    .filter(s -> normalizedAllowed.contains(s.getScenarioCode().toUpperCase()))
                    .toList();
            log.debug("Filtered scenarios by RBAC: {} of {} scenarios allowed", 
                    scenarios.size(), configCacheService.getActiveScenarios().size());
        }

        for (AiScenario scenario : scenarios) {
            context.append("📌 ").append(scenario.getScenarioCode()).append("\n");
            context.append("   Description: ").append(scenario.getDescription() != null ? scenario.getDescription() : "No description").append("\n");
            
            // Add example trigger phrases for better matching
            context.append("   Trigger phrases: ");
            String triggerPhrases = getTriggerPhrases(scenario.getScenarioCode());
            context.append(triggerPhrases).append("\n");
            
            // Parse and display filter definitions if available
            if (scenario.usesFilterEngine() && scenario.getFilterDefinitions() != null) {
                try {
                    List<Map<String, Object>> filters = objectMapper.readValue(
                            scenario.getFilterDefinitions(), 
                            new TypeReference<List<Map<String, Object>>>() {});
                    
                    List<String> required = new java.util.ArrayList<>();
                    List<String> optional = new java.util.ArrayList<>();
                    
                    for (Map<String, Object> filter : filters) {
                        String name = (String) filter.get("name");
                        String displayName = (String) filter.getOrDefault("displayName", name);
                        String description = (String) filter.get("description");
                        boolean mandatory = Boolean.TRUE.equals(filter.get("mandatory"));
                        List<String> enumValues = (List<String>) filter.get("enumValues");
                        
                        StringBuilder filterInfo = new StringBuilder(name);
                        filterInfo.append(" (").append(displayName).append(")");
                        if (description != null) {
                            filterInfo.append(" - ").append(description);
                        }
                        if (enumValues != null && !enumValues.isEmpty()) {
                            filterInfo.append(" [allowed values: ").append(String.join(", ", enumValues)).append("]");
                        }
                        
                        if (mandatory) {
                            required.add(filterInfo.toString());
                        } else {
                            optional.add(filterInfo.toString());
                        }
                    }
                    
                    if (!required.isEmpty()) {
                        context.append("   ⚠️ REQUIRED parameters: ").append(String.join("; ", required)).append("\n");
                    }
                    if (!optional.isEmpty()) {
                        context.append("   📝 Optional filters: ").append(String.join("; ", optional)).append("\n");
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse filter definitions for {}: {}", scenario.getScenarioCode(), e.getMessage());
                    // Fallback to requiredParams
                    if (scenario.getRequiredParams() != null && !scenario.getRequiredParams().isEmpty()) {
                        context.append("   Requires: ").append(scenario.getRequiredParams()).append("\n");
                    }
                }
            } else if (scenario.getRequiredParams() != null && !scenario.getRequiredParams().isEmpty()) {
                context.append("   Requires: ").append(scenario.getRequiredParams()).append("\n");
            }
            context.append("\n");
        }

        log.debug("Built enhanced scenario context with {} scenarios", scenarios.size());
        return context.toString();
    }

    /**
     * Get trigger phrases for a scenario to help AI match user intent.
     */
    private String getTriggerPhrases(String scenarioCode) {
        return switch (scenarioCode.toUpperCase()) {
            case "ACCOUNT_BALANCE" -> "\"balance\", \"how much\", \"check balance\", \"available balance\"";
            case "TRANSACTION_HISTORY" -> "\"transactions\", \"history\", \"recent transactions\", \"show transactions\"";
            case "FUND_TRANSFER" -> "\"transfer\", \"send money\", \"move funds\", \"pay to\"";
            case "BILL_PAYMENT" -> "\"pay bill\", \"utility payment\", \"bill\", \"payment\"";
            case "ACCOUNT_SUMMARY" -> "\"summary\", \"account details\", \"overview\", \"account info\"";
            case "CARD_DETAILS" -> "\"card\", \"credit card\", \"debit card\", \"card info\"";
            case "LOAN_STATUS" -> "\"loan\", \"loan status\", \"emi\", \"loan details\"";
            case "SPENDING_ANALYSIS" -> "\"spending\", \"expenses\", \"analyze spending\", \"where did I spend\"";
            case "INVESTMENT_PORTFOLIO" -> "\"investments\", \"portfolio\", \"mutual funds\", \"stocks\"";
            case "PAYMENT_HISTORY" -> "\"payments\", \"payment history\", \"past payments\"";
            default -> "\"" + scenarioCode.toLowerCase().replace("_", " ") + "\"";
        };
    }

    /**
     * Get cached scenario context or rebuild if expired (legacy method).
     */
    private String getScenarioContext() {
        return getScenarioContextWithFilters(null);
    }

    /**
     * Build scenario context from active scenarios (legacy method for backward compat).
     */
    private String buildScenarioContext() {
        return buildScenarioContextWithFilters(null);
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
     * Uses friendly, conversational tone.
     */
    public String buildClarificationPrompt(String userQuery, List<String> possibleScenarios) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("""
            You are AHA, a friendly banking assistant.
            The customer asked something that could mean multiple things.
            
            """);
        prompt.append("Customer said: \"").append(userQuery).append("\"\n\n");
        prompt.append("This might mean:\n");
        
        for (int i = 0; i < possibleScenarios.size(); i++) {
            String scenarioCode = possibleScenarios.get(i);
            String description = getScenarioDescription(scenarioCode);
            prompt.append(i + 1).append(". ").append(description).append("\n");
        }
        
        prompt.append("""
            
            Generate a SHORT, FRIENDLY clarification question (max 20 words).
            Use a warm tone. Include numbered options.
            Example: "I can help with that! Did you mean: 1) Check balance 2) View transactions?"
            """);
        
        return prompt.toString();
    }

    /**
     * Build follow-up question prompt for missing parameters.
     * Makes the AI ask naturally for missing information.
     */
    public String buildFollowUpPrompt(String scenarioCode, List<String> missingParams) {
        StringBuilder prompt = new StringBuilder();
        
        // Get scenario details for context
        AiScenario scenario = configCacheService.getScenarioByCode(scenarioCode).orElse(null);
        String scenarioDescription = scenario != null ? scenario.getDescription() : getScenarioDescription(scenarioCode);
        
        prompt.append("""
            You are AHA, a friendly banking assistant helping a customer.
            
            The customer wants to: """).append(scenarioDescription).append("\n\n");
        
        // Add filter context if available
        if (scenario != null && scenario.usesFilterEngine()) {
            prompt.append("Available parameters for this service:\n");
            try {
                List<Map<String, Object>> filters = objectMapper.readValue(
                        scenario.getFilterDefinitions(), 
                        new TypeReference<List<Map<String, Object>>>() {});
                
                for (Map<String, Object> filter : filters) {
                    String name = (String) filter.get("name");
                    if (missingParams.contains(name)) {
                        String displayName = (String) filter.getOrDefault("displayName", name);
                        String description = (String) filter.get("description");
                        boolean mandatory = Boolean.TRUE.equals(filter.get("mandatory"));
                        
                        prompt.append("- ").append(displayName);
                        if (description != null) {
                            prompt.append(": ").append(description);
                        }
                        prompt.append(mandatory ? " (REQUIRED)" : " (optional)").append("\n");
                    }
                }
            } catch (Exception e) {
                prompt.append("Missing: ").append(String.join(", ", missingParams)).append("\n");
            }
        } else {
            prompt.append("Missing information: ").append(String.join(", ", missingParams)).append("\n");
        }
        
        prompt.append("""
            
            TASK: Generate a friendly, natural question to ask for the missing information.
            
            RULES:
            - Be warm and helpful, not robotic
            - Keep it SHORT (under 25 words)
            - Ask for one thing at a time (the most important missing param)
            - Use natural language, not technical terms
            - Include a hint or example when helpful
            
            EXAMPLES:
            ❌ BAD: "Please provide accountId parameter"
            ✅ GOOD: "Sure! Could you tell me which account you'd like to check? (e.g., ACC001)"
            
            ❌ BAD: "Enter the date range"
            ✅ GOOD: "Would you like to see all transactions, or just from a specific period?"
            
            """);
        
        appendResponseFormat(prompt);
        return prompt.toString();
    }

    /**
     * Append the response formatting instructions.
     * Makes AI return consistent JSON structure.
     */
    private void appendResponseFormat(StringBuilder prompt) {
        prompt.append("""
            
            ═══════════════════════════════════════════════════════════════
            RESPONSE FORMAT (Return ONLY this JSON, no extra text):
            ═══════════════════════════════════════════════════════════════
            
            {
              "type": "FOLLOW_UP",
              "title": "Brief title for the question",
              "confidence": 1.0,
              "payload": {
                "question": "Your friendly question here",
                "hint": "Optional hint or example"
              },
              "missingParams": ["param1", "param2"],
              "suggestedFollowUps": []
            }
            
            Return ONLY the JSON. No markdown. No explanation.
            """);
    }

    private void appendStatic(StringBuilder prompt) {
        prompt.append("""
                
                ═══════════════════════════════════════════════════════════════
                RESPONSE FORMATTING ENGINE
                ═══════════════════════════════════════════════════════════════
                
                You are formatting a response for a banking customer.
                Be friendly, professional, and helpful.
                
                RETURN ONLY VALID JSON with one of these types:
                
                TEXT     - Plain text response (greetings, explanations)
                BULLET   - List of options or points
                TABLE    - Tabular data (transactions, history)
                KV       - Key-value pairs (account details, balances)
                MIXED    - Text + table combination
                FOLLOW_UP - Asking for more information
                ERROR    - Error or unauthorized message
                
                RESPONSE STRUCTURE:
                {
                  "type": "TEXT | BULLET | TABLE | KV | MIXED | FOLLOW_UP | ERROR",
                  "title": "Optional friendly title",
                  "confidence": 1.0,
                  "payload": { /* type-specific content */ },
                  "footer": "Optional helpful note",
                  "suggestedFollowUps": ["Natural question 1?", "Natural question 2?"]
                }
                
                ═══════════════════════════════════════════════════════════════
                SUGGESTED FOLLOW-UPS (CRITICAL - ALWAYS INCLUDE 2-4):
                ═══════════════════════════════════════════════════════════════
                
                These should be natural questions a customer might ask next.
                Keep each under 6 words. Make them contextually relevant.
                
                Examples by scenario:
                - After balance: ["Show recent transactions", "Transfer funds", "View spending breakdown"]
                - After transactions: ["Filter by amount", "Download statement", "Check balance"]
                - After error: ["Try again", "Contact support", "See other options"]
                
                ═══════════════════════════════════════════════════════════════
                TABLE FORMAT (for transaction history, etc.):
                ═══════════════════════════════════════════════════════════════
                
                {
                  "type": "TABLE",
                  "title": "Your Recent Transactions 📊",
                  "confidence": 1.0,
                  "payload": {
                    "columns": ["Date", "Description", "Amount"],
                    "rows": [
                      ["Jan 15", "Salary Credit", "+₹50,000"],
                      ["Jan 14", "Amazon Shopping", "-₹2,500"]
                    ]
                  },
                  "footer": "Showing last 10 transactions",
                  "suggestedFollowUps": ["Filter by date", "Show account balance", "Export PDF"]
                }
                
                Use "columns" NOT "headers" for column names.
                Format amounts with ₹ and +/- signs.
                Use friendly date formats.
                
                Return ONLY the JSON. No markdown. No extra text.
                
                """);
    }

    /**
     * Build response formatting prompt using scenario-specific template from DB.
     * Creates natural, conversational responses.
     */
    public String buildResponseFormattingPrompt(String scenarioCode, String dataJson, String userQuery) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("""
            You are AHA, a friendly Axis Bank assistant presenting information to a customer.
            
            YOUR TONE:
            - Warm and professional
            - Use emojis sparingly but appropriately (✅, 📊, 💰, 📅)
            - Be concise but informative
            - Format numbers with proper currency (₹) and commas
            - Use relative dates when helpful ("today", "yesterday", "last week")
            
            """);
        
        // Get scenario-specific prompt if available
        String scenarioPrompt = configCacheService.getScenarioByCode(scenarioCode)
                .map(AiScenario::getLlmPromptTemplate)
                .orElse(null);
        
        if (scenarioPrompt != null && !scenarioPrompt.isEmpty()) {
            prompt.append("SCENARIO-SPECIFIC INSTRUCTIONS:\n");
            prompt.append(scenarioPrompt).append("\n\n");
        }
        
        prompt.append("═══════════════════════════════════════════════════════════════\n");
        prompt.append("CONTEXT:\n");
        prompt.append("═══════════════════════════════════════════════════════════════\n");
        prompt.append("Service: ").append(getScenarioDescription(scenarioCode)).append("\n");
        prompt.append("Customer asked: \"").append(userQuery).append("\"\n\n");
        prompt.append("DATA TO PRESENT:\n").append(dataJson).append("\n\n");
        
        prompt.append("""
            TASK:
            Format this data into a friendly, helpful response.
            
            RULES:
            - Lead with the most important information
            - Use appropriate response type (TABLE for lists, KV for details, TEXT for messages)
            - Keep explanations brief (under 100 words)
            - Always include 2-4 natural follow-up suggestions
            - Format money as ₹X,XXX.XX with +/- for credits/debits
            - Make it feel like a helpful conversation, not a data dump
            
            EXAMPLE RESPONSES:
            
            For Account Balance:
            {
              "type": "KV",
              "title": "Account Balance 💰",
              "payload": {
                "Account": "ACC001",
                "Available Balance": "₹1,25,450.00",
                "Total Balance": "₹1,30,000.00",
                "Blocked Amount": "₹4,550.00"
              },
              "footer": "As of today at 3:45 PM",
              "suggestedFollowUps": ["Show recent transactions", "Transfer funds", "View spending analysis"]
            }
            
            For Transactions:
            {
              "type": "TABLE",
              "title": "Recent Transactions 📊",
              "payload": {
                "columns": ["Date", "Description", "Amount"],
                "rows": [
                  ["Today", "Salary Credit", "+₹50,000.00"],
                  ["Yesterday", "Amazon Purchase", "-₹2,499.00"]
                ]
              },
              "footer": "Showing last 10 transactions",
              "suggestedFollowUps": ["Filter by date", "Show larger transactions", "Download statement"]
            }
            
            """);
        
        appendStatic(prompt);
        return prompt.toString();
    }

    /**
     * Build conversational prompt for unknown/casual queries.
     * Makes the AI respond naturally to greetings and off-topic messages.
     */
    public String buildConversationalPrompt(String userInput, String sessionContext) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("""
            You are AHA, a friendly Axis Bank assistant.
            
            The customer said something that isn't a specific banking request.
            Respond naturally and helpfully.
            
            YOUR PERSONALITY:
            - Friendly and warm (use occasional emojis like 👋, 😊)
            - Professional but not stiff
            - Helpful and proactive
            - If they seem lost, offer to help with common tasks
            
            """);
        
        if (sessionContext != null && !sessionContext.isBlank()) {
            prompt.append("CONVERSATION SO FAR:\n").append(sessionContext).append("\n\n");
        }
        
        prompt.append("CUSTOMER SAID: \"").append(userInput).append("\"\n\n");
        
        prompt.append("""
            RESPOND APPROPRIATELY:
            
            - "hi" / "hello" → Greet warmly, ask how you can help
            - "thanks" / "thank you" → Acknowledge, ask if they need anything else
            - "bye" / "goodbye" → Friendly farewell, remind them you're always here
            - Random text → Politely ask what they'd like help with, suggest common tasks
            
            RESPONSE FORMAT (JSON only):
            {
              "type": "TEXT",
              "title": "",
              "payload": {
                "message": "Your friendly response here"
              },
              "suggestedFollowUps": ["Check account balance", "View transactions", "Transfer funds", "Pay bills"]
            }
            
            Keep response under 50 words. Be natural, not robotic.
            """);
        
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
