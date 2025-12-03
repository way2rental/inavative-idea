package com.enterprise.ai.llm.executor;

import com.enterprise.ai.common.dto.ScenarioRequest;
import com.enterprise.ai.common.dto.ScenarioResult;
import com.enterprise.ai.common.enums.ExecutionType;
import com.enterprise.ai.core.scenario.DynamicExecutor;
import com.enterprise.ai.data.entity.AiScenario;
import com.enterprise.ai.data.service.SystemConfigService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Dynamic executor for LLM_ONLY execution type.
 * Used for scenarios that don't require database or HTTP calls,
 * such as email drafting, summarization, and content generation.
 * 
 * The LLM generates content directly based on:
 * - Chat history and context
 * - User-provided parameters
 * - Scenario-specific prompt templates from database
 * 
 * Key scenarios: EMAIL_DRAFT, SUMMARY_GENERATION, CONVERSATIONAL_REPLY
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LlmOnlyExecutor implements DynamicExecutor {

    private static final long DEFAULT_TIMEOUT_MS = 30000;

    private final ChatClient chatClient;
    private final SystemConfigService systemConfigService;
    private final ObjectMapper objectMapper;

    @Override
    public boolean supports(String executionType) {
        return ExecutionType.LLM_ONLY.name().equals(executionType);
    }

    @Override
    public ScenarioResult execute(ScenarioRequest request, AiScenario scenario) {
        long startTime = System.currentTimeMillis();
        String scenarioCode = scenario.getScenarioCode();

        try {
            log.info("Executing LLM_ONLY scenario: {} with params: {}", scenarioCode, request.getParams());

            // Build the prompt for this scenario
            String prompt = buildScenarioPrompt(request, scenario);
            log.debug("LLM prompt for {}: {}", scenarioCode, 
                    prompt.length() > 500 ? prompt.substring(0, 500) + "..." : prompt);

            // Call LLM
            String llmResponse = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            log.debug("LLM response for {}: {}", scenarioCode, 
                    llmResponse.length() > 500 ? llmResponse.substring(0, 500) + "..." : llmResponse);

            // Parse and structure the response
            Map<String, Object> responseData = parseResponse(llmResponse, scenarioCode);

            long executionTime = System.currentTimeMillis() - startTime;
            log.info("LLM_ONLY scenario {} completed in {}ms", scenarioCode, executionTime);

            return ScenarioResult.builder()
                    .scenario(scenarioCode)
                    .success(true)
                    .data(responseData)
                    .build();

        } catch (Exception e) {
            log.error("LLM_ONLY execution failed for {}: {}", scenarioCode, e.getMessage(), e);
            return ScenarioResult.builder()
                    .scenario(scenarioCode)
                    .success(false)
                    .errorMessage("Failed to generate content: " + e.getMessage())
                    .data(Map.of("error", "Content generation error"))
                    .build();
        }
    }

    @Override
    public Mono<ScenarioResult> executeReactive(ScenarioRequest request, AiScenario scenario) {
        long timeoutMs = scenario.getTimeoutMs() != null ? scenario.getTimeoutMs() : DEFAULT_TIMEOUT_MS;

        return Mono.fromCallable(() -> execute(request, scenario))
                .subscribeOn(Schedulers.boundedElastic())
                .timeout(Duration.ofMillis(timeoutMs))
                .onErrorResume(e -> {
                    log.error("Reactive LLM_ONLY failed: {}", e.getMessage());
                    return Mono.just(ScenarioResult.builder()
                            .scenario(scenario.getScenarioCode())
                            .success(false)
                            .errorMessage("Content generation timeout or error: " + e.getMessage())
                            .build());
                });
    }

    @Override
    public ScenarioResult executeDryRun(ScenarioRequest request, AiScenario scenario) {
        try {
            String prompt = buildScenarioPrompt(request, scenario);

            return ScenarioResult.builder()
                    .scenario(scenario.getScenarioCode())
                    .success(true)
                    .data(Map.of(
                            "dryRun", true,
                            "executionType", ExecutionType.LLM_ONLY.name(),
                            "prompt", prompt.length() > 1000 ? prompt.substring(0, 1000) + "..." : prompt,
                            "params", request.getParams(),
                            "timeoutMs", scenario.getTimeoutMs()
                    ))
                    .build();
        } catch (Exception e) {
            return ScenarioResult.builder()
                    .scenario(scenario.getScenarioCode())
                    .success(false)
                    .errorMessage("Dry-run validation failed: " + e.getMessage())
                    .build();
        }
    }

    /**
     * Build the prompt for LLM-only scenarios.
     * Uses scenario-specific template from database if available.
     */
    private String buildScenarioPrompt(ScenarioRequest request, AiScenario scenario) {
        String scenarioCode = scenario.getScenarioCode();
        Map<String, Object> params = request.getParams() != null ? request.getParams() : new HashMap<>();

        // Get branding from config
        String orgName = systemConfigService.getOrgName();
        String assistantName = systemConfigService.getAssistantName();

        // Check if scenario has a custom LLM prompt template
        String customTemplate = scenario.getLlmPromptTemplate();
        if (customTemplate != null && !customTemplate.isBlank()) {
            return substituteVariables(customTemplate, params, orgName, assistantName);
        }

        // Build default prompt based on scenario code
        return switch (scenarioCode.toUpperCase()) {
            case "EMAIL_DRAFT" -> buildEmailDraftPrompt(params, orgName, assistantName);
            case "EMAIL_COMPLAINT" -> buildComplaintEmailPrompt(params, orgName, assistantName);
            case "EMAIL_REQUEST" -> buildRequestEmailPrompt(params, orgName, assistantName);
            case "EMAIL_FOLLOWUP" -> buildFollowUpEmailPrompt(params, orgName, assistantName);
            case "SUMMARIZE_CHAT" -> buildSummarizePrompt(params, assistantName);
            default -> buildGenericContentPrompt(params, scenario.getDescription(), assistantName);
        };
    }

    /**
     * Build email drafting prompt based on chat history and user context.
     * Identifies email type and creates professional banking correspondence.
     */
    private String buildEmailDraftPrompt(Map<String, Object> params, String orgName, String assistantName) {
        StringBuilder prompt = new StringBuilder();

        // Get email context from params
        String chatHistory = getStringParam(params, "chatHistory", "");
        String emailType = getStringParam(params, "emailType", "general");
        String subject = getStringParam(params, "subject", "");
        String recipientType = getStringParam(params, "recipientType", "customer_service");
        String customerName = getStringParam(params, "customerName", "[Customer Name]");
        String accountNumber = getStringParam(params, "accountNumber", "[Account Number]");
        String issueDescription = getStringParam(params, "issueDescription", "");
        String transactionId = getStringParam(params, "transactionId", "");
        String amount = getStringParam(params, "amount", "");
        String transactionDate = getStringParam(params, "transactionDate", "");

        prompt.append("""
            ═══════════════════════════════════════════════════════════════════════════════
            PROFESSIONAL BANKING EMAIL GENERATOR
            ═══════════════════════════════════════════════════════════════════════════════
            
            You are helping a customer of %s draft a professional banking email.
            
            BANK INFORMATION:
            - Bank Name: %s
            - Customer Support Email: support@%s.com
            - Escalations: escalations@%s.com
            
            """.formatted(orgName, orgName, orgName.toLowerCase().replace(" ", ""), orgName.toLowerCase().replace(" ", "")));

        // Add chat history context if available
        if (!chatHistory.isBlank()) {
            prompt.append("""
                ═══════════════════════════════════════════════════════════════════════════════
                CONVERSATION CONTEXT (Use this to understand the customer's situation):
                ═══════════════════════════════════════════════════════════════════════════════
                %s
                
                """.formatted(chatHistory));
        }

        prompt.append("""
            ═══════════════════════════════════════════════════════════════════════════════
            EMAIL DETAILS:
            ═══════════════════════════════════════════════════════════════════════════════
            Email Type: %s
            Subject: %s
            Recipient: %s
            Customer Name: %s
            Account Number: %s
            """.formatted(
                emailType.isBlank() ? "[To be determined from context]" : emailType,
                subject.isBlank() ? "[To be generated]" : subject,
                recipientType,
                customerName,
                accountNumber
            ));

        // Add transaction details if available
        if (!transactionId.isBlank() || !amount.isBlank() || !transactionDate.isBlank()) {
            prompt.append("""
                
                Transaction Details:
                - Transaction ID: %s
                - Amount: %s
                - Date: %s
                """.formatted(
                    transactionId.isBlank() ? "[Not provided]" : transactionId,
                    amount.isBlank() ? "[Not provided]" : "₹" + amount,
                    transactionDate.isBlank() ? "[Not provided]" : transactionDate
                ));
        }

        if (!issueDescription.isBlank()) {
            prompt.append("\nIssue Description: ").append(issueDescription).append("\n");
        }

        prompt.append("""
            
            ═══════════════════════════════════════════════════════════════════════════════
            YOUR TASK:
            ═══════════════════════════════════════════════════════════════════════════════
            
            1. ANALYZE the conversation context to understand what the customer needs
            2. IDENTIFY the appropriate email type:
               - COMPLAINT: For issues, disputes, failed transactions
               - REQUEST: For service requests, document requests, account changes
               - INQUIRY: For information, clarification, status checks
               - FEEDBACK: For suggestions, appreciation, general feedback
               - ESCALATION: For unresolved issues needing higher attention
            
            3. GENERATE a professional email with:
               - Clear subject line (if not provided)
               - Proper salutation
               - Reference numbers where applicable
               - Specific details from the conversation
               - Professional tone appropriate for banking
               - Clear call to action
               - Proper closing
            
            4. USE PLACEHOLDERS for missing information:
               - [Customer Name] - if name not provided
               - [Account Number] - if account not provided
               - [Transaction ID] - if transaction reference not provided
               - [Date] - for specific dates
               - [Amount] - for specific amounts
            
            ═══════════════════════════════════════════════════════════════════════════════
            EMAIL TEMPLATES BY TYPE:
            ═══════════════════════════════════════════════════════════════════════════════
            
            COMPLAINT EMAIL STRUCTURE:
            - Subject: Complaint Regarding [Issue] - Account: [Account Number]
            - Reference any case numbers or previous communications
            - Clearly state the issue with dates and amounts
            - Mention impact on customer
            - Request specific resolution with timeline
            - Request acknowledgment and case number
            
            REQUEST EMAIL STRUCTURE:
            - Subject: Request for [Service] - Account: [Account Number]
            - Clearly state what is being requested
            - Provide necessary account/identity details
            - Mention any urgency if applicable
            - Request confirmation and timeline
            
            ═══════════════════════════════════════════════════════════════════════════════
            RESPONSE FORMAT (JSON):
            ═══════════════════════════════════════════════════════════════════════════════
            
            {
              "type": "EMAIL",
              "title": "Email Draft 📧",
              "confidence": 1.0,
              "payload": {
                "emailType": "COMPLAINT | REQUEST | INQUIRY | FEEDBACK | ESCALATION",
                "subject": "Generated or provided subject line",
                "to": "Recipient email suggestion",
                "body": "Complete email body with proper formatting",
                "placeholders": ["List of placeholders used that need customer input"],
                "tips": ["Any tips for the customer before sending"]
              },
              "footer": "Review and customize before sending",
              "suggestedFollowUps": ["Edit recipient", "Add attachment note", "Send another email"]
            }
            
            IMPORTANT:
            - Use proper email formatting with paragraphs
            - Include today's date: %s
            - Be professional but not overly formal
            - If context is unclear, generate a general inquiry email and note what information is needed
            
            Generate the email now.
            """.formatted(LocalDate.now().format(DateTimeFormatter.ofPattern("dd MMMM yyyy"))));

        return prompt.toString();
    }

    /**
     * Build complaint-specific email prompt.
     */
    private String buildComplaintEmailPrompt(Map<String, Object> params, String orgName, String assistantName) {
        Map<String, Object> enhancedParams = new HashMap<>(params);
        enhancedParams.put("emailType", "complaint");
        return buildEmailDraftPrompt(enhancedParams, orgName, assistantName);
    }

    /**
     * Build request-specific email prompt.
     */
    private String buildRequestEmailPrompt(Map<String, Object> params, String orgName, String assistantName) {
        Map<String, Object> enhancedParams = new HashMap<>(params);
        enhancedParams.put("emailType", "request");
        return buildEmailDraftPrompt(enhancedParams, orgName, assistantName);
    }

    /**
     * Build follow-up email prompt.
     */
    private String buildFollowUpEmailPrompt(Map<String, Object> params, String orgName, String assistantName) {
        Map<String, Object> enhancedParams = new HashMap<>(params);
        enhancedParams.put("emailType", "followup");
        enhancedParams.put("subject", getStringParam(params, "subject", "Follow-up: Previous Communication"));
        return buildEmailDraftPrompt(enhancedParams, orgName, assistantName);
    }

    /**
     * Build chat summarization prompt.
     */
    private String buildSummarizePrompt(Map<String, Object> params, String assistantName) {
        String chatHistory = getStringParam(params, "chatHistory", "No conversation history available.");

        return """
            You are %s, a banking assistant.
            
            Summarize the following conversation into key points:
            
            CONVERSATION:
            %s
            
            Create a concise summary with:
            1. Main topics discussed
            2. Key actions taken or requested
            3. Any pending items
            4. Important reference numbers mentioned
            
            RESPONSE FORMAT (JSON):
            {
              "type": "SUMMARY",
              "title": "Conversation Summary 📋",
              "payload": {
                "topics": ["topic1", "topic2"],
                "actions": ["action1", "action2"],
                "pending": ["pending item"],
                "references": {"type": "reference_number"}
              },
              "suggestedFollowUps": ["Continue with...", "Start new topic"]
            }
            """.formatted(assistantName, chatHistory);
    }

    /**
     * Build generic content generation prompt.
     */
    private String buildGenericContentPrompt(Map<String, Object> params, String description, String assistantName) {
        String content = getStringParam(params, "content", "");
        String instruction = getStringParam(params, "instruction", "Generate helpful content");

        return """
            You are %s, a professional banking assistant.
            
            TASK: %s
            Description: %s
            
            USER INPUT: %s
            
            Generate appropriate content in JSON format:
            {
              "type": "TEXT",
              "title": "Generated Content",
              "payload": {
                "content": "Your generated content here"
              },
              "suggestedFollowUps": ["Related action 1", "Related action 2"]
            }
            """.formatted(assistantName, instruction, 
                description != null ? description : "Content generation", 
                content);
    }

    /**
     * Substitute variables in a template string.
     */
    private String substituteVariables(String template, Map<String, Object> params, String orgName, String assistantName) {
        String result = template;
        result = result.replace("{{orgName}}", orgName);
        result = result.replace("{{assistantName}}", assistantName);
        result = result.replace("{{date}}", LocalDate.now().format(DateTimeFormatter.ofPattern("dd MMMM yyyy")));

        for (Map.Entry<String, Object> entry : params.entrySet()) {
            String value = entry.getValue() != null ? entry.getValue().toString() : "";
            result = result.replace("{{" + entry.getKey() + "}}", value);
        }

        return result;
    }

    /**
     * Parse LLM response into structured data.
     */
    private Map<String, Object> parseResponse(String llmResponse, String scenarioCode) {
        try {
            // Try to parse as JSON
            String jsonPart = extractJson(llmResponse);
            if (jsonPart != null) {
                return objectMapper.readValue(jsonPart, new TypeReference<Map<String, Object>>() {});
            }
        } catch (Exception e) {
            log.warn("Failed to parse LLM response as JSON for {}: {}", scenarioCode, e.getMessage());
        }

        // Fallback: wrap as text response
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("type", "TEXT");
        response.put("title", getDefaultTitle(scenarioCode));
        response.put("payload", Map.of("content", llmResponse));
        response.put("suggestedFollowUps", List.of("Edit content", "Generate another", "Help"));
        return response;
    }

    /**
     * Extract JSON from LLM response.
     */
    private String extractJson(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return null;
    }

    /**
     * Get default title based on scenario code.
     */
    private String getDefaultTitle(String scenarioCode) {
        return switch (scenarioCode.toUpperCase()) {
            case "EMAIL_DRAFT" -> "Email Draft 📧";
            case "EMAIL_COMPLAINT" -> "Complaint Email 📧";
            case "EMAIL_REQUEST" -> "Request Email 📧";
            case "EMAIL_FOLLOWUP" -> "Follow-up Email 📧";
            case "SUMMARIZE_CHAT" -> "Conversation Summary 📋";
            default -> "Generated Content";
        };
    }

    /**
     * Safely get string parameter with default value.
     */
    private String getStringParam(Map<String, Object> params, String key, String defaultValue) {
        if (params == null || !params.containsKey(key)) {
            return defaultValue;
        }
        Object value = params.get(key);
        return value != null ? value.toString() : defaultValue;
    }
}
