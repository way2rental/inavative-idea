package com.enterprise.ai.api.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Global exception handler for reactive endpoints.
 * Prevents "response already committed" errors in SSE streams.
 */
@Slf4j
@RestControllerAdvice
public class ReactiveExceptionHandler {

    /**
     * Handle AccessDeniedException for SSE streams.
     * Returns error as part of the stream instead of trying to redirect.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public Mono<ResponseEntity<Object>> handleAccessDenied(
            AccessDeniedException ex,
            ServerWebExchange exchange) {

        String path = exchange.getRequest().getPath().value();
        log.warn("Access denied for path: {} - {}", path, ex.getMessage());

        // Check if this is an SSE stream request
        String accept = exchange.getRequest().getHeaders().getFirst("Accept");
        if (accept != null && accept.contains("text/event-stream")) {
            // For SSE streams, return the error as part of the stream
            log.debug("Returning access denied as SSE event");
            return Mono.just(ResponseEntity.status(HttpStatus.OK)
                    .body(Flux.just(
                        ServerSentEvent.<String>builder()
                            .data("❌ Access denied. You don't have permission to access this resource.")
                            .build()
                    )));
        }

        // For regular requests, return 403
        return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of(
                    "error", "Access Denied",
                    "message", ex.getMessage(),
                    "path", path
                )));
    }

    /**
     * Generic exception handler for unhandled exceptions.
     */
    @ExceptionHandler(Exception.class)
    public Mono<ResponseEntity<Object>> handleGenericException(
            Exception ex,
            ServerWebExchange exchange) {

        String path = exchange.getRequest().getPath().value();
        log.error("Unhandled exception for path: {}", path, ex);

        // Check if this is an SSE stream request
        String accept = exchange.getRequest().getHeaders().getFirst("Accept");
        if (accept != null && accept.contains("text/event-stream")) {
            // For SSE streams, return the error as part of the stream
            return Mono.just(ResponseEntity.status(HttpStatus.OK)
                    .body(Flux.just(
                        ServerSentEvent.<String>builder()
                            .data("❌ An error occurred: " + ex.getMessage())
                            .build()
                    )));
        }

        // For regular requests, return 500
        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                    "error", "Internal Server Error",
                    "message", ex.getMessage(),
                    "path", path
                )));
    }

    private static class Map {
        public static java.util.Map<String, String> of(String k1, String v1, String k2, String v2, String k3, String v3) {
            return java.util.Map.of(k1, v1, k2, v2, k3, v3);
        }
    }
}

