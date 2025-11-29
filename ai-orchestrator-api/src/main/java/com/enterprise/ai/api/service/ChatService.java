package com.enterprise.ai.api.service;

import com.enterprise.ai.common.dto.*;
import com.enterprise.ai.core.router.ScenarioRouter;
import com.enterprise.ai.data.entity.ChatMessage;
import com.enterprise.ai.data.entity.ChatSession;
import com.enterprise.ai.data.entity.AiAuditLog;
import com.enterprise.ai.data.repository.ChatMessageRepository;
import com.enterprise.ai.data.repository.ChatSessionRepository;
import com.enterprise.ai.data.repository.AiAuditLogRepository;
import com.enterprise.ai.llm.client.LlmClient;
import com.enterprise.ai.security.rbac.RbacService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

/**
 * Main chat service that orchestrates the conversation flow.
 * Implements 3-layer protection system for safe intent execution:
 * 1. Confidence threshold checking
 * 2. Required parameter enforcement
 * 3. User confirmation for sensitive/ambiguous requests
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final LlmClient llmClient;
    private final ScenarioRouter scenarioRouter;
    private final RbacService rbacService;
    private final IntentValidationService validationService;
    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final AiAuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    /**
     * Process a chat request with 3-layer protection.
     */
    public ChatResponse processChat(ChatRequest request) {
        String executionId = UUID.randomUUID().toString();
        Instant requestTime = Instant.now();

        try {
            // Get or create session
            String sessionId = getOrCreateSession(request);

            // Handle confirmation response
            if (request.isConfirmationResponse()) {
                return handleConfirmationResponse(request, sessionId, executionId, requestTime);
            }

            // Handle clarification response
            if (request.isClarificationResponse()) {
                return handleClarificationResponse(request, sessionId, executionId);
            }

            // Save user message
            saveMessage(sessionId, "user", request.getQuery());

            // Get session context
            String sessionContext = getSessionContext(sessionId);

            // Detect intent
            IntentResult intent = llmClient.detectIntent(request.getQuery(), sessionContext);
            log.info("Detected intent: scenario={}, confidence={}, params={}", 
                    intent.getScenario(), intent.getConfidence(), intent.getParams());

            // === 3-LAYER PROTECTION SYSTEM ===
            
            // Validate intent using 3-layer protection
            IntentValidationService.ValidationResult validation = 
                    validationService.validate(intent, request.getQuery());

            if (!validation.isValid()) {
                return handleValidationFailure(validation, intent, request, sessionId, executionId);
            }

            // Check authorization
            List<String> userRoles = getCurrentUserRoles();
            if (!rbacService.isAnyRoleAuthorized(userRoles, intent.getScenario())) {
                String response = "You don't have permission to access this information.";
                saveMessage(sessionId, "assistant", response);
                return buildResponse(sessionId, response, ChatResponse.ResponseType.ERROR, 
                        null, intent.getScenario(), null, intent.getConfidence(), executionId);
            }

            // Execute scenario (all validations passed)
            return executeScenario(intent, request, sessionId, executionId, requestTime);

        } catch (Exception e) {
            log.error("Error processing chat request", e);
            logAudit(executionId, request.getUserId(), null, requestTime, 
                    Instant.now(), false, e.getMessage(), null, null);
            return buildErrorResponse(request.getSessionId(), 
                    "An error occurred processing your request. Please try again.");
        }
    }

    /**
     * Handle validation failures based on type.
     */
    private ChatResponse handleValidationFailure(
            IntentValidationService.ValidationResult validation,
            IntentResult intent,
            ChatRequest request,
            String sessionId,
            String executionId) {

        // Low confidence - ask for clarification
        if (validation.hasLowConfidence()) {
            String response = "I'm not fully sure what you're asking for. Could you please:\n" +
                    "• Be more specific about what you want to check\n" +
                    "• Include relevant IDs or names\n" +
                    "• Or tell me if you want to check:\n" +
                    "  1. Transaction status\n" +
                    "  2. File processing status\n" +
                    "  3. Account balance";
            saveMessage(sessionId, "assistant", response);
            return buildResponse(sessionId, response, ChatResponse.ResponseType.CLARIFICATION,
                    null, null, List.of("TXN_STATUS", "FILE_STATUS", "ACCOUNT_SUMMARY"), 
                    intent.getConfidence(), executionId);
        }

        // Ambiguous - ask for clarification
        if (validation.isAmbiguous()) {
            String clarificationQuestion = llmClient.generateFollowUpQuestion(
                    "CLARIFICATION", 
                    validation.suggestedScenarios() != null ? 
                            validation.suggestedScenarios() : List.of()
            );
            
            // Build clarification options
            StringBuilder response = new StringBuilder();
            response.append(validation.validationMessage()).append("\n\n");
            response.append("Please select an option:\n");
            List<String> scenarios = validation.suggestedScenarios();
            for (int i = 0; i < scenarios.size(); i++) {
                response.append(String.format("%d. %s\n", i + 1, 
                        validationService.getScenarioDescription(scenarios.get(i))));
            }
            
            saveMessage(sessionId, "assistant", response.toString());
            return buildResponse(sessionId, response.toString(), ChatResponse.ResponseType.CLARIFICATION,
                    null, null, scenarios, intent.getConfidence(), executionId);
        }

        // Missing parameters - ask for them
        if (!validation.missingRequiredParams().isEmpty()) {
            String followUp = llmClient.generateFollowUpQuestion(
                    intent.getScenario(), validation.missingRequiredParams());
            saveMessage(sessionId, "assistant", followUp);
            return ChatResponse.builder()
                    .sessionId(sessionId)
                    .message(followUp)
                    .responseType(ChatResponse.ResponseType.FOLLOW_UP)
                    .followUpRequired(true)
                    .missingParams(validation.missingRequiredParams())
                    .scenario(intent.getScenario())
                    .confidence(intent.getConfidence())
                    .meta(ChatResponse.ResponseMeta.builder()
                            .executionId(executionId)
                            .intentConfidence(intent.getConfidence())
                            .intentReasoning(intent.getReasoning())
                            .build())
                    .build();
        }

        // Needs confirmation - ask user to confirm
        if (validation.needsConfirmation()) {
            Map<String, Object> params = intent.getParams() != null ? intent.getParams() : Map.of();
            String confirmationMsg = String.format(
                    "I understand you want to check %s",
                    validationService.getScenarioDescription(intent.getScenario())
            );
            if (!params.isEmpty()) {
                String paramStr = params.entrySet().stream()
                        .map(e -> e.getKey() + ": " + e.getValue())
                        .reduce((a, b) -> a + ", " + b)
                        .orElse("");
                confirmationMsg += " for " + paramStr;
            }
            confirmationMsg += ".\n\nShall I proceed? (Yes/No)";
            
            saveMessage(sessionId, "assistant", confirmationMsg);
            
            return ChatResponse.builder()
                    .sessionId(sessionId)
                    .message(confirmationMsg)
                    .responseType(ChatResponse.ResponseType.CONFIRMATION)
                    .followUpRequired(true)
                    .scenario(intent.getScenario())
                    .confidence(intent.getConfidence())
                    .pendingAction(ChatResponse.PendingAction.builder()
                            .scenario(intent.getScenario())
                            .params(params)
                            .description(validationService.getScenarioDescription(intent.getScenario()))
                            .build())
                    .meta(ChatResponse.ResponseMeta.builder()
                            .executionId(executionId)
                            .intentConfidence(intent.getConfidence())
                            .build())
                    .build();
        }

        // Fallback - unknown validation failure
        String response = "I couldn't process your request. Please try rephrasing.";
        saveMessage(sessionId, "assistant", response);
        return buildErrorResponse(sessionId, response);
    }

    /**
     * Handle user's confirmation response (Yes/No).
     */
    private ChatResponse handleConfirmationResponse(
            ChatRequest request, String sessionId, String executionId, Instant requestTime) {
        
        saveMessage(sessionId, "user", request.getQuery());
        
        if (Boolean.TRUE.equals(request.getConfirmed()) || 
                isAffirmativeResponse(request.getQuery())) {
            // User confirmed - execute the pending action
            if (request.getPendingScenario() != null && request.getPendingActionParams() != null) {
                IntentResult intent = IntentResult.builder()
                        .scenario(request.getPendingScenario())
                        .confidence(1.0)  // User confirmed
                        .params(request.getPendingActionParams())
                        .missingParams(List.of())
                        .build();
                return executeScenario(intent, request, sessionId, executionId, requestTime);
            }
        }
        
        // User declined or invalid confirmation
        String response = "Okay, I've cancelled that action. How else can I help you?";
        saveMessage(sessionId, "assistant", response);
        return buildResponse(sessionId, response, ChatResponse.ResponseType.DIRECT, 
                null, null, null, null, executionId);
    }

    /**
     * Handle user's clarification response (option selection).
     */
    private ChatResponse handleClarificationResponse(
            ChatRequest request, String sessionId, String executionId) {
        
        saveMessage(sessionId, "user", request.getQuery());
        
        // Try to extract the selected option from the response
        String selectedScenario = extractSelectedScenario(request);
        
        if (selectedScenario != null) {
            // Re-process with the clarified scenario as context
            String clarifiedQuery = request.getQuery() + " (User selected: " + 
                    validationService.getScenarioDescription(selectedScenario) + ")";
            
            // Get session context
            String sessionContext = getSessionContext(sessionId);
            
            // Detect intent again with clarified context
            IntentResult intent = llmClient.detectIntent(clarifiedQuery, sessionContext);
            
            // Force the scenario to what user selected
            intent.setScenario(selectedScenario);
            intent.setConfidence(0.95);  // User clarified
            
            // Continue with normal validation (check params)
            IntentValidationService.ValidationResult validation = 
                    validationService.validate(intent, request.getQuery());
            
            if (!validation.missingRequiredParams().isEmpty()) {
                String followUp = llmClient.generateFollowUpQuestion(
                        selectedScenario, validation.missingRequiredParams());
                saveMessage(sessionId, "assistant", followUp);
                return ChatResponse.builder()
                        .sessionId(sessionId)
                        .message(followUp)
                        .responseType(ChatResponse.ResponseType.FOLLOW_UP)
                        .followUpRequired(true)
                        .missingParams(validation.missingRequiredParams())
                        .scenario(selectedScenario)
                        .build();
            }
        }
        
        // Couldn't understand the selection
        String response = "I didn't understand your selection. Please reply with a number (1, 2, or 3) or describe what you're looking for.";
        saveMessage(sessionId, "assistant", response);
        return buildResponse(sessionId, response, ChatResponse.ResponseType.CLARIFICATION, 
                null, null, List.of("TXN_STATUS", "FILE_STATUS", "ACCOUNT_SUMMARY"), null, executionId);
    }

    /**
     * Execute the scenario after all validations pass.
     */
    private ChatResponse executeScenario(
            IntentResult intent, ChatRequest request, String sessionId, 
            String executionId, Instant requestTime) {
        
        ScenarioRequest scenarioRequest = ScenarioRequest.builder()
                .scenario(intent.getScenario())
                .params(intent.getParams())
                .userId(request.getUserId())
                .sessionId(sessionId)
                .build();

        ScenarioResult result = scenarioRouter.route(scenarioRequest);

        // Format response using LLM
        String formattedResponse = llmClient.formatResponse(
                intent.getScenario(), result, request.getQuery());
        saveMessage(sessionId, "assistant", formattedResponse);

        // Log successful audit
        logAudit(executionId, request.getUserId(), intent.getScenario(), requestTime,
                Instant.now(), true, null, intent, result);

        return ChatResponse.builder()
                .sessionId(sessionId)
                .message(formattedResponse)
                .responseType(ChatResponse.ResponseType.DIRECT)
                .followUpRequired(false)
                .scenario(intent.getScenario())
                .confidence(intent.getConfidence())
                .meta(ChatResponse.ResponseMeta.builder()
                        .executionId(executionId)
                        .intentConfidence(intent.getConfidence())
                        .intentReasoning(intent.getReasoning())
                        .build())
                .build();
    }

    /**
     * Check if user response is affirmative.
     */
    private boolean isAffirmativeResponse(String response) {
        if (response == null) return false;
        String lower = response.toLowerCase().trim();
        return lower.matches("(yes|y|yeah|yep|sure|ok|okay|proceed|confirm|haan|ha|theek hai).*");
    }

    /**
     * Extract selected scenario from clarification response.
     */
    private String extractSelectedScenario(ChatRequest request) {
        // Check explicit selection
        if (request.getSelectedOption() != null) {
            return switch (request.getSelectedOption()) {
                case 1 -> "TXN_STATUS";
                case 2 -> "FILE_STATUS";
                case 3 -> "ACCOUNT_SUMMARY";
                default -> null;
            };
        }
        
        // Try to extract from query text
        String query = request.getQuery().toLowerCase();
        if (query.contains("1") || query.contains("transaction") || query.contains("payment") || query.contains("txn")) {
            return "TXN_STATUS";
        }
        if (query.contains("2") || query.contains("file") || query.contains("batch") || query.contains("upload")) {
            return "FILE_STATUS";
        }
        if (query.contains("3") || query.contains("balance") || query.contains("account") || query.contains("summary")) {
            return "ACCOUNT_SUMMARY";
        }
        
        return null;
    }

    private String getOrCreateSession(ChatRequest request) {
        if (request.getSessionId() != null) {
            Optional<ChatSession> existing = sessionRepository.findBySessionId(request.getSessionId());
            if (existing.isPresent()) {
                return request.getSessionId();
            }
        }

        String sessionId = UUID.randomUUID().toString();
        ChatSession session = ChatSession.builder()
                .sessionId(sessionId)
                .userId(request.getUserId())
                .build();
        sessionRepository.save(session);
        return sessionId;
    }

    private void saveMessage(String sessionId, String role, String content) {
        ChatMessage message = ChatMessage.builder()
                .sessionId(sessionId)
                .role(role)
                .content(content)
                .build();
        messageRepository.save(message);
    }

    private String getSessionContext(String sessionId) {
        List<ChatMessage> recentMessages = messageRepository.findTop10BySessionIdOrderByTimestampDesc(sessionId);
        if (recentMessages.isEmpty()) {
            return "No previous context";
        }

        StringBuilder context = new StringBuilder();
        Collections.reverse(recentMessages);
        for (ChatMessage msg : recentMessages) {
            context.append(msg.getRole()).append(": ").append(msg.getContent()).append("\n");
        }
        return context.toString();
    }

    private List<String> getCurrentUserRoles() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getAuthorities() == null) {
            return List.of();
        }
        return auth.getAuthorities().stream()
                .map(a -> a.getAuthority().replace("ROLE_", ""))
                .toList();
    }

    private ChatResponse buildResponse(String sessionId, String message, ChatResponse.ResponseType type,
                                       List<String> missingParams, String scenario, 
                                       List<String> possibleScenarios, Double confidence, String executionId) {
        return ChatResponse.builder()
                .sessionId(sessionId)
                .message(message)
                .responseType(type)
                .followUpRequired(type != ChatResponse.ResponseType.DIRECT && type != ChatResponse.ResponseType.ERROR)
                .missingParams(missingParams)
                .scenario(scenario)
                .possibleScenarios(possibleScenarios)
                .confidence(confidence)
                .meta(ChatResponse.ResponseMeta.builder()
                        .executionId(executionId)
                        .intentConfidence(confidence)
                        .build())
                .build();
    }

    private ChatResponse buildErrorResponse(String sessionId, String message) {
        return ChatResponse.builder()
                .sessionId(sessionId)
                .message(message)
                .responseType(ChatResponse.ResponseType.ERROR)
                .followUpRequired(false)
                .build();
    }

    private void logAudit(String executionId, String userId, String scenarioCode,
                          Instant requestTime, Instant responseTime, boolean success,
                          String errorMessage, IntentResult intent, ScenarioResult result) {
        try {
            AiAuditLog auditLog = AiAuditLog.builder()
                    .executionId(executionId)
                    .userId(userId)
                    .scenarioCode(scenarioCode)
                    .requestTime(requestTime)
                    .responseTime(responseTime)
                    .success(success)
                    .errorMessage(errorMessage)
                    .rawIntentJson(intent != null ? objectMapper.writeValueAsString(intent) : null)
                    .rawResultJson(result != null ? objectMapper.writeValueAsString(result) : null)
                    .build();
            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.error("Error saving audit log", e);
        }
    }
}
