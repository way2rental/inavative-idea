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
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final LlmClient llmClient;
    private final ScenarioRouter scenarioRouter;
    private final RbacService rbacService;
    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final AiAuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    /**
     * Process a chat request.
     */
    public ChatResponse processChat(ChatRequest request) {
        String executionId = UUID.randomUUID().toString();
        Instant requestTime = Instant.now();

        try {
            // Get or create session
            String sessionId = getOrCreateSession(request);

            // Save user message
            saveMessage(sessionId, "user", request.getQuery());

            // Get session context
            String sessionContext = getSessionContext(sessionId);

            // Detect intent
            IntentResult intent = llmClient.detectIntent(request.getQuery(), sessionContext);
            log.info("Detected intent: {}", intent);

            // Check if we have a valid scenario
            if (intent.getScenario() == null || "UNKNOWN".equals(intent.getScenario())) {
                String response = "I'm not sure how to help with that. Could you please rephrase your request?";
                saveMessage(sessionId, "assistant", response);
                return buildResponse(sessionId, response, false, null, null, executionId);
            }

            // Check authorization
            List<String> userRoles = getCurrentUserRoles();
            if (!rbacService.isAnyRoleAuthorized(userRoles, intent.getScenario())) {
                String response = "You don't have permission to access this information.";
                saveMessage(sessionId, "assistant", response);
                return buildResponse(sessionId, response, false, null, intent.getScenario(), executionId);
            }

            // Check for missing parameters
            if (intent.getMissingParams() != null && !intent.getMissingParams().isEmpty()) {
                String followUp = llmClient.generateFollowUpQuestion(intent.getScenario(), intent.getMissingParams());
                saveMessage(sessionId, "assistant", followUp);
                return buildResponse(sessionId, followUp, true, intent.getMissingParams(), intent.getScenario(), executionId);
            }

            // Execute scenario
            ScenarioRequest scenarioRequest = ScenarioRequest.builder()
                    .scenario(intent.getScenario())
                    .params(intent.getParams())
                    .userId(request.getUserId())
                    .sessionId(sessionId)
                    .build();

            ScenarioResult result = scenarioRouter.route(scenarioRequest);

            // Format response
            String formattedResponse = llmClient.formatResponse(intent.getScenario(), result, request.getQuery());
            saveMessage(sessionId, "assistant", formattedResponse);

            // Log audit
            logAudit(executionId, request.getUserId(), intent.getScenario(), requestTime, 
                    Instant.now(), true, null, intent, result);

            return buildResponse(sessionId, formattedResponse, false, null, intent.getScenario(), executionId);

        } catch (Exception e) {
            log.error("Error processing chat request", e);
            logAudit(executionId, request.getUserId(), null, requestTime, 
                    Instant.now(), false, e.getMessage(), null, null);
            return buildErrorResponse(request.getSessionId(), "An error occurred processing your request. Please try again.");
        }
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

    private ChatResponse buildResponse(String sessionId, String message, boolean followUpRequired,
                                       List<String> missingParams, String scenario, String executionId) {
        return ChatResponse.builder()
                .sessionId(sessionId)
                .message(message)
                .followUpRequired(followUpRequired)
                .missingParams(missingParams)
                .scenario(scenario)
                .meta(ChatResponse.ResponseMeta.builder()
                        .executionId(executionId)
                        .build())
                .build();
    }

    private ChatResponse buildErrorResponse(String sessionId, String message) {
        return ChatResponse.builder()
                .sessionId(sessionId)
                .message(message)
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
