package com.enterprise.ai.api.service;

import com.enterprise.ai.common.dto.*;
import com.enterprise.ai.core.router.DynamicScenarioRouter;
import com.enterprise.ai.data.entity.AiAuditLog;
import com.enterprise.ai.data.entity.ChatMessage;
import com.enterprise.ai.data.entity.ChatSession;
import com.enterprise.ai.data.repository.AiAuditLogRepository;
import com.enterprise.ai.data.repository.ChatMessageRepository;
import com.enterprise.ai.data.repository.ChatSessionRepository;
import com.enterprise.ai.llm.client.ReactiveLlmClient;
import com.enterprise.ai.llm.config.OllamaProperties;
import com.enterprise.ai.security.rbac.RbacService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Reactive chat service with non-blocking Ollama calls and SSE streaming support.
 * Uses DynamicScenarioRouter for config-driven scenario execution.
 * Implements 3-layer protection system with performance logging.
 */
@Slf4j
@Service
public class ReactiveChatService {

    private static final String AFFIRMATIVE_PATTERN = 
            "(yes|y|yeah|yep|sure|ok|okay|proceed|confirm|haan|ha|theek hai).*";

    private final ReactiveLlmClient llmClient;
    private final DynamicScenarioRouter scenarioRouter;
    private final RbacService rbacService;
    private final IntentValidationService validationService;
    private final PerformanceLoggingService perfService;
    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final AiAuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;
    private final OllamaProperties ollamaProperties;

    // Configurable runtime protection limits
    private final long maxExecutionTimeMs;
    private final long maxOllamaTimeoutMs;
    private final long maxDbTimeoutMs;

    // Dry-run mode flag
    private final boolean dryRunMode;

    public ReactiveChatService(
            ReactiveLlmClient llmClient,
            DynamicScenarioRouter scenarioRouter,
            RbacService rbacService,
            IntentValidationService validationService,
            PerformanceLoggingService perfService,
            ChatSessionRepository sessionRepository,
            ChatMessageRepository messageRepository,
            AiAuditLogRepository auditLogRepository,
            ObjectMapper objectMapper,
            OllamaProperties ollamaProperties,
            @Value("${runtime.protection.max-execution-time-ms:60000}") long maxExecutionTimeMs,
            @Value("${runtime.protection.max-ollama-timeout-ms:120000}") long maxOllamaTimeoutMs,
            @Value("${runtime.protection.max-db-timeout-ms:5000}") long maxDbTimeoutMs,
            @Value("${runtime.dry-run-mode:false}") boolean dryRunMode) {
        this.llmClient = llmClient;
        this.scenarioRouter = scenarioRouter;
        this.rbacService = rbacService;
        this.validationService = validationService;
        this.perfService = perfService;
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
        this.ollamaProperties = ollamaProperties;
        this.maxExecutionTimeMs = maxExecutionTimeMs;
        this.maxOllamaTimeoutMs = maxOllamaTimeoutMs;
        this.maxDbTimeoutMs = maxDbTimeoutMs;
        this.dryRunMode = dryRunMode;
    }

    /**
     * Process chat request reactively (non-blocking).
     */
    public Mono<ChatResponse> processChatReactive(ChatRequest request) {
        return processChatReactive(request, request.isDryRun() || dryRunMode);
    }

    /**
     * Process chat request with explicit dry-run mode.
     */
    public Mono<ChatResponse> processChatReactive(ChatRequest request, boolean dryRun) {
        String executionId = UUID.randomUUID().toString();
        PerformanceLoggingService.ExecutionTracker tracker = perfService.startTracking(executionId)
                .withUserId(request.getUserId());
        
        return Mono.defer(() -> {
            Instant requestTime = Instant.now();
            
            // Get or create session
            String sessionId = getOrCreateSessionSync(request);
            
            // Handle confirmation/clarification responses
            if (request.isConfirmationResponse() || request.isClarificationResponse()) {
                return handleSpecialResponses(request, sessionId, executionId, tracker, dryRun);
            }
            
            // Save user message
            saveMessageSync(sessionId, "user", request.getQuery());
            
            // Get session context
            String sessionContext = getSessionContextSync(sessionId);
            
            // Intent detection (non-blocking)
            tracker.startIntentDetection();
            
            Mono<IntentResult> intentMono = ollamaProperties.isTwoStageDetection()
                    ? llmClient.detectIntentTwoStage(request.getQuery(), sessionContext)
                    : llmClient.detectIntent(request.getQuery(), sessionContext);
            
            return intentMono
                    .timeout(Duration.ofMillis(maxOllamaTimeoutMs))
                    .doOnSuccess(intent -> {
                        tracker.endIntentDetection();
                        tracker.withScenario(intent.getScenario());
                        log.info("Detected intent: scenario={}, confidence={}", 
                                intent.getScenario(), intent.getConfidence());
                    })
                    .flatMap(intent -> processIntent(intent, request, sessionId, executionId, tracker, requestTime, dryRun))
                    .doOnSuccess(response -> tracker.complete())
                    .doOnError(e -> {
                        tracker.markFailed();
                        tracker.complete();
                    });
        })
        .timeout(Duration.ofMillis(maxExecutionTimeMs))
        .onErrorResume(e -> {
            log.error("Error processing chat request: {}", e.getMessage());
            return Mono.just(buildErrorResponse(request.getSessionId(), 
                    "An error occurred processing your request. Please try again."));
        });
    }

    /**
     * Process chat with streaming response (SSE).
     *
     * CRITICAL: Authentication and authorization checks MUST happen BEFORE sending any data
     * to prevent "response already committed" Spring Security errors.
     */
    public Flux<String> processChatStreaming(ChatRequest request) {
        String executionId = UUID.randomUUID().toString();
        
        log.debug("Starting streaming chat processing for user: {}", request.getUserId());

        // Pre-flight authentication check BEFORE sending any data
        List<String> userRoles = getCurrentUserRoles();
        if (userRoles.isEmpty()) {
            log.error("No user roles found! Authentication may have failed.");
            return Flux.just("❌ Authentication required. Please log in again.");
        }
        log.debug("Pre-flight auth check passed: user has roles {}", userRoles);

        // Cache the intent detection to avoid re-execution
        Mono<IntentResult> intentMono = Mono.defer(() -> {
            String sessionId = getOrCreateSessionSync(request);
            saveMessageSync(sessionId, "user", request.getQuery());
            String sessionContext = getSessionContextSync(sessionId);
            
            return llmClient.detectIntent(request.getQuery(), sessionContext)
                    .timeout(Duration.ofMillis(maxOllamaTimeoutMs));
        })
        .doOnSuccess(intent -> log.info("Intent detected for streaming: scenario={}, confidence={}",
                intent.getScenario(), intent.getConfidence()))
        .doOnError(e -> log.error("Intent detection failed in streaming: {}", e.getMessage()))
        .cache(); // Cache to avoid re-executing intent detection

        // Now build the response flux with progress indicators
        return Flux.concat(
                // Send initial progress AFTER auth check
                Flux.just("🔍 Analyzing your request...\n"),

                intentMono.flatMapMany(intent -> {
                    // Validate intent
                    IntentValidationService.ValidationResult validation =
                            validationService.validate(intent, request.getQuery());

                    if (!validation.isValid()) {
                        String message = validation.validationMessage();
                        log.debug("Validation failed, returning error message");
                        return Flux.just("\n" + message);
                    }

                    // Authorization check (roles already validated above)
                    if (!rbacService.isAnyRoleAuthorized(userRoles, intent.getScenario())) {
                        log.warn("User with roles {} not authorized for scenario: {}", userRoles, intent.getScenario());
                        return Flux.just(String.format("\n❌ You don't have permission to access this information.\nYour roles: %s\nRequired scenario: %s", userRoles, intent.getScenario()));
                    }

                    log.debug("Authorization successful for user with roles: {}", userRoles);

                    // Execute scenario
                    ScenarioRequest scenarioRequest = ScenarioRequest.builder()
                            .scenario(intent.getScenario())
                            .params(intent.getParams())
                            .userId(request.getUserId())
                            .build();

                    log.debug("Executing scenario reactively: {}", intent.getScenario());

                    return Flux.concat(
                            Flux.just("\n📊 Fetching data...\n"),
                            scenarioRouter.routeReactive(scenarioRequest)
                                    .timeout(Duration.ofMillis(maxDbTimeoutMs))
                                    .doOnSuccess(result -> log.debug("Scenario executed, starting streaming response"))
                                    .doOnError(e -> log.error("Scenario execution failed: {}", e.getMessage()))
                                    .flatMapMany(result -> {
                                        log.debug("Formatting response as stream");
                                        return Flux.concat(
                                                Flux.just("\n"),
                                                llmClient.formatResponseStreaming(intent.getScenario(), result, request.getQuery())
                                                        .doOnComplete(() -> log.debug("Response streaming completed"))
                                                        .doOnError(e -> log.error("Response streaming error: {}", e.getMessage()))
                                        );
                                    })
                    );
                })
        )
        .timeout(Duration.ofMillis(maxExecutionTimeMs),
                Flux.just("\n⏱️ Request timeout. The operation is taking longer than expected. Please try again."))
        .onErrorResume(e -> {
            log.error("Streaming error: {}", e.getMessage(), e);
            return Flux.just("\n❌ An error occurred: " + e.getMessage() + "\nPlease try again.");
        });
    }


    private Mono<ChatResponse> processIntent(
            IntentResult intent, 
            ChatRequest request, 
            String sessionId,
            String executionId,
            PerformanceLoggingService.ExecutionTracker tracker,
            Instant requestTime,
            boolean dryRun) {
        
        // Validation
        tracker.startValidation();
        IntentValidationService.ValidationResult validation = 
                validationService.validate(intent, request.getQuery());
        tracker.endValidation();

        if (!validation.isValid()) {
            return handleValidationFailure(validation, intent, request, sessionId, executionId);
        }

        // Authorization check
        List<String> userRoles = getCurrentUserRoles();
        if (!rbacService.isAnyRoleAuthorized(userRoles, intent.getScenario())) {
            String response = "You don't have permission to access this information.";
            saveMessageSync(sessionId, "assistant", response);
            return Mono.just(buildResponse(sessionId, response, ChatResponse.ResponseType.ERROR, 
                    null, intent.getScenario(), null, intent.getConfidence(), executionId));
        }

        // Execute scenario (non-blocking)
        return executeScenarioReactive(intent, request, sessionId, executionId, tracker, requestTime, dryRun);
    }

    private Mono<ChatResponse> executeScenarioReactive(
            IntentResult intent,
            ChatRequest request,
            String sessionId,
            String executionId,
            PerformanceLoggingService.ExecutionTracker tracker,
            Instant requestTime,
            boolean dryRun) {
        
        ScenarioRequest scenarioRequest = ScenarioRequest.builder()
                .scenario(intent.getScenario())
                .params(intent.getParams())
                .userId(request.getUserId())
                .sessionId(sessionId)
                .build();

        tracker.startDbExecution();
        
        // Use DynamicScenarioRouter with dry-run support
        return scenarioRouter.routeReactive(scenarioRequest, dryRun)
                .timeout(Duration.ofMillis(maxDbTimeoutMs))
                .doOnSuccess(r -> tracker.endDbExecution())
                .flatMap(result -> {
                    // Format response (non-blocking)
                    tracker.startFormatting();
                    return llmClient.formatResponse(intent.getScenario(), result, request.getQuery())
                            .timeout(Duration.ofMillis(maxOllamaTimeoutMs))
                            .doOnSuccess(r -> tracker.endFormatting())
                            .map(formattedResponse -> {
                                saveMessageSync(sessionId, "assistant", formattedResponse);
                                logAuditAsync(executionId, request.getUserId(), intent.getScenario(), 
                                        requestTime, Instant.now(), true, null, intent, result);
                                
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
                            });
                })
                .onErrorResume(e -> {
                    log.error("Scenario execution failed: {}", e.getMessage());
                    tracker.endDbExecution();
                    return Mono.just(buildErrorResponse(sessionId, 
                            "Failed to process your request. Please try again."));
                });
    }

    private Mono<ChatResponse> handleSpecialResponses(
            ChatRequest request, 
            String sessionId, 
            String executionId,
            PerformanceLoggingService.ExecutionTracker tracker,
            boolean dryRun) {
        
        if (request.isConfirmationResponse()) {
            saveMessageSync(sessionId, "user", request.getQuery());
            
            if (Boolean.TRUE.equals(request.getConfirmed()) || 
                    isAffirmativeResponse(request.getQuery())) {
                if (request.getPendingScenario() != null && request.getPendingActionParams() != null) {
                    IntentResult intent = IntentResult.builder()
                            .scenario(request.getPendingScenario())
                            .confidence(1.0)
                            .params(request.getPendingActionParams())
                            .missingParams(List.of())
                            .build();
                    tracker.withScenario(intent.getScenario());
                    return executeScenarioReactive(intent, request, sessionId, executionId, tracker, Instant.now(), dryRun);
                }
            }
            
            String response = "Okay, I've cancelled that action. How else can I help you?";
            saveMessageSync(sessionId, "assistant", response);
            return Mono.just(buildResponse(sessionId, response, ChatResponse.ResponseType.DIRECT, 
                    null, null, null, null, executionId));
        }
        
        // Handle clarification response
        return handleClarificationResponseReactive(request, sessionId, executionId, tracker, dryRun);
    }

    private Mono<ChatResponse> handleClarificationResponseReactive(
            ChatRequest request, String sessionId, String executionId,
            PerformanceLoggingService.ExecutionTracker tracker,
            boolean dryRun) {
        
        saveMessageSync(sessionId, "user", request.getQuery());
        
        String selectedScenario = extractSelectedScenario(request);
        if (selectedScenario != null) {
            tracker.withScenario(selectedScenario);
            String sessionContext = getSessionContextSync(sessionId);
            
            return llmClient.detectIntent(request.getQuery(), sessionContext)
                    .map(detectedIntent -> IntentResult.builder()
                            .scenario(selectedScenario)
                            .confidence(0.95)
                            .params(detectedIntent.getParams())
                            .missingParams(detectedIntent.getMissingParams())
                            .reasoning("User selected from clarification options")
                            .build())
                    .flatMap(intent -> {
                        IntentValidationService.ValidationResult validation = 
                                validationService.validate(intent, request.getQuery());
                        
                        if (!validation.missingRequiredParams().isEmpty()) {
                            return llmClient.generateFollowUpQuestion(selectedScenario, validation.missingRequiredParams())
                                    .map(followUp -> {
                                        saveMessageSync(sessionId, "assistant", followUp);
                                        return ChatResponse.builder()
                                                .sessionId(sessionId)
                                                .message(followUp)
                                                .responseType(ChatResponse.ResponseType.FOLLOW_UP)
                                                .followUpRequired(true)
                                                .missingParams(validation.missingRequiredParams())
                                                .scenario(selectedScenario)
                                                .build();
                                    });
                        }
                        
                        return executeScenarioReactive(intent, request, sessionId, executionId, tracker, Instant.now(), dryRun);
                    });
        }
        
        String response = "I didn't understand your selection. Please reply with a number (1, 2, or 3).";
        saveMessageSync(sessionId, "assistant", response);
        return Mono.just(buildResponse(sessionId, response, ChatResponse.ResponseType.CLARIFICATION, 
                null, null, List.of("TXN_STATUS", "FILE_STATUS", "ACCOUNT_SUMMARY"), null, executionId));
    }

    private Mono<ChatResponse> handleValidationFailure(
            IntentValidationService.ValidationResult validation,
            IntentResult intent,
            ChatRequest request,
            String sessionId,
            String executionId) {
        
        if (validation.hasLowConfidence()) {
            String response = "I'm not fully sure what you're asking for. Could you please:\n" +
                    "• Be more specific about what you want to check\n" +
                    "• Include relevant IDs or names\n" +
                    "• Or tell me if you want to check:\n" +
                    "  1. Transaction status\n" +
                    "  2. File processing status\n" +
                    "  3. Account balance";
            saveMessageSync(sessionId, "assistant", response);
            return Mono.just(buildResponse(sessionId, response, ChatResponse.ResponseType.CLARIFICATION,
                    null, null, List.of("TXN_STATUS", "FILE_STATUS", "ACCOUNT_SUMMARY"), 
                    intent.getConfidence(), executionId));
        }

        if (validation.isAmbiguous()) {
            StringBuilder response = new StringBuilder();
            response.append(validation.validationMessage()).append("\n\n");
            response.append("Please select an option:\n");
            List<String> scenarios = validation.suggestedScenarios();
            for (int i = 0; i < scenarios.size(); i++) {
                response.append(String.format("%d. %s\n", i + 1, 
                        validationService.getScenarioDescription(scenarios.get(i))));
            }
            
            saveMessageSync(sessionId, "assistant", response.toString());
            return Mono.just(buildResponse(sessionId, response.toString(), ChatResponse.ResponseType.CLARIFICATION,
                    null, null, scenarios, intent.getConfidence(), executionId));
        }

        if (!validation.missingRequiredParams().isEmpty()) {
            return llmClient.generateFollowUpQuestion(intent.getScenario(), validation.missingRequiredParams())
                    .map(followUp -> {
                        saveMessageSync(sessionId, "assistant", followUp);
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
                    });
        }

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
            
            saveMessageSync(sessionId, "assistant", confirmationMsg);
            
            return Mono.just(ChatResponse.builder()
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
                    .build());
        }

        String response = "I couldn't process your request. Please try rephrasing.";
        saveMessageSync(sessionId, "assistant", response);
        return Mono.just(buildErrorResponse(sessionId, response));
    }

    // ===== HELPER METHODS =====

    private boolean isAffirmativeResponse(String response) {
        if (response == null) return false;
        return response.toLowerCase().trim().matches(AFFIRMATIVE_PATTERN);
    }

    private String extractSelectedScenario(ChatRequest request) {
        if (request.getSelectedOption() != null) {
            return switch (request.getSelectedOption()) {
                case 1 -> "TXN_STATUS";
                case 2 -> "FILE_STATUS";
                case 3 -> "ACCOUNT_SUMMARY";
                default -> null;
            };
        }
        
        String query = request.getQuery().toLowerCase();
        if (query.contains("1") || query.contains("transaction") || query.contains("payment")) {
            return "TXN_STATUS";
        }
        if (query.contains("2") || query.contains("file") || query.contains("batch")) {
            return "FILE_STATUS";
        }
        if (query.contains("3") || query.contains("balance") || query.contains("account")) {
            return "ACCOUNT_SUMMARY";
        }
        return null;
    }

    private String getOrCreateSessionSync(ChatRequest request) {
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

    private void saveMessageSync(String sessionId, String role, String content) {
        ChatMessage message = ChatMessage.builder()
                .sessionId(sessionId)
                .role(role)
                .content(content)
                .build();
        messageRepository.save(message);
    }

    private String getSessionContextSync(String sessionId) {
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
        if (auth == null) {
            log.error("No authentication found in SecurityContext");
            return List.of();
        }
        if (auth.getAuthorities() == null || auth.getAuthorities().isEmpty()) {
            log.warn("Authentication found but no authorities: principal={}, authenticated={}",
                    auth.getPrincipal(), auth.isAuthenticated());
            return List.of();
        }
        List<String> roles = auth.getAuthorities().stream()
                .map(a -> a.getAuthority().replace("ROLE_", ""))
                .toList();
        log.debug("Extracted roles from authentication: {}", roles);
        return roles;
    }

    private void logAuditAsync(String executionId, String userId, String scenarioCode,
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
