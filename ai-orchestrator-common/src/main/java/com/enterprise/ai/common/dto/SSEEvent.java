package com.enterprise.ai.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Wrapper for SSE events to properly tag them with event types.
 * This ensures the controller can emit the correct SSE event type.
 */
@Data
@AllArgsConstructor
public class SSEEvent {
    private String eventType; // "progress", "response", "message", etc.
    private String data;

    public static SSEEvent progress(String message) {
        return new SSEEvent("progress", message);
    }

    public static SSEEvent response(String jsonData) {
        return new SSEEvent("response", jsonData);
    }

    public static SSEEvent message(String data) {
        return new SSEEvent("message", data);
    }
}

