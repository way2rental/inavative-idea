package com.enterprise.ai.api.service;

import com.enterprise.ai.common.dto.*;
import com.enterprise.ai.core.router.ScenarioRouter;
import com.enterprise.ai.data.entity.ChatMessage;
import com.enterprise.ai.data.entity.ChatSession;
import com.enterprise.ai.data.repository.ChatMessageRepository;
import com.enterprise.ai.data.repository.ChatSessionRepository;
import com.enterprise.ai.intelligence.client.ReactiveIntelligenceClient;
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

    /**
     * Pattern for detecting affirmative responses (supports English and Hinglish).
     */
    private static final String AFFIRMATIVE_PATTERN = 
            "(yes|y|yeah|yep|sure|ok|okay|proceed|confirm|haan|ha|theek hai).*";

    private final ReactiveIntelligenceClient intelligenceClient;
    private final ScenarioRouter scenarioRouter;
    private final RbacService rbacService;
    private final IntentValidationService validationService;
    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final AuditService auditService;
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
            IntentResult intent = intelligenceClient.detectIntent(request.getQuery(), sessionContext).block();
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
            if (!rbacService.anyRoleAuthorized(userRoles, intent.getScenario())) {
                String response = "You don't have permission to access this information.";
                saveMessage(sessionId, "assistant", response);
                return buildResponse(sessionId, response, ChatResponse.ResponseType.ERROR, 
                        null, intent.getScenario(), null, intent.getConfidence(), executionId);
            }

            // Execute scenario (all validations passed)
            return executeScenario(intent, request, sessionId, executionId, requestTime);

        } catch (Exception e) {
            log.error("Error processing chat request", e);
            auditService.logAuditAsync(executionId, request.getUserId(), null, requestTime, 
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

        // Low confidence - ask for clarification with dynamic scenarios from DB
        if (validation.hasLowConfidence()) {
            List<String> commonScenarios = validationService.getCommonScenarios();
            StringBuilder response = new StringBuilder();
            response.append("I'm not fully sure what you're asking for. Could you please:\n");
            response.append("• Be more specific about what you want to check\n");
            response.append("• Include relevant IDs or names\n");
            response.append("• Or tell me if you want to check:\n");
            for (int i = 0; i < commonScenarios.size(); i++) {
                response.append(String.format("  %d. %s\n", i + 1, 
                        validationService.getScenarioDescription(commonScenarios.get(i))));
            }
            saveMessage(sessionId, "assistant", response.toString());
            return buildResponse(sessionId, response.toString(), ChatResponse.ResponseType.CLARIFICATION,
                    null, null, commonScenarios, 
                    intent.getConfidence(), executionId);
        }

        // Ambiguous - ask for clarification
        if (validation.isAmbiguous()) {
            String clarificationQuestion = intelligenceClient.generateFollowUpQuestion(
                    "CLARIFICATION", 
                    validation.suggestedScenarios() != null ? 
                            validation.suggestedScenarios() : List.of()
            ).block();
            
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
            String followUp = intelligenceClient.generateFollowUpQuestion(
                    intent.getScenario(), validation.missingRequiredParams()).block();
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
                IntentResult detectedIntent = intelligenceClient.detectIntent(clarifiedQuery, sessionContext).block();
            
            // Create a new intent with user-selected scenario (using builder for immutability)
            IntentResult intent = IntentResult.builder()
                    .scenario(selectedScenario)
                    .confidence(0.95)  // User clarified
                    .params(detectedIntent.getParams())
                    .missingParams(detectedIntent.getMissingParams())
                    .possibleScenarios(null)
                    .reasoning("User selected from clarification options")
                    .build();
            
            // Continue with normal validation (check params)
            IntentValidationService.ValidationResult validation = 
                    validationService.validate(intent, request.getQuery());
            
            if (!validation.missingRequiredParams().isEmpty()) {
                String followUp = intelligenceClient.generateFollowUpQuestion(
                        selectedScenario, validation.missingRequiredParams()).block();
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
        
        // Couldn't understand the selection - show dynamic options
        List<String> commonScenarios = validationService.getCommonScenarios();
        StringBuilder response = new StringBuilder();
        response.append("I didn't understand your selection. Please reply with a number or describe what you're looking for:\n");
        for (int i = 0; i < commonScenarios.size(); i++) {
            response.append(String.format("%d. %s\n", i + 1, 
                    validationService.getScenarioDescription(commonScenarios.get(i))));
        }
        saveMessage(sessionId, "assistant", response.toString());
        return buildResponse(sessionId, response.toString(), ChatResponse.ResponseType.CLARIFICATION, 
                null, null, commonScenarios, null, executionId);
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
        String formattedResponse = intelligenceClient.formatResponse(
                intent.getScenario(), result, request.getQuery()).block();
        saveMessage(sessionId, "assistant", formattedResponse);

        // Log successful audit (async - non-blocking)
        auditService.logAuditAsync(executionId, request.getUserId(), intent.getScenario(), requestTime,
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
        return lower.matches(AFFIRMATIVE_PATTERN);
    }

    /**
     * Extract selected scenario from clarification response.
     * Uses dynamic scenarios from database instead of hardcoded values.
     */
    private String extractSelectedScenario(ChatRequest request) {
        List<String> commonScenarios = validationService.getCommonScenarios();
        
        // Check explicit selection (1-based index)
        if (request.getSelectedOption() != null) {
            int index = request.getSelectedOption() - 1;
            if (index >= 0 && index < commonScenarios.size()) {
                return commonScenarios.get(index);
            }
            return null;
        }
        
        // Try to extract from query text - match against scenario codes and descriptions
        String query = request.getQuery().toLowerCase();
        
        // Check for number selection
        for (int i = 0; i < commonScenarios.size(); i++) {
            if (query.contains(String.valueOf(i + 1))) {
                return commonScenarios.get(i);
            }
        }
        
        // Check for keyword matches from scenario descriptions
        for (String scenarioCode : commonScenarios) {
            String description = validationService.getScenarioDescription(scenarioCode).toLowerCase();
            // Check if query contains keywords from description
            String[] keywords = description.split("\\s+");
            for (String keyword : keywords) {
                if (keyword.length() > 3 && query.contains(keyword)) {
                    return scenarioCode;
                }
            }
            // Also check scenario code words
            String codeWords = scenarioCode.toLowerCase().replace("_", " ");
            if (query.contains(codeWords) || query.contains(scenarioCode.toLowerCase())) {
                return scenarioCode;
            }
        }
        
        return null;
    }

    private String getOrCreateSession(ChatRequest request) {
        if (request.getSessionId() != null) {
            Optional<ChatSession> existing = sessionRepository.findBySessionId(request.getSessionId());
            if (existing.isPresent()) {
                // Update lastActivityAt to track session activity
                ChatSession session = existing.get();
                session.setLastActivityAt(Instant.now());
                sessionRepository.save(session);
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
}
