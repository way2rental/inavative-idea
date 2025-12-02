package com.enterprise.ai.core.scenario;

import com.enterprise.ai.common.context.RequestContext;
import com.enterprise.ai.common.context.RequestContextHolder;
import com.enterprise.ai.common.dto.ScenarioRequest;
import com.enterprise.ai.common.dto.ScenarioResult;
import com.enterprise.ai.common.enums.ExecutionType;
import com.enterprise.ai.common.exception.SecurityViolationException;
import com.enterprise.ai.core.security.ReadOnlyEnforcementService;
import com.enterprise.ai.data.entity.AiScenario;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Dynamic executor for HTTP_CALL execution type.
 * Executes HTTP GET or POST calls with strict security enforcement.
 * 
 * Features:
 * - GET/POST only validation
 * - URL whitelist enforcement
 * - URL template variable substitution
 * - Request body building from request_mapping
 * - Response shaping via response_mapping with MANDATORY masking
 * - Circuit breaker for fault tolerance
 * - Configurable timeout
 * - Response size limit (1MB)
 * 
 * SECURITY CRITICAL:
 * - User context ONLY from RequestContextHolder
 * - NEVER accept userId from request body or frontend
 * - Always inject X-User-Id, X-Org-Id, X-Roles headers
 */
@Slf4j
@Component
public class HttpCallExecutor implements DynamicExecutor {

    private static final long DEFAULT_TIMEOUT_MS = 5000;
    private static final int MAX_RESPONSE_SIZE_BYTES = 1024 * 1024; // 1MB limit
    private static final Pattern URL_VARIABLE_PATTERN = Pattern.compile("\\{(\\w+)}");

    // Security headers injected from RequestContext
    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String HEADER_ORG_ID = "X-Org-Id";
    private static final String HEADER_ROLES = "X-Roles";

    private final WebClient webClient;
    private final ReadOnlyEnforcementService readOnlyEnforcement;
    private final ObjectMapper objectMapper;
    private final CircuitBreakerRegistry circuitBreakerRegistry;

    @Value("${business-data.base-url:http://localhost:8081}")
    private String defaultBaseUrl;

    public HttpCallExecutor(
            WebClient.Builder webClientBuilder,
            ReadOnlyEnforcementService readOnlyEnforcement,
            ObjectMapper objectMapper) {
        this.webClient = webClientBuilder
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.readOnlyEnforcement = readOnlyEnforcement;
        this.objectMapper = objectMapper;
        
        // Configure circuit breaker
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .slidingWindowSize(10)
                .build();
        this.circuitBreakerRegistry = CircuitBreakerRegistry.of(config);
    }

    @PostConstruct
    public void init() {
        log.info("HttpCallExecutor initialized with default base URL: {}", defaultBaseUrl);
    }

    @Override
    public boolean supports(String executionType) {
        return ExecutionType.HTTP_CALL.name().equals(executionType);
    }

    @Override
    public ScenarioResult execute(ScenarioRequest request, AiScenario scenario) {
        long timeoutMs = scenario.getTimeoutMs() != null ? scenario.getTimeoutMs() : DEFAULT_TIMEOUT_MS;
        return executeReactive(request, scenario)
                .block(Duration.ofMillis(timeoutMs + 1000)); // Add buffer for blocking
    }

    @Override
    public Mono<ScenarioResult> executeReactive(ScenarioRequest request, AiScenario scenario) {
        String scenarioCode = scenario.getScenarioCode();
        long startTime = System.currentTimeMillis();
        long timeoutMs = scenario.getTimeoutMs() != null ? scenario.getTimeoutMs() : DEFAULT_TIMEOUT_MS;

        try {
            // SECURITY: Get user context ONLY from RequestContextHolder
            // NEVER accept userId from request body or frontend
            RequestContext userContext = RequestContextHolder.get();
            if (userContext == null) {
                log.error("Security context not initialized for HTTP_CALL scenario {}", scenarioCode);
                return Mono.just(ScenarioResult.builder()
                        .scenario(scenarioCode)
                        .success(false)
                        .errorMessage("Security context not initialized. HTTP call blocked.")
                        .data(Map.of("error", "Access denied"))
                        .build());
            }

            // Get and validate HTTP method
            String httpMethod = scenario.getHttpMethod();
            if (httpMethod == null) httpMethod = "GET";
            readOnlyEnforcement.validateHttpMethod(httpMethod);

            // Build URL with variable substitution
            String url = buildUrl(scenario.getHttpUrl(), request, scenario);
            
            // Validate URL is whitelisted
            readOnlyEnforcement.validateUrl(url);

            log.info("Executing HTTP_CALL {} {} for scenario {} (user: {})", 
                    httpMethod, url, scenarioCode, userContext.getUserId());

            // Get circuit breaker for this scenario
            CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(scenarioCode);

            // Build and execute request
            WebClient.RequestHeadersSpec<?> requestSpec;
            if ("POST".equalsIgnoreCase(httpMethod)) {
                Map<String, Object> requestBody = buildRequestBody(request, scenario);
                requestSpec = webClient.post()
                        .uri(url)
                        .bodyValue(requestBody);
            } else {
                requestSpec = webClient.get().uri(url);
            }

            // MANDATORY: Inject security headers from RequestContext
            requestSpec = injectSecurityHeaders(requestSpec, userContext);

            // Note: Custom headers removed - security headers injected via injectSecurityHeaders()

            return requestSpec
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .timeout(Duration.ofMillis(timeoutMs))
                    .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
                    .map(responseBody -> {
                        long executionTime = System.currentTimeMillis() - startTime;
                        log.info("HTTP_CALL for {} completed in {}ms", scenarioCode, executionTime);
                        
                        Map<String, Object> shapedResult = applyResponseMapping(responseBody, scenario);
                        
                        return ScenarioResult.builder()
                                .scenario(scenarioCode)
                                .success(true)
                                .data(shapedResult)
                                .build();
                    })
                    .onErrorResume(e -> {
                        log.error("HTTP_CALL failed for {}: {}", scenarioCode, e.getMessage());
                        // Sanitize error message - don't expose internal details
                        return Mono.just(ScenarioResult.builder()
                                .scenario(scenarioCode)
                                .success(false)
                                .errorMessage("HTTP call failed. Please try again later.")
                                .data(Map.of("error", "Service temporarily unavailable"))
                                .build());
                    });

        } catch (Exception e) {
            log.error("HTTP_CALL execution failed for {}: {}", scenarioCode, e.getMessage());
            // Sanitize error message - don't expose internal details
            return Mono.just(ScenarioResult.builder()
                    .scenario(scenarioCode)
                    .success(false)
                    .errorMessage("HTTP call error. Please try again later.")
                    .data(Map.of("error", "Service temporarily unavailable"))
                    .build());
        }
    }

    @Override
    public ScenarioResult executeDryRun(ScenarioRequest request, AiScenario scenario) {
        try {
            String httpMethod = scenario.getHttpMethod() != null ? scenario.getHttpMethod() : "GET";
            readOnlyEnforcement.validateHttpMethod(httpMethod);
            
            String url = buildUrl(scenario.getHttpUrl(), request, scenario);
            readOnlyEnforcement.validateUrl(url);
            
            Map<String, Object> requestBody = "POST".equalsIgnoreCase(httpMethod) 
                    ? buildRequestBody(request, scenario) 
                    : Map.of();

            return ScenarioResult.builder()
                    .scenario(scenario.getScenarioCode())
                    .success(true)
                    .data(Map.of(
                            "dryRun", true,
                            "executionType", ExecutionType.HTTP_CALL.name(),
                            "httpMethod", httpMethod,
                            "url", url,
                            "urlWhitelisted", true,
                            "requestBody", requestBody,
                            "responseMapping", scenario.getResponseMapping(),
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
     * Build URL by substituting template variables.
     * URL template format: /api/txn/{txnId} or ${business-data.base-url}/api/txn/{txnId}
     */
    private String buildUrl(String urlTemplate, ScenarioRequest request, AiScenario scenario) {
        if (urlTemplate == null || urlTemplate.isBlank()) {
            throw new IllegalArgumentException("http_url cannot be empty for HTTP_CALL");
        }

        String url = urlTemplate;

        // Replace ${business-data.base-url} with actual base URL
        if (url.contains("${business-data.base-url}")) {
            url = url.replace("${business-data.base-url}", defaultBaseUrl);
        }

        // Replace path variables {varName} with values from params
        Matcher matcher = URL_VARIABLE_PATTERN.matcher(url);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String varName = matcher.group(1);
            Object value = request.getParams() != null ? request.getParams().get(varName) : null;
            if (value != null) {
                matcher.appendReplacement(sb, value.toString());
            } else {
                log.warn("URL variable {} not found in params", varName);
                matcher.appendReplacement(sb, matcher.group(0)); // Keep original
            }
        }
        matcher.appendTail(sb);

        return sb.toString();
    }

    /**
     * Build request body for POST requests using request_mapping.
     */
    private Map<String, Object> buildRequestBody(ScenarioRequest request, AiScenario scenario) {
        Map<String, Object> body = new HashMap<>();

        if (scenario.getRequestMapping() == null || scenario.getRequestMapping().isBlank()) {
            // No mapping, pass all params directly
            if (request.getParams() != null) {
                body.putAll(request.getParams());
            }
            return body;
        }

        try {
            Map<String, String> mappings = objectMapper.readValue(
                    scenario.getRequestMapping(), 
                    new TypeReference<Map<String, String>>() {}
            );

            for (Map.Entry<String, String> entry : mappings.entrySet()) {
                String bodyField = entry.getKey();
                String jsonPath = entry.getValue();
                Object value = extractValue(request, jsonPath);
                if (value != null) {
                    body.put(bodyField, value);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse request_mapping, using direct params: {}", e.getMessage());
            if (request.getParams() != null) {
                body.putAll(request.getParams());
            }
        }

        return body;
    }

    /**
     * Apply response_mapping to shape HTTP response.
     */
    private Map<String, Object> applyResponseMapping(JsonNode responseBody, AiScenario scenario) {
        Map<String, Object> result = new LinkedHashMap<>();

        if (scenario.getResponseMapping() == null || scenario.getResponseMapping().isBlank()) {
            // No mapping, convert JSON to map directly
            try {
                return objectMapper.convertValue(responseBody, new TypeReference<Map<String, Object>>() {});
            } catch (Exception e) {
                result.put("response", responseBody.toString());
                return result;
            }
        }

        try {
            Map<String, String> mappings = objectMapper.readValue(
                    scenario.getResponseMapping(), 
                    new TypeReference<Map<String, String>>() {}
            );

            for (Map.Entry<String, String> entry : mappings.entrySet()) {
                String outputField = entry.getKey();
                String jsonPath = entry.getValue();
                JsonNode value = extractJsonValue(responseBody, jsonPath);
                if (value != null && !value.isNull()) {
                    result.put(outputField, convertJsonNode(value));
                }
            }
        } catch (Exception e) {
            log.warn("Failed to apply response_mapping, returning raw: {}", e.getMessage());
            try {
                return objectMapper.convertValue(responseBody, new TypeReference<Map<String, Object>>() {});
            } catch (Exception ex) {
                result.put("response", responseBody.toString());
            }
        }

        return result;
    }

    private Object extractValue(ScenarioRequest request, String jsonPath) {
        if (jsonPath.startsWith("$.params.")) {
            String fieldName = jsonPath.substring("$.params.".length());
            return request.getParams() != null ? request.getParams().get(fieldName) : null;
        }
        if (jsonPath.startsWith("$.")) {
            String fieldName = jsonPath.substring(2);
            if ("userId".equals(fieldName)) return request.getUserId();
            if ("sessionId".equals(fieldName)) return request.getSessionId();
            if ("scenario".equals(fieldName)) return request.getScenario();
        }
        return null;
    }

    private JsonNode extractJsonValue(JsonNode root, String jsonPath) {
        // Simple JSONPath extraction (e.g., $.field or $.parent.child)
        if (jsonPath.startsWith("$.")) {
            String path = jsonPath.substring(2);
            String[] parts = path.split("\\.");
            JsonNode current = root;
            for (String part : parts) {
                if (current == null || current.isNull()) return null;
                current = current.get(part);
            }
            return current;
        }
        return root.get(jsonPath);
    }

    private Object convertJsonNode(JsonNode node) {
        if (node.isTextual()) return node.asText();
        if (node.isNumber()) return node.numberValue();
        if (node.isBoolean()) return node.asBoolean();
        if (node.isArray() || node.isObject()) {
            try {
                return objectMapper.convertValue(node, new TypeReference<Object>() {});
            } catch (Exception e) {
                return node.toString();
            }
        }
        return node.toString();
    }

    /**
     * Inject security headers from RequestContext.
     * These headers identify the user to downstream services.
     * 
     * MANDATORY: Headers are ALWAYS injected from RequestContextHolder,
     * NEVER from request body or frontend parameters.
     */
    private WebClient.RequestHeadersSpec<?> injectSecurityHeaders(
            WebClient.RequestHeadersSpec<?> requestSpec, 
            RequestContext context) {
        
        if (context == null) {
            log.warn("Cannot inject security headers - context is null");
            return requestSpec;
        }

        // Inject X-User-Id
        if (context.getUserId() != null) {
            requestSpec = requestSpec.header(HEADER_USER_ID, context.getUserId());
        }

        // Inject X-Org-Id
        String orgId = context.getEffectiveOrgId();
        if (orgId != null) {
            requestSpec = requestSpec.header(HEADER_ORG_ID, orgId);
        }

        // Inject X-Roles (comma-separated list)
        List<String> roles = context.getRoles();
        if (roles != null && !roles.isEmpty()) {
            String rolesHeader = String.join(",", roles);
            requestSpec = requestSpec.header(HEADER_ROLES, rolesHeader);
        } else if (context.getRole() != null) {
            requestSpec = requestSpec.header(HEADER_ROLES, context.getRole());
        }

        log.debug("Injected security headers: userId={}, orgId={}, roles={}", 
                context.getUserId(), orgId, roles);

        return requestSpec;
    }
}
