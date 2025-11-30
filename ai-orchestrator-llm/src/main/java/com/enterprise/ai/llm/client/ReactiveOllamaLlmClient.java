package com.enterprise.ai.llm.client;

import com.enterprise.ai.common.dto.IntentResult;
import com.enterprise.ai.common.dto.ScenarioResult;
import com.enterprise.ai.common.exception.LlmException;
import com.enterprise.ai.llm.config.OllamaProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.*;

/**
 * Reactive Ollama LLM client implementation.
 * Fully non-blocking with SSE streaming support.
 * Uses custom Modelfiles for enterprise-intent and enterprise-formatter.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReactiveOllamaLlmClient implements ReactiveLlmClient {

    private final WebClient ollamaWebClient;
    private final OllamaProperties properties;
    private final ObjectMapper objectMapper;

    // Custom model names (created from Modelfiles)
    private static final String INTENT_MODEL = "enterprise-intent";
    private static final String FORMATTER_MODEL = "enterprise-formatter";

    // Category detection for two-stage intent
    private static final Map<String, List<String>> CATEGORY_SCENARIOS = Map.of(
            "PAYMENT", List.of("TXN_STATUS", "PAYMENT_HISTORY", "REFUND_STATUS"),
            "FILE", List.of("FILE_STATUS", "BATCH_STATUS", "UPLOAD_STATUS"),
            "ACCOUNT", List.of("ACCOUNT_SUMMARY", "STATEMENT", "BALANCE_CHECK")
    );

    @PostConstruct
    public void init() {
        if (properties.isEnabled()) {
            log.info("Reactive Ollama LLM Client initialized with base URL: {}", properties.getBaseUrl());
            log.info("Models: intent={}, formatter={}", 
                    properties.getIntentModel() != null ? properties.getIntentModel() : INTENT_MODEL,
                    properties.getFormatterModel() != null ? properties.getFormatterModel() : FORMATTER_MODEL);
        } else {
            log.warn("Reactive Ollama LLM Client is DISABLED. All calls will use fallback responses.");
        }
    }

    @Override
    @CircuitBreaker(name = "ollama", fallbackMethod = "detectIntentFallback")
    @Retry(name = "ollama")
    @RateLimiter(name = "ollama")
    public Mono<IntentResult> detectIntent(String userInput, String sessionContext) {
        if (!properties.isEnabled()) {
            return detectIntentFallback(userInput, sessionContext, new LlmException("Ollama disabled"));
        }

        String prompt = buildIntentPrompt(userInput, sessionContext);
        String model = properties.getIntentModel() != null ? properties.getIntentModel() : INTENT_MODEL;

        return callOllamaNonBlocking(model, prompt, false)
                .map(this::parseIntentResult)
                .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                .doOnSuccess(result -> log.debug("Intent detected: scenario={}, confidence={}", 
                        result.getScenario(), result.getConfidence()))
                .doOnError(e -> log.error("Error detecting intent: {}", e.getMessage()));
    }

    @Override
    @CircuitBreaker(name = "ollama", fallbackMethod = "detectIntentFallback")
    @Retry(name = "ollama")
    public Mono<IntentResult> detectIntentTwoStage(String userInput, String sessionContext) {
        if (!properties.isEnabled()) {
            return detectIntentFallback(userInput, sessionContext, new LlmException("Ollama disabled"));
        }

        // Stage 1: Detect category
        return detectCategory(userInput)
                .flatMap(category -> {
                    if ("UNKNOWN".equals(category)) {
                        // Fall back to full detection
                        return detectIntent(userInput, sessionContext);
                    }
                    
                    // Stage 2: Detect exact scenario within category
                    List<String> categoryScenarios = CATEGORY_SCENARIOS.getOrDefault(category, List.of());
                    return detectScenarioInCategory(userInput, sessionContext, category, categoryScenarios);
                })
                .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()));
    }

    private Mono<String> detectCategory(String userInput) {
        String prompt = """
                Classify this query into ONE category:
                - PAYMENT: transactions, transfers, payments, UPI, status
                - FILE: files, batches, uploads, processing, records
                - ACCOUNT: balance, summary, account info
                - UNKNOWN: doesn't fit any category
                
                Query: "%s"
                
                Return ONLY the category name (PAYMENT, FILE, ACCOUNT, or UNKNOWN).
                """.formatted(userInput);

        String model = properties.getIntentModel() != null ? properties.getIntentModel() : INTENT_MODEL;
        
        return callOllamaNonBlocking(model, prompt, false)
                .map(response -> {
                    String upper = response.trim().toUpperCase();
                    if (upper.contains("PAYMENT")) return "PAYMENT";
                    if (upper.contains("FILE")) return "FILE";
                    if (upper.contains("ACCOUNT")) return "ACCOUNT";
                    return "UNKNOWN";
                });
    }

    private Mono<IntentResult> detectScenarioInCategory(
            String userInput, String sessionContext, String category, List<String> scenarios) {
        
        String scenarioList = String.join(", ", scenarios);
        String prompt = """
                Category: %s
                Available scenarios: %s
                
                User query: "%s"
                Session context: %s
                
                Return JSON:
                {
                  "scenario": "one of: %s",
                  "confidence": 0.0 to 1.0,
                  "params": { extracted parameters },
                  "missingParams": [ required but missing params ],
                  "reasoning": "explanation"
                }
                """.formatted(category, scenarioList, userInput, sessionContext, scenarioList);

        String model = properties.getIntentModel() != null ? properties.getIntentModel() : INTENT_MODEL;
        
        return callOllamaNonBlocking(model, prompt, false)
                .map(response -> {
                    IntentResult result = parseIntentResult(response);
                    // Add category to result
                    Map<String, Object> params = result.getParams() != null ? 
                            new HashMap<>(result.getParams()) : new HashMap<>();
                    params.put("_category", category);
                    return IntentResult.builder()
                            .scenario(result.getScenario())
                            .confidence(result.getConfidence())
                            .params(params)
                            .missingParams(result.getMissingParams())
                            .possibleScenarios(result.getPossibleScenarios())
                            .reasoning(result.getReasoning())
                            .build();
                });
    }

    @Override
    @CircuitBreaker(name = "ollama", fallbackMethod = "generateFollowUpFallback")
    @Retry(name = "ollama")
    public Mono<String> generateFollowUpQuestion(String scenarioCode, List<String> missingParams) {
        if (!properties.isEnabled()) {
            return generateFollowUpFallback(scenarioCode, missingParams, new LlmException("Ollama disabled"));
        }

        String prompt = buildFollowUpPrompt(scenarioCode, missingParams);
        String model = properties.getFormatterModel() != null ? properties.getFormatterModel() : FORMATTER_MODEL;

        return callOllamaNonBlocking(model, prompt, false)
                .map(String::trim)
                .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()));
    }

    @Override
    @CircuitBreaker(name = "ollama", fallbackMethod = "formatResponseFallback")
    @Retry(name = "ollama")
    public Mono<String> formatResponse(String scenarioCode, ScenarioResult result, String userQuery) {
        if (!properties.isEnabled()) {
            return formatResponseFallback(scenarioCode, result, userQuery, new LlmException("Ollama disabled"));
        }

        try {
            String dataJson = objectMapper.writeValueAsString(result.getData());
            String prompt = buildFormattingPrompt(scenarioCode, dataJson, userQuery);
            String model = properties.getFormatterModel() != null ? properties.getFormatterModel() : FORMATTER_MODEL;

            return callOllamaNonBlocking(model, prompt, false)
                    .map(String::trim)
                    .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()));
        } catch (JsonProcessingException e) {
            return Mono.error(new LlmException("Failed to serialize result data", e));
        }
    }

    @Override
    public Flux<String> formatResponseStreaming(String scenarioCode, ScenarioResult result, String userQuery) {
        if (!properties.isEnabled()) {
            return Flux.just("Streaming is disabled when Ollama is not enabled.");
        }

        try {
            String dataJson = objectMapper.writeValueAsString(result.getData());
            String prompt = buildFormattingPrompt(scenarioCode, dataJson, userQuery);
            String model = properties.getFormatterModel() != null ? properties.getFormatterModel() : FORMATTER_MODEL;

            return callOllamaStreaming(model, prompt);
        } catch (JsonProcessingException e) {
            return Flux.error(new LlmException("Failed to serialize result data", e));
        }
    }

    @Override
    public Mono<Boolean> isHealthy() {
        return ollamaWebClient.get()
                .uri("/api/tags")
                .retrieve()
                .bodyToMono(String.class)
                .map(response -> true)
                .timeout(Duration.ofSeconds(5))
                .onErrorReturn(false);
    }

    // ===== NON-BLOCKING OLLAMA CALLS =====

    // LLM parameters - configurable
    private static final double DEFAULT_TEMPERATURE = 0.3;
    private static final double STREAMING_TEMPERATURE = 0.4;
    private static final int DEFAULT_NUM_PREDICT = 1024;

    private Map<String, Object> buildRequestBody(String model, String prompt, boolean stream, double temperature) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("prompt", prompt);
        requestBody.put("stream", stream);
        requestBody.put("options", Map.of(
                "temperature", temperature,
                "num_predict", DEFAULT_NUM_PREDICT
        ));
        return requestBody;
    }

    private Mono<String> callOllamaNonBlocking(String model, String prompt, boolean stream) {
        Map<String, Object> requestBody = buildRequestBody(model, prompt, stream, DEFAULT_TEMPERATURE);

        log.debug("Calling Ollama [non-blocking] model={}, prompt length={}", model, prompt.length());

        return ollamaWebClient.post()
                .uri("/api/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .flatMap(response -> {
                    try {
                        JsonNode node = objectMapper.readTree(response);
                        if (node.has("error")) {
                            return Mono.error(new LlmException("Ollama error: " + node.get("error").asText()));
                        }
                        String result = node.has("response") ? node.get("response").asText() : response;
                        return Mono.just(result);
                    } catch (Exception e) {
                        return Mono.error(new LlmException("Failed to parse Ollama response", e));
                    }
                })
                .doOnError(WebClientResponseException.class, e -> 
                        log.error("Ollama API error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString()));
    }

    private Flux<String> callOllamaStreaming(String model, String prompt) {
        Map<String, Object> requestBody = buildRequestBody(model, prompt, true, STREAMING_TEMPERATURE);

        log.debug("Calling Ollama [streaming] model={}, prompt length={}", model, prompt.length());

        return ollamaWebClient.post()
                .uri("/api/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToFlux(String.class)
                .doOnSubscribe(s -> log.debug("Streaming subscription started"))
                .doOnNext(line -> log.trace("Received streaming line: {}", line))
                .flatMap(line -> {
                    try {
                        JsonNode node = objectMapper.readTree(line);
                        if (node.has("error")) {
                            String error = node.get("error").asText();
                            log.error("Ollama streaming error: {}", error);
                            return Flux.error(new LlmException("Ollama streaming error: " + error));
                        }
                        if (node.has("response")) {
                            String token = node.get("response").asText();
                            if (!token.isEmpty()) {
                                return Flux.just(token);
                            }
                        }
                        // Check if streaming is done
                        if (node.has("done") && node.get("done").asBoolean()) {
                            log.debug("Streaming completed (done=true)");
                            return Flux.empty();
                        }
                        return Flux.empty();
                    } catch (Exception e) {
                        log.error("Failed to parse streaming response: {}", line, e);
                        return Flux.error(new LlmException("Failed to parse streaming response", e));
                    }
                })
                .doOnComplete(() -> log.debug("Streaming flux completed"))
                .doOnError(e -> log.error("Streaming flux error: {}", e.getMessage()))
                .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()), Flux.empty());
    }

    // ===== PROMPT BUILDERS =====

    private String buildIntentPrompt(String userInput, String sessionContext) {
        return """
                Session context: %s
                
                User message: "%s"
                
                Analyze and return JSON with scenario, confidence, params, missingParams.
                """.formatted(
                        sessionContext != null ? sessionContext : "No previous context",
                        userInput
                );
    }

    private String buildFollowUpPrompt(String scenarioCode, List<String> missingParams) {
        String contextualHelp = switch (scenarioCode) {
            case "TXN_STATUS" -> "For transaction status, ask for transaction ID/UTR/reference number.";
            case "FILE_STATUS" -> "For file status, ask for file name or date of upload.";
            case "ACCOUNT_SUMMARY" -> "For account summary, ask for account number or type.";
            default -> "Ask for the missing information politely.";
        };

        return """
                Scenario: %s
                Missing parameters: %s
                
                %s
                
                Generate a SHORT, FRIENDLY question (under 25 words) asking for the missing information.
                Return ONLY the question text.
                """.formatted(scenarioCode, String.join(", ", missingParams), contextualHelp);
    }

    private String buildFormattingPrompt(String scenarioCode, String dataJson, String userQuery) {
        return """
                Scenario: %s
                User query: "%s"
                Raw data: %s
                
                Format this data into a natural, helpful response following enterprise formatting rules.
                Keep it under 150 words. Use emojis appropriately.
                """.formatted(scenarioCode, userQuery, dataJson);
    }

    // ===== RESPONSE PARSING =====

    private IntentResult parseIntentResult(String response) {
        try {
            String jsonPart = extractJson(response);
            if (jsonPart == null || jsonPart.isEmpty()) {
                log.warn("No JSON found in Ollama response, returning UNKNOWN intent");
                return createUnknownIntent();
            }

            JsonNode node = objectMapper.readTree(jsonPart);

            List<String> missingParams = new ArrayList<>();
            if (node.has("missingParams") && node.get("missingParams").isArray()) {
                for (JsonNode param : node.get("missingParams")) {
                    missingParams.add(param.asText());
                }
            }

            Map<String, Object> params = new HashMap<>();
            if (node.has("params") && node.get("params").isObject()) {
                node.get("params").fields().forEachRemaining(
                        entry -> params.put(entry.getKey(), entry.getValue().isNull() ? null : entry.getValue().asText())
                );
            }

            List<String> possibleScenarios = new ArrayList<>();
            if (node.has("possibleScenarios") && node.get("possibleScenarios").isArray()) {
                for (JsonNode scenario : node.get("possibleScenarios")) {
                    possibleScenarios.add(scenario.asText());
                }
            }

            String scenario = node.has("scenario") ? node.get("scenario").asText() : "UNKNOWN";
            double confidence = node.has("confidence") ? node.get("confidence").asDouble() : 0.0;
            String reasoning = node.has("reasoning") ? node.get("reasoning").asText() : null;

            return IntentResult.builder()
                    .scenario(scenario)
                    .confidence(confidence)
                    .params(params)
                    .missingParams(missingParams)
                    .possibleScenarios(possibleScenarios.isEmpty() ? null : possibleScenarios)
                    .reasoning(reasoning)
                    .build();
        } catch (Exception e) {
            log.error("Error parsing intent result from response: {}", response, e);
            return createUnknownIntent();
        }
    }

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

    private IntentResult createUnknownIntent() {
        return IntentResult.builder()
                .scenario("UNKNOWN")
                .confidence(0.0)
                .params(new HashMap<>())
                .missingParams(new ArrayList<>())
                .build();
    }

    // ===== FALLBACK METHODS =====

    public Mono<IntentResult> detectIntentFallback(String userInput, String sessionContext, Throwable t) {
        log.warn("Fallback for detectIntent due to: {}", t.getMessage());
        return Mono.just(createUnknownIntent());
    }

    public Mono<String> generateFollowUpFallback(String scenarioCode, List<String> missingParams, Throwable t) {
        log.warn("Fallback for generateFollowUp due to: {}", t.getMessage());
        if (missingParams != null && !missingParams.isEmpty()) {
            return Mono.just("Please provide the following information: " + String.join(", ", missingParams));
        }
        return Mono.just("Could you please provide more details about your request?");
    }

    public Mono<String> formatResponseFallback(String scenarioCode, ScenarioResult result, String userQuery, Throwable t) {
        log.warn("Fallback for formatResponse due to: {}", t.getMessage());
        if (result != null && result.getData() != null) {
            StringBuilder sb = new StringBuilder("Here is the result for your request:\n");
            result.getData().forEach((key, value) -> 
                    sb.append("- ").append(key).append(": ").append(value).append("\n"));
            return Mono.just(sb.toString());
        }
        return Mono.just("Your request has been processed successfully.");
    }
}
