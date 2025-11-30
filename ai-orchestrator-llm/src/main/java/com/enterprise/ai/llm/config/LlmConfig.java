package com.enterprise.ai.llm.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Configuration for LLM module with proper Ollama client setup.
 */
@Slf4j
@Configuration
public class LlmConfig {

    @Bean
    public WebClient ollamaWebClient(OllamaProperties properties) {
        int connectTimeoutMs = properties.getConnectionTimeoutMs();
        int readTimeoutMs = properties.getReadTimeoutMs();
        int writeTimeoutMs = properties.getWriteTimeoutMs();

        log.info("Configuring Ollama WebClient: connectTimeout={}ms, readTimeout={}ms, writeTimeout={}ms",
                connectTimeoutMs, readTimeoutMs, writeTimeoutMs);

        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMs)
                .responseTimeout(Duration.ofMillis(readTimeoutMs))
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(readTimeoutMs, TimeUnit.MILLISECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(writeTimeoutMs, TimeUnit.MILLISECONDS)));

        return WebClient.builder()
                .baseUrl(properties.getBaseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .filter(logRequest())
                .filter(logResponse())
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(32 * 1024 * 1024)) // 32MB buffer for streaming
                .build();
    }

    private ExchangeFilterFunction logRequest() {
        return ExchangeFilterFunction.ofRequestProcessor(clientRequest -> {
            log.debug("Ollama Request: {} {}", clientRequest.method(), clientRequest.url());
            return Mono.just(clientRequest);
        });
    }

    private ExchangeFilterFunction logResponse() {
        return ExchangeFilterFunction.ofResponseProcessor(clientResponse -> {
            log.debug("Ollama Response: Status {}", clientResponse.statusCode());
            return Mono.just(clientResponse);
        });
    }
}
