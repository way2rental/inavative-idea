package com.enterprise.ai.api.controller;

import com.enterprise.ai.common.dto.ChatRequest;
import com.enterprise.ai.common.dto.ChatResponse;
import com.enterprise.ai.api.service.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.time.Duration;

/**
 * REST controller for chat endpoints.
 */
@Slf4j
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@Tag(name = "Chat", description = "Chat API for AI Orchestrator")
public class ChatController {

    private final ChatService chatService;

    @PostMapping
    @Operation(summary = "Send a chat message", description = "Process a user query and return AI response")
    public ResponseEntity<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
        log.info("Received chat request from user: {}", request.getUserId());
        ChatResponse response = chatService.processChat(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Send a chat message with streaming response", description = "Process a user query with SSE streaming updates")
    public Flux<String> chatStream(@Valid @RequestBody ChatRequest request) {
        log.info("Received streaming chat request from user: {}", request.getUserId());

        return Flux.create(sink -> {
            sink.next("Understanding your request...");

            // Process in background
            try {
                ChatResponse response = chatService.processChat(request);
                sink.next("Processing complete.");
                sink.next(response.getMessage());
                sink.complete();
            } catch (Exception e) {
                log.error("Error in streaming chat", e);
                sink.next("An error occurred processing your request.");
                sink.complete();
            }
        }).delayElements(Duration.ofMillis(500)).map(Object::toString);
    }
}
