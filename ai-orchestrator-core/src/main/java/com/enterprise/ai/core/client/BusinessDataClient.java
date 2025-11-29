package com.enterprise.ai.core.client;

import com.enterprise.ai.core.config.BusinessDataProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * WebClient-based client for calling business data service APIs.
 * Replaces mock data with real HTTP calls to backend services.
 */
@Slf4j
@Component
public class BusinessDataClient {

    private final WebClient webClient;
    private final BusinessDataProperties properties;
    private final ObjectMapper objectMapper;

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{([^}]+)}");

    public BusinessDataClient(BusinessDataProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;

        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, properties.getConnectionTimeoutMs())
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(properties.getTimeoutMs(), TimeUnit.MILLISECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(properties.getTimeoutMs(), TimeUnit.MILLISECONDS)));

        this.webClient = WebClient.builder()
                .baseUrl(properties.getBaseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * Call the business data service for a specific scenario.
     *
     * @param scenarioCode the scenario code (e.g., TXN_STATUS)
     * @param params       parameters extracted from user query
     * @return Mono of response map
     */
    @SuppressWarnings("unchecked")
    public Mono<Map<String, Object>> fetchBusinessData(String scenarioCode, Map<String, Object> params) {
        if (properties.isMockMode()) {
            log.debug("Mock mode enabled, returning mock data for scenario: {}", scenarioCode);
            return Mono.just(getMockData(scenarioCode, params));
        }

        BusinessDataProperties.EndpointConfig config = getEndpointConfig(scenarioCode);
        if (config == null) {
            log.warn("No endpoint config found for scenario: {}, using mock data", scenarioCode);
            return Mono.just(getMockData(scenarioCode, params));
        }

        String path = resolvePlaceholders(config.getPath(), params);
        int timeout = config.getTimeoutMs() != null ? config.getTimeoutMs() : properties.getTimeoutMs();

        log.info("Calling business data service: {} {} (timeout: {}ms)", config.getMethod(), path, timeout);

        WebClient.RequestBodySpec requestSpec = webClient
                .method(HttpMethod.valueOf(config.getMethod()))
                .uri(path);

        // Add custom headers if configured
        if (config.getHeaders() != null) {
            config.getHeaders().forEach(requestSpec::header);
        }

        // Add body for POST requests
        WebClient.RequestHeadersSpec<?> headersSpec;
        if ("POST".equalsIgnoreCase(config.getMethod()) && config.getBodyTemplate() != null) {
            String body = resolvePlaceholders(config.getBodyTemplate(), params);
            headersSpec = requestSpec.bodyValue(body);
        } else {
            headersSpec = requestSpec;
        }

        return headersSpec
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofMillis(timeout))
                .flatMap(response -> {
                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> result = objectMapper.readValue(response, Map.class);
                        return Mono.just(result);
                    } catch (Exception e) {
                        log.error("Failed to parse business data response: {}", response, e);
                        Map<String, Object> errorMap = new java.util.HashMap<>();
                        errorMap.put("error", "Failed to parse response");
                        errorMap.put("raw", response);
                        return Mono.just(errorMap);
                    }
                })
                .doOnSuccess(data -> log.debug("Business data fetched successfully for {}", scenarioCode))
                .doOnError(e -> log.error("Error fetching business data for {}: {}", scenarioCode, e.getMessage()))
                .onErrorResume(e -> Mono.just(getMockData(scenarioCode, params))); // Fallback to mock on error
    }

    private BusinessDataProperties.EndpointConfig getEndpointConfig(String scenarioCode) {
        if (properties.getEndpoints() == null) {
            return null;
        }
        return properties.getEndpoints().get(scenarioCode);
    }

    private String resolvePlaceholders(String template, Map<String, Object> params) {
        if (template == null || params == null) {
            return template;
        }

        StringBuffer result = new StringBuffer();
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);
        while (matcher.find()) {
            String paramName = matcher.group(1);
            Object value = params.get(paramName);
            String replacement = value != null ? value.toString() : "";
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    // ===== MOCK DATA FOR DEVELOPMENT =====

    private Map<String, Object> getMockData(String scenarioCode, Map<String, Object> params) {
        return switch (scenarioCode) {
            case "TXN_STATUS" -> getMockTxnStatus(params);
            case "FILE_STATUS" -> getMockFileStatus(params);
            case "ACCOUNT_SUMMARY" -> getMockAccountSummary(params);
            default -> {
                Map<String, Object> errorMap = new java.util.HashMap<>();
                errorMap.put("error", "Unknown scenario");
                errorMap.put("scenario", scenarioCode);
                yield errorMap;
            }
        };
    }

    private Map<String, Object> getMockTxnStatus(Map<String, Object> params) {
        String txnId = params.getOrDefault("txnId", "UNKNOWN").toString();
        Map<String, Object> data = new java.util.HashMap<>();
        data.put("txnId", txnId);
        data.put("status", "SUCCESS");
        data.put("amount", 5000.00);
        data.put("currency", "INR");
        data.put("channel", "UPI");
        data.put("senderAccount", "XXXX-XXXX-1234");
        data.put("receiverAccount", "XXXX-XXXX-5678");
        data.put("timestamp", java.time.Instant.now().toString());
        data.put("referenceNo", "REF" + System.currentTimeMillis());
        return data;
    }

    private Map<String, Object> getMockFileStatus(Map<String, Object> params) {
        String fileName = params.getOrDefault("fileName", "unknown.csv").toString();
        Map<String, Object> data = new java.util.HashMap<>();
        data.put("fileName", fileName);
        data.put("status", "PROCESSED");
        data.put("totalRecords", 1000);
        data.put("successRecords", 985);
        data.put("failedRecords", 15);
        data.put("failureRate", "1.5%");
        data.put("processedAt", java.time.Instant.now().toString());
        data.put("fileSize", "2.5 MB");
        return data;
    }

    private Map<String, Object> getMockAccountSummary(Map<String, Object> params) {
        String accountId = params.getOrDefault("accountId", "UNKNOWN").toString();
        // Mask account ID
        String maskedAccount = accountId.length() > 4 
                ? "XXXX-XXXX-" + accountId.substring(accountId.length() - 4)
                : "XXXX-XXXX-" + accountId;
        Map<String, Object> data = new java.util.HashMap<>();
        data.put("accountId", maskedAccount);
        data.put("accountType", "SAVINGS");
        data.put("availableBalance", 150000.00);
        data.put("currentBalance", 152000.00);
        data.put("currency", "INR");
        data.put("lastTransactionDate", java.time.LocalDate.now().minusDays(2).toString());
        data.put("status", "ACTIVE");
        return data;
    }
}
