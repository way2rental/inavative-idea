package com.enterprise.ai.api.service;

import com.enterprise.ai.common.dto.ChatRequest;
import com.enterprise.ai.common.dto.ChatResponse;
import com.enterprise.ai.common.dto.KernelResponse;
import com.enterprise.ai.common.dto.ReasoningPlan;
import com.enterprise.ai.data.repository.ChatMessageRepository;
import com.enterprise.ai.data.repository.ChatSessionRepository;
import com.enterprise.ai.intelligence.kernel.AxisAiKernel;
import com.enterprise.ai.security.rbac.RbacService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Adapter service that connects ChatService/ReactiveChatService to AxisAiKernel.
 * 
 * Handles:
 * - Request conversion (ChatRequest → Kernel input)
 * - Response conversion (KernelResponse → ChatResponse)
 * - Session management
 * - RBAC filtering
 * - Follow-up question handling
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KernelAdapterService {

    private final AxisAiKernel axisAiKernel;
    private final RbacService rbacService;
    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;

    /**
     * Process chat request using AxisAiKernel (DLM-based).
     */
    public Mono<ChatResponse> processWithKernel(ChatRequest request) {
        try {
            // Get or create session
            String sessionId = getOrCreateSession(request);
            
            // Get session context
            String sessionContext = getSessionContext(sessionId);
            
            // Get user roles and allowed scenarios
            List<String> userRoles = getCurrentUserRoles();
            Set<String> allowedScenarios = getAllowedScenarios(userRoles);
            
            // Process through kernel
            return axisAiKernel.process(
                    request.getQuery(),
                    sessionContext,
                    allowedScenarios,
                    request.getUserId(),
                    sessionId,
                    userRoles
            )
            .map(kernelResponse -> convertToChatResponse(kernelResponse, request, sessionId));
            
        } catch (Exception e) {
            log.error("Error processing request through kernel: {}", e.getMessage(), e);
            return Mono.just(buildErrorResponse(request.getSessionId() != null ? request.getSessionId() : UUID.randomUUID().toString(),
                    "An error occurred processing your request. Please try again."));
        }
    }

    /**
     * Convert KernelResponse to ChatResponse.
     */
    private ChatResponse convertToChatResponse(KernelResponse kernelResponse, ChatRequest request, String sessionId) {
        ReasoningPlan reasoningPlan = kernelResponse.getReasoningPlan();
        
        // Determine response type
        ChatResponse.ResponseType responseType = determineResponseType(kernelResponse, reasoningPlan);
        
        // Build response
        ChatResponse.ChatResponseBuilder builder = ChatResponse.builder()
                .sessionId(sessionId)
                .message(kernelResponse.getResponseText())
                .responseType(responseType)
                .scenario(reasoningPlan != null ? reasoningPlan.getIntent() : null)
                .confidence(reasoningPlan != null ? reasoningPlan.getConfidence() : null)
                .followUpRequired(responseType != ChatResponse.ResponseType.DIRECT && 
                                 responseType != ChatResponse.ResponseType.ERROR);

        // Handle missing parameters (follow-up required)
        if (reasoningPlan != null && reasoningPlan.getMissingParameters() != null && 
            !reasoningPlan.getMissingParameters().isEmpty()) {
            builder.missingParams(reasoningPlan.getMissingParameters());
            builder.responseType(ChatResponse.ResponseType.FOLLOW_UP);
            builder.followUpRequired(true);
        }

        // Handle blocked response
        if (Boolean.TRUE.equals(kernelResponse.getBlocked())) {
            builder.responseType(ChatResponse.ResponseType.ERROR);
            builder.followUpRequired(false);
            if (kernelResponse.getComplianceResult() != null && 
                kernelResponse.getComplianceResult().getBlockingMessage() != null) {
                builder.message(kernelResponse.getComplianceResult().getBlockingMessage());
            }
        }

        // Add metadata
        ChatResponse.ResponseMeta meta = ChatResponse.ResponseMeta.builder()
                .executionId(kernelResponse.getMetadata() != null ? 
                        kernelResponse.getMetadata().get("executionId") != null ? 
                        kernelResponse.getMetadata().get("executionId").toString() : null : null)
                .intentConfidence(reasoningPlan != null ? reasoningPlan.getConfidence() : null)
                .intentReasoning(reasoningPlan != null ? reasoningPlan.getReasoning() : null)
                .additionalInfo(kernelResponse.getMetadata())
                .build();
        builder.meta(meta);

        return builder.build();
    }

    /**
     * Determine ChatResponse type from KernelResponse.
     */
    private ChatResponse.ResponseType determineResponseType(KernelResponse kernelResponse, ReasoningPlan reasoningPlan) {
        if (Boolean.TRUE.equals(kernelResponse.getBlocked())) {
            return ChatResponse.ResponseType.ERROR;
        }

        if (reasoningPlan == null) {
            return ChatResponse.ResponseType.DIRECT;
        }

        // Check for missing parameters
        if (reasoningPlan.getMissingParameters() != null && !reasoningPlan.getMissingParameters().isEmpty()) {
            return ChatResponse.ResponseType.FOLLOW_UP;
        }

        // Check for low confidence (clarification needed)
        if (reasoningPlan.getConfidence() != null && reasoningPlan.getConfidence() < 0.6) {
            return ChatResponse.ResponseType.CLARIFICATION;
        }

        // Check for unknown intent
        if ("UNKNOWN".equals(reasoningPlan.getIntent()) || "AMBIGUOUS".equals(reasoningPlan.getIntent())) {
            return ChatResponse.ResponseType.CLARIFICATION;
        }

        return ChatResponse.ResponseType.DIRECT;
    }

    /**
     * Get allowed scenarios for user roles.
     */
    private Set<String> getAllowedScenarios(List<String> userRoles) {
        if (userRoles == null || userRoles.isEmpty()) {
            return Set.of();
        }

        Set<String> allowedScenarios = new HashSet<>();
        for (String role : userRoles) {
            Set<String> roleScenarios = rbacService.getAllowedScenarios(role);
            if (roleScenarios != null) {
                allowedScenarios.addAll(roleScenarios);
            }
        }

        return allowedScenarios;
    }

    /**
     * Get or create session.
     */
    private String getOrCreateSession(ChatRequest request) {
        if (request.getSessionId() != null) {
            Optional<com.enterprise.ai.data.entity.ChatSession> existing = 
                    sessionRepository.findBySessionId(request.getSessionId());
            if (existing.isPresent()) {
                com.enterprise.ai.data.entity.ChatSession session = existing.get();
                session.setLastActivityAt(java.time.Instant.now());
                sessionRepository.save(session);
                return request.getSessionId();
            }
        }

        String sessionId = UUID.randomUUID().toString();
        com.enterprise.ai.data.entity.ChatSession session = com.enterprise.ai.data.entity.ChatSession.builder()
                .sessionId(sessionId)
                .userId(request.getUserId())
                .build();
        sessionRepository.save(session);
        return sessionId;
    }

    /**
     * Get session context from chat history.
     */
    private String getSessionContext(String sessionId) {
        List<com.enterprise.ai.data.entity.ChatMessage> recentMessages = 
                messageRepository.findTop10BySessionIdOrderByTimestampDesc(sessionId);
        if (recentMessages.isEmpty()) {
            return "No previous context";
        }

        StringBuilder context = new StringBuilder();
        Collections.reverse(recentMessages);
        for (com.enterprise.ai.data.entity.ChatMessage msg : recentMessages) {
            context.append(msg.getRole()).append(": ").append(msg.getContent()).append("\n");
        }
        return context.toString();
    }

    /**
     * Get current user roles from security context.
     */
    private List<String> getCurrentUserRoles() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getAuthorities() == null) {
            return List.of();
        }
        return auth.getAuthorities().stream()
                .map(a -> a.getAuthority().replace("ROLE_", ""))
                .collect(Collectors.toList());
    }

    /**
     * Build error response.
     */
    private ChatResponse buildErrorResponse(String sessionId, String message) {
        return ChatResponse.builder()
                .sessionId(sessionId)
                .message(message)
                .responseType(ChatResponse.ResponseType.ERROR)
                .followUpRequired(false)
                .build();
    }
}
