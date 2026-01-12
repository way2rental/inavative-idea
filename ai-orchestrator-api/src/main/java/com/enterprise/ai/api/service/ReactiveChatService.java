package com.enterprise.ai.api.service;

import com.enterprise.ai.common.dto.*;
import com.enterprise.ai.core.router.DynamicScenarioRouter;
import com.enterprise.ai.data.entity.AiAuditLog;
import com.enterprise.ai.data.entity.ChatMessage;
import com.enterprise.ai.data.entity.ChatSession;
import com.enterprise.ai.data.repository.AiAuditLogRepository;
import com.enterprise.ai.data.repository.ChatMessageRepository;
import com.enterprise.ai.data.repository.ChatSessionRepository;
import com.enterprise.ai.llm.client.ConversationalAiService;
import com.enterprise.ai.intelligence.client.ReactiveIntelligenceClient;
// Removed: import com.enterprise.ai.llm.config.OllamaProperties; - No longer needed
import com.enterprise.ai.security.rbac.RbacService;
import com.fasterxml.jackson.core.type.TypeReference;
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
 * Reactive chat service with non-blocking LLM calls and SSE streaming support.
 * Uses DynamicScenarioRouter for config-driven scenario execution.
 * Implements 3-layer protection system with performance logging.
 * 
 * Axis AI: Uses LLMs as interchangeable reasoning engines (API-based only).
 */
@Slf4j
@Service
public class ReactiveChatService {

    private static final String AFFIRMATIVE_PATTERN = 
            "(yes|y|yeah|yep|sure|ok|okay|proceed|confirm|haan|ha|theek hai).*";

    private final com.enterprise.ai.intelligence.client.ReactiveIntelligenceClient intelligenceClient;
    private final KernelAdapterService kernelAdapterService;
    private final DynamicScenarioRouter scenarioRouter;
    private final RbacService rbacService;
    private final IntentValidationService validationService;
    private final PerformanceLoggingService perfService;
    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final AiAuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;
    private final ConversationalAiService conversationalAiService;

    // Configurable runtime protection limits
    private final long maxExecutionTimeMs;
    private final long maxLlmTimeoutMs;
    private final long maxDbTimeoutMs;
    private final boolean twoStageDetection;

    // Dry-run mode flag
    private final boolean dryRunMode;

    public ReactiveChatService(
            com.enterprise.ai.intelligence.client.ReactiveIntelligenceClient intelligenceClient,
            KernelAdapterService kernelAdapterService,
            DynamicScenarioRouter scenarioRouter,
            RbacService rbacService,
            IntentValidationService validationService,
            PerformanceLoggingService perfService,
            ChatSessionRepository sessionRepository,
            ChatMessageRepository messageRepository,
            AiAuditLogRepository auditLogRepository,
            ObjectMapper objectMapper,
            com.enterprise.ai.llm.client.ConversationalAiService conversationalAiService,
            @Value("${runtime.protection.max-execution-time-ms:60000}") long maxExecutionTimeMs,
            @Value("${runtime.protection.max-llm-timeout-ms:120000}") long maxLlmTimeoutMs,
            @Value("${runtime.protection.max-db-timeout-ms:5000}") long maxDbTimeoutMs,
            @Value("${llm.two-stage-detection:false}") boolean twoStageDetection,
            @Value("${runtime.dry-run-mode:false}") boolean dryRunMode) {
        this.intelligenceClient = intelligenceClient;
        this.kernelAdapterService = kernelAdapterService;
        this.scenarioRouter = scenarioRouter;
        this.rbacService = rbacService;
        this.validationService = validationService;
        this.perfService = perfService;
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
        this.conversationalAiService = conversationalAiService;
        this.maxExecutionTimeMs = maxExecutionTimeMs;
        this.maxLlmTimeoutMs = maxLlmTimeoutMs;
        this.maxDbTimeoutMs = maxDbTimeoutMs;
        this.twoStageDetection = twoStageDetection;
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
            
            // Use DLM/AI Kernel (AxisAiKernel) for processing through KernelAdapterService
            // This replaces the old ReactiveIntelligenceClient flow with the 10-stage pipeline
            log.info("Processing request through DLM/AI Kernel (AxisAiKernel) for reactive flow");
            
            tracker.startIntentDetection();
            
            return kernelAdapterService.processWithKernel(request)
                    .timeout(Duration.ofMillis(maxExecutionTimeMs))
                    .doOnSuccess(response -> {
                        tracker.endIntentDetection();
                        if (response.getScenario() != null) {
                            tracker.withScenario(response.getScenario());
                        }
                        log.info("Kernel processing complete: scenario={}, type={}",
                                response.getScenario(), response.getResponseType());
                    })
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
     *
     * CONTEXT RETENTION: When asking for missing parameters, we store the pending scenario
     * in the session. When the user replies, we check for pending context first.
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

        // Get or create session BEFORE the reactive chain
        String sessionId = getOrCreateSessionSync(request);
        saveMessageSync(sessionId, "user", request.getQuery());
        String sessionContext = getSessionContextSync(sessionId);
        log.info("Processing chat for session: {}", sessionId);

        // Check for pending follow-up context (user is answering a previous question)
        ChatSession session = sessionRepository.findBySessionId(sessionId).orElse(null);
        if (session != null && session.hasPendingFollowUp()) {
            String pendingParams = session.getPendingParams();
            log.info("Found pending follow-up context: scenario={}, pendingParams={}",
                    session.getPendingScenario(), pendingParams);
            
            // Check if this is a confirmation follow-up (user confirming previous question)
            if ("CONFIRMATION".equals(pendingParams)) {
                // User is confirming - check if affirmative or providing additional info
                if (isAffirmativeResponse(request.getQuery()) || request.getQuery().length() < 20) {
                    // Short response like "type", "yes", "debit" - treat as confirmation with parameter
                    return Flux.concat(
                            Flux.just("[SESSION]" + sessionId),
                            Flux.just("[PROGRESS]✅ Confirmed\n"),
                            processPendingConfirmation(request, session, sessionContext, userRoles, executionId)
                    );
                }
            } else {
                // Regular follow-up for missing parameters
                return Flux.concat(
                        Flux.just("[SESSION]" + sessionId),
                        processPendingFollowUp(request, session, sessionContext, userRoles, executionId)
                );
            }
        }

        // Get last used params for context resolution (e.g., "same account", "that account")
        String lastUsedParamsJson = session != null ? session.getLastUsedParams() : null;
        if (lastUsedParamsJson != null && !lastUsedParamsJson.isEmpty()) {
            log.debug("Using last used params for context: {}", lastUsedParamsJson);
        }

        // Use DLM/AI Kernel (AxisAiKernel) for processing through KernelAdapterService
        // This replaces the old ReactiveIntelligenceClient flow with the 10-stage pipeline
        log.info("Processing request through DLM/AI Kernel (AxisAiKernel) for streaming");
        
        // Process through KernelAdapterService which uses AxisAiKernel
        Mono<ChatResponse> kernelResponseMono = kernelAdapterService.processWithKernel(request)
                .timeout(Duration.ofMillis(maxExecutionTimeMs))
                .doOnSuccess(response -> log.info("Kernel processing complete: scenario={}, type={}",
                        response.getScenario(), response.getResponseType()))
                .doOnError(e -> log.error("Kernel processing failed: {}", e.getMessage(), e))
                .cache(); // Cache to avoid re-execution

        // Convert Mono<ChatResponse> to Flux<String> with progress indicators
        return Flux.concat(
                // Session ID event for frontend to capture
                Flux.just("[SESSION]" + sessionId),
                // Stage 1: Initial analysis
                Flux.just("[PROGRESS]🔍 Analyzing your request...\n"),
                
                kernelResponseMono.flatMapMany(response -> {
                    log.debug("Converting kernel response to streaming format: scenario={}, type={}",
                            response.getScenario(), response.getResponseType());
                    
                    // Handle different response types
                    if (response.getResponseType() == ChatResponse.ResponseType.ERROR) {
                        // Error response
                        String errorMsg = response.getMessage() != null ? response.getMessage() : 
                                "An error occurred processing your request.";
                        saveMessageSync(sessionId, "assistant", errorMsg);
                        return Flux.just("[RESPONSE]" + errorMsg + "\n");
                    }
                    
                    if (response.getResponseType() == ChatResponse.ResponseType.FOLLOW_UP) {
                        // Follow-up required (missing parameters)
                        String followUpMsg = response.getMessage() != null ? response.getMessage() : 
                                "I need more information to help you.";
                        saveMessageSync(sessionId, "assistant", followUpMsg);
                        return Flux.concat(
                                Flux.just("[PROGRESS]✅ Request understood\n"),
                                Flux.just("[PROGRESS]❓ Need more information\n"),
                                Flux.just("[RESPONSE]" + followUpMsg + "\n")
                        );
                    }
                    
                    if (response.getResponseType() == ChatResponse.ResponseType.CLARIFICATION) {
                        // Clarification needed
                        String clarificationMsg = response.getMessage() != null ? response.getMessage() : 
                                "Could you please clarify your request?";
                        saveMessageSync(sessionId, "assistant", clarificationMsg);
                        return Flux.concat(
                                Flux.just("[PROGRESS]✅ Request understood\n"),
                                Flux.just("[PROGRESS]❓ Need clarification\n"),
                                Flux.just("[RESPONSE]" + clarificationMsg + "\n")
                        );
                    }
                    
                    // Direct response (success)
                    String responseMessage = response.getMessage();
                    if (responseMessage == null || responseMessage.trim().isEmpty()) {
                        responseMessage = "I've processed your request successfully.";
                    }
                    
                    // Save the complete assistant response
                    saveMessageSync(sessionId, "assistant", responseMessage);
                    
                    // Log successful execution to audit
                    Instant requestTime = Instant.now();
                    logAuditAsync(executionId, request.getUserId(), response.getScenario(),
                            requestTime, Instant.now(), true, null, null, null);
                    
                    // Return streaming response with progress indicators
                    return Flux.concat(
                            Flux.just("[PROGRESS]✅ Request understood\n"),
                            Flux.just("[PROGRESS]🔐 Permissions verified\n"),
                            Flux.just("[PROGRESS]📊 Processing complete\n"),
                            Flux.just("[PROGRESS]📝 Preparing your response...\n"),
                            Flux.just("[RESPONSE]" + responseMessage + "\n")
                    );
                })
        )
        .timeout(Duration.ofMillis(maxExecutionTimeMs),
                Flux.just("\n⏱️ **Request timeout.** The operation is taking longer than expected. Please try again."))
        .onErrorResume(e -> {
            log.error("Streaming error: {}", e.getMessage(), e);
            return Flux.just("\n❌ **An error occurred:** " + e.getMessage() + "\n\nPlease try again or contact support if the issue persists.");
        });
    }

    /**
     * Generate user-friendly missing parameter message as structured JSON.
     * Frontend expects FOLLOW_UP type structured response.
     * Keep it simple - just the question, no extra formatting.
     */
    private Flux<String> generateMissingParamMessage(String scenarioCode, List<String> missingParams, String userQuery) {
        return intelligenceClient.generateFollowUpQuestion(scenarioCode, missingParams)
                .map(question -> {
                    log.info("Generating structured follow-up for missing params: {}, question : {}", missingParams, question);
                    return "[RESPONSE]" + question;
                })
                .flatMapMany(Flux::just)
                .onErrorResume(e -> {
                    // Fallback to simple structured message if AI fails
                    log.error("Failed to generate follow-up question: {}", e.getMessage());
                    return Flux.just(generateSimpleMissingParamMessageAsJson(
                        scenarioCode, missingParams, "Could you please provide the required information?"));
                });
    }

    /**
     * Generate simple missing parameter message as structured JSON fallback.
     * Keep it simple - just a clean question.
     */
    private String generateSimpleMissingParamMessageAsJson(String scenarioCode, List<String> missingParams, String question) {
        try {
            List<String> formattedParams = missingParams.stream()
                    .map(param -> capitalize(param.replace("_", " ")))
                    .toList();

            // Simple, clean question - no extra formatting
            String cleanQuestion = question;

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("type", "FOLLOW_UP");
            response.put("title", "");
            response.put("confidence", 1.0);

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("question", cleanQuestion);
            payload.put("missingParams", formattedParams);
            response.put("payload", payload);
            response.put("scenario", scenarioCode);

            // Use [RESPONSE] marker so frontend detects this as structured response
            return "[RESPONSE]" + objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            log.error("Failed to create fallback JSON: {}", e.getMessage());
            // Last resort: minimal valid JSON with simple question - also with [RESPONSE] marker
            return "[RESPONSE]{\"type\":\"FOLLOW_UP\",\"title\":\"\",\"payload\":{\"question\":\"" + question + "\",\"missingParams\":[]},\"scenario\":\"" + scenarioCode + "\"}";
        }
    }

    /**
     * Capitalize first letter of a string.
     */
    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
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
        if (!rbacService.anyRoleAuthorized(userRoles, intent.getScenario())) {
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
                    return intelligenceClient.formatResponse(intent.getScenario(), result, request.getQuery())
                            .timeout(Duration.ofMillis(maxLlmTimeoutMs))
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
            
            return intelligenceClient.detectIntent(request.getQuery(), sessionContext)
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
                            return intelligenceClient.generateFollowUpQuestion(selectedScenario, validation.missingRequiredParams())
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
        
        // Couldn't understand the selection - show dynamic options
        List<String> commonScenarios = validationService.getCommonScenarios();
        StringBuilder response = new StringBuilder();
        response.append("I didn't understand your selection. Please reply with a number or describe what you're looking for:\n");
        for (int i = 0; i < commonScenarios.size(); i++) {
            response.append(String.format("%d. %s\n", i + 1, 
                    validationService.getScenarioDescription(commonScenarios.get(i))));
        }
        saveMessageSync(sessionId, "assistant", response.toString());
        return Mono.just(buildResponse(sessionId, response.toString(), ChatResponse.ResponseType.CLARIFICATION, 
                null, null, commonScenarios, null, executionId));
    }

    private Mono<ChatResponse> handleValidationFailure(
            IntentValidationService.ValidationResult validation,
            IntentResult intent,
            ChatRequest request,
            String sessionId,
            String executionId) {
        
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
            saveMessageSync(sessionId, "assistant", response.toString());
            return Mono.just(buildResponse(sessionId, response.toString(), ChatResponse.ResponseType.CLARIFICATION,
                    null, null, commonScenarios, 
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
            return intelligenceClient.generateFollowUpQuestion(intent.getScenario(), validation.missingRequiredParams())
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
            
            // Store confirmation state in session for follow-up handling
            storePendingConfirmation(sessionId, intent.getScenario(), params);
            
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

    /**
     * Store pending follow-up state in the session.
     * Called when we ask the user for missing parameters.
     */
    private void storePendingFollowUp(String sessionId, String scenarioCode, List<String> missingParams, Map<String, Object> collectedParams) {
        try {
            Optional<ChatSession> sessionOpt = sessionRepository.findBySessionId(sessionId);
            if (sessionOpt.isPresent()) {
                ChatSession session = sessionOpt.get();
                session.setPendingScenario(scenarioCode);
                session.setPendingParams(String.join(",", missingParams));
                if (collectedParams != null && !collectedParams.isEmpty()) {
                    session.setCollectedParams(objectMapper.writeValueAsString(collectedParams));
                }
                sessionRepository.save(session);
                log.info("Stored pending follow-up: scenario={}, missingParams={}", scenarioCode, missingParams);
            }
        } catch (Exception e) {
            log.error("Failed to store pending follow-up state: {}", e.getMessage());
        }
    }
    
    /**
     * Store pending confirmation state in the session.
     * Called when we ask the user to confirm before execution.
     */
    private void storePendingConfirmation(String sessionId, String scenarioCode, Map<String, Object> params) {
        try {
            Optional<ChatSession> sessionOpt = sessionRepository.findBySessionId(sessionId);
            if (sessionOpt.isPresent()) {
                ChatSession session = sessionOpt.get();
                // Store confirmation state - reuse pendingScenario and collectedParams
                session.setPendingScenario(scenarioCode);
                session.setPendingParams("CONFIRMATION"); // Special marker for confirmation
                if (params != null && !params.isEmpty()) {
                    session.setCollectedParams(objectMapper.writeValueAsString(params));
                }
                sessionRepository.save(session);
                log.info("Stored pending confirmation: scenario={}, params={}", scenarioCode, params);
            }
        } catch (Exception e) {
            log.error("Failed to store pending confirmation state: {}", e.getMessage());
        }
    }

    /**
     * Store last used params after successful scenario execution.
     * This enables context resolution like "same account", "that account".
     */
    private void storeLastUsedParams(String sessionId, Map<String, Object> params) {
        if (params == null || params.isEmpty()) {
            return;
        }
        try {
            Optional<ChatSession> sessionOpt = sessionRepository.findBySessionId(sessionId);
            if (sessionOpt.isPresent()) {
                ChatSession session = sessionOpt.get();
                session.setLastUsedParams(objectMapper.writeValueAsString(params));
                sessionRepository.save(session);
                log.info("Stored last used params for session {}: {}", sessionId, params);
            }
        } catch (Exception e) {
            log.error("Failed to store last used params for session {}", sessionId, e);
        }
    }

    /**
     * Clear pending follow-up state from the session.
     * Called after successfully completing the pending scenario.
     */
    private void clearPendingFollowUp(String sessionId) {
        try {
            Optional<ChatSession> sessionOpt = sessionRepository.findBySessionId(sessionId);
            if (sessionOpt.isPresent()) {
                ChatSession session = sessionOpt.get();
                session.clearPendingState();
                sessionRepository.save(session);
                log.debug("Cleared pending follow-up state for session: {}", sessionId);
            }
        } catch (Exception e) {
            log.error("Failed to clear pending follow-up state: {}", e.getMessage());
        }
    }

    /**
     * Process a follow-up response when we have pending context.
     * The user is answering our question about missing parameters.
     */
    private Flux<String> processPendingFollowUp(ChatRequest request, ChatSession session,
                                                 String sessionContext, List<String> userRoles, String executionId) {
        String pendingScenario = session.getPendingScenario();
        String pendingParamsStr = session.getPendingParams();
        List<String> missingParams = pendingParamsStr != null ?
                Arrays.asList(pendingParamsStr.split(",")) : List.of();

        log.info("Processing pending follow-up for scenario: {}, user provided: {}",
                pendingScenario, request.getQuery());

        // Build enhanced context that tells the LLM this is a follow-up to a previous question
        String enhancedContext = String.format(
                "IMPORTANT CONTEXT: The user was previously asked about %s scenario and we need %s. " +
                "The user's current message is their response to that question.\n\n%s",
                pendingScenario, String.join(", ", missingParams), sessionContext);

        return Flux.concat(
                Flux.just("[PROGRESS]🔍 Processing your response...\n"),

                // Use LLM to extract the parameter from user's response
                intelligenceClient.detectIntent(request.getQuery(), enhancedContext)
                        .timeout(Duration.ofMillis(maxLlmTimeoutMs))
                        .flatMapMany(detectedIntent -> {
                            // Build the params map combining previously collected params with new ones
                            Map<String, Object> combinedParams = new HashMap<>();

                            // Load previously collected params
                            String collectedJson = session.getCollectedParams();
                            if (collectedJson != null && !collectedJson.isEmpty()) {
                                try {
                                    @SuppressWarnings("unchecked")
                                    Map<String, Object> collected = objectMapper.readValue(collectedJson, Map.class);
                                    combinedParams.putAll(collected);
                                } catch (Exception e) {
                                    log.warn("Failed to parse collected params: {}", e.getMessage());
                                }
                            }

                            // Add newly detected params
                            if (detectedIntent.getParams() != null) {
                                combinedParams.putAll(detectedIntent.getParams());
                            }

                            // If the user's message IS the parameter value, extract it directly
                            if (missingParams.size() == 1 && !combinedParams.containsKey(missingParams.get(0))) {
                                String paramName = missingParams.get(0);
                                String userValue = request.getQuery().trim();
                                // If the query looks like a direct value (not a sentence), use it
                                if (userValue.split("\\s+").length <= 3) {
                                    combinedParams.put(paramName, userValue);
                                    log.info("Extracted direct value for {}: {}", paramName, userValue);
                                }
                            }

                            log.info("Combined params for pending scenario: {}", combinedParams);

                            // Create intent with the pending scenario and combined params
                            IntentResult intent = IntentResult.builder()
                                    .scenario(pendingScenario)
                                    .confidence(0.95)
                                    .params(combinedParams)
                                    .missingParams(List.of())
                                    .reasoning("Follow-up response to previous question")
                                    .build();

                            // Validate if we now have all required params
                            IntentValidationService.ValidationResult validation =
                                    validationService.validate(intent, request.getQuery());

                            // Still missing params?
                            if (!validation.missingRequiredParams().isEmpty()) {
                                log.info("Still missing params after follow-up: {}", validation.missingRequiredParams());
                                // Update stored pending params with remaining missing ones
                                storePendingFollowUp(session.getSessionId(), pendingScenario,
                                        validation.missingRequiredParams(), combinedParams);
                                return generateMissingParamMessage(pendingScenario,
                                        validation.missingRequiredParams(), request.getQuery());
                            }

                            // Clear pending state since we now have all params
                            clearPendingFollowUp(session.getSessionId());

                            // Authorization check
                            if (!rbacService.anyRoleAuthorized(userRoles, pendingScenario)) {
                                log.warn("User not authorized for pending scenario: {}", pendingScenario);
                                return Flux.just("\n❌ You don't have permission to access this information.");
                            }

                            // Execute the scenario
                            ScenarioRequest scenarioRequest = ScenarioRequest.builder()
                                    .scenario(pendingScenario)
                                    .params(combinedParams)
                                    .userId(request.getUserId())
                                    .build();

                            // Store combined params for future context resolution before execution
                            final Map<String, Object> finalCombinedParams = combinedParams;

                            return Flux.concat(
                                    Flux.just("[PROGRESS]✅ Got it!\n"),
                                    Flux.just("[PROGRESS]🔐 Verifying permissions...\n"),
                                    Flux.just("[PROGRESS]✅ Access granted\n"),
                                    Flux.just("[PROGRESS]📊 Fetching your data...\n"),
                                    scenarioRouter.routeReactive(scenarioRequest)
                                            .timeout(Duration.ofMillis(maxDbTimeoutMs))
                                            .flatMapMany(result -> {
                                                log.debug("Pending scenario executed successfully");
                                                // Store last used params for context resolution
                                                storeLastUsedParams(session.getSessionId(), finalCombinedParams);
                                                return Flux.concat(
                                                        Flux.just("[PROGRESS]✅ Data retrieved\n"),
                                                        Flux.just("[PROGRESS]📝 Preparing your response...\n"),
                                                        intelligenceClient.formatResponseStreaming(pendingScenario, result, request.getQuery())
                                                                .map(jsonResponse -> "[RESPONSE]" + jsonResponse)
                                                                .doOnComplete(() -> {
                                                                    log.debug("Pending follow-up completed successfully");
                                                                    Instant requestTime = Instant.now();
                                                                    logAuditAsync(executionId, request.getUserId(), pendingScenario,
                                                                            requestTime, Instant.now(), true, null, intent, result);
                                                                })
                                                );
                                            })
                                            .onErrorResume(e -> {
                                                log.error("Pending scenario execution failed: {}", e.getMessage());
                                                return Flux.just("\n❌ Failed to retrieve your data. Please try again.");
                                            })
                            );
                        })
                        .onErrorResume(e -> {
                            log.error("Error processing pending follow-up: {}", e.getMessage());
                            return Flux.just("\n❌ I had trouble understanding your response. Please try again.");
                        })
        );
    }

    /**
     * Process pending confirmation response.
     * User is confirming or providing additional parameter info.
     */
    private Flux<String> processPendingConfirmation(ChatRequest request, ChatSession session,
                                                     String sessionContext, List<String> userRoles, String executionId) {
        String pendingScenario = session.getPendingScenario();
        String collectedParamsStr = session.getCollectedParams();
        
        log.info("Processing pending confirmation for scenario: {}, user provided: {}",
                pendingScenario, request.getQuery());
        
        try {
            // Parse collected params
            Map<String, Object> params = new HashMap<>();
            if (collectedParamsStr != null && !collectedParamsStr.isEmpty()) {
                params = objectMapper.readValue(collectedParamsStr, 
                    new TypeReference<Map<String, Object>>() {});
            }
            
            // If user provided additional info (like "type", "debit"), try to extract it
            String userQuery = request.getQuery().toLowerCase().trim();
            if (!isAffirmativeResponse(userQuery) && userQuery.length() < 20) {
                // Short response - might be providing parameter value
                // For TRANSACTION_HISTORY, "type" or "debit" could be transactionType filter
                if (pendingScenario != null && pendingScenario.equals("TRANSACTION_HISTORY")) {
                    // Check session context for original query with "debit"
                    if (sessionContext != null && sessionContext.toLowerCase().contains("debit")) {
                        params.put("transactionType", "DEBIT");
                    } else if (userQuery.contains("debit")) {
                        params.put("transactionType", "DEBIT");
                    } else if (userQuery.contains("credit")) {
                        params.put("transactionType", "CREDIT");
                    } else if (userQuery.contains("transfer")) {
                        params.put("transactionType", "TRANSFER");
                    }
                }
            }
            
            // Clear confirmation state
            clearPendingFollowUp(session.getSessionId());
            
            // Authorization check
            if (!rbacService.anyRoleAuthorized(userRoles, pendingScenario)) {
                log.warn("User not authorized for pending scenario: {}", pendingScenario);
                return Flux.just("\n❌ You don't have permission to access this information.");
            }
            
            // Execute the scenario with confirmed params
            ScenarioRequest scenarioRequest = ScenarioRequest.builder()
                    .scenario(pendingScenario)
                    .params(params)
                    .userId(request.getUserId())
                    .build();
            
            // Store combined params for future context resolution before execution
            final Map<String, Object> finalParams = params;
            
            return Flux.concat(
                    Flux.just("[PROGRESS]✅ Confirmed\n"),
                    Flux.just("[PROGRESS]🔐 Verifying permissions...\n"),
                    Flux.just("[PROGRESS]✅ Access granted\n"),
                    Flux.just("[PROGRESS]📊 Fetching your data...\n"),
                    scenarioRouter.routeReactive(scenarioRequest)
                            .timeout(Duration.ofMillis(maxDbTimeoutMs))
                            .doOnSuccess(result -> {
                                log.debug("Scenario executed, starting streaming response");
                                // Log successful execution to audit
                                Instant requestTime = Instant.now();
                                logAuditAsync(executionId, request.getUserId(), pendingScenario,
                                        requestTime, Instant.now(), true, null, 
                                        IntentResult.builder().scenario(pendingScenario).confidence(1.0).build(), 
                                        result);
                                // Store last used params for future context resolution
                                storeLastUsedParams(session.getSessionId(), finalParams);
                            })
                            .doOnError(e -> {
                                log.error("Scenario execution failed: {}", e.getMessage());
                                // Log execution failure to audit
                                Instant requestTime = Instant.now();
                                logAuditAsync(executionId, request.getUserId(), pendingScenario,
                                        requestTime, Instant.now(), false,
                                        "Scenario execution failed: " + e.getMessage(), 
                                        IntentResult.builder().scenario(pendingScenario).confidence(1.0).build(), 
                                        null);
                            })
                            .flatMapMany(result -> {
                                log.debug("Formatting response as stream");
                                return Flux.concat(
                                        // Status/progress messages
                                        Flux.just("[PROGRESS]✅ Data retrieved\n"),
                                        Flux.just("[PROGRESS]📝 Preparing your response...\n"),
                                        // Final structured response
                                        intelligenceClient.formatResponseStreaming(pendingScenario, result, request.getQuery())
                                                .map(jsonResponse -> "[RESPONSE]" + jsonResponse)
                                                .doOnComplete(() -> {
                                                    log.debug("Response streaming completed");
                                                    saveMessageSync(session.getSessionId(), "assistant", 
                                                        result.getData() != null ? result.getData().toString() : "Response completed");
                                                })
                                                .doOnError(e -> log.error("Response streaming error: {}", e.getMessage()))
                                );
                            })
                            .onErrorResume(e -> {
                                log.error("Error processing confirmation: {}", e.getMessage());
                                return Flux.just("\n❌ I encountered an error processing your request. Please try again.");
                            })
            );
        } catch (Exception e) {
            log.error("Error processing pending confirmation: {}", e.getMessage(), e);
            clearPendingFollowUp(session.getSessionId());
            return Flux.just("\n❌ I had trouble processing your confirmation. Please try again.");
        }
    }
    
    private boolean isAffirmativeResponse(String response) {
        if (response == null) return false;
        return response.toLowerCase().trim().matches(AFFIRMATIVE_PATTERN);
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

    private String getOrCreateSessionSync(ChatRequest request) {
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

    /**
     * Get all scenarios allowed for the given roles.
     * Used for RBAC-filtered intent detection - only show scenarios the user can access.
     */
    private Set<String> getAllowedScenariosForRoles(List<String> roles) {
        Set<String> allowedScenarios = new HashSet<>();
        for (String role : roles) {
            allowedScenarios.addAll(rbacService.getAllowedScenarios(role));
        }
        // Always allow UNKNOWN for conversational responses
        allowedScenarios.add("UNKNOWN");
        return allowedScenarios;
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

    /**
     * Default conversational message for unknown intents (fallback).
     */
    private String getDefaultUnknownMessage() {
        return """
                I'd be happy to help! 👋
                
                I couldn't quite understand your request. Could you tell me what you're looking for?
                
                Here are some common actions:
                • 💰 Check account balance
                • 📜 View transaction history
                • 📊 Get account summary
                • 💳 View card details
                • 🏦 Check loan status
                • 📈 Analyze spending
                
                What would you like to do?
                """;
    }
}
