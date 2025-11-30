package com.enterprise.ai.api.controller;

import com.enterprise.ai.llm.client.OllamaHealthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * REST controller for Ollama health and status endpoints.
 */
@RestController
@RequestMapping("/api/ollama")
@RequiredArgsConstructor
@Tag(name = "Ollama", description = "Ollama LLM health and status endpoints")
public class OllamaController {

    private final OllamaHealthService ollamaHealthService;

    @GetMapping("/health")
    @Operation(summary = "Check Ollama health", description = "Check if Ollama is reachable and healthy")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> healthDetails = ollamaHealthService.getHealthDetails();
        boolean healthy = (boolean) healthDetails.get("healthy");
        
        if (healthy) {
            return ResponseEntity.ok(healthDetails);
        } else {
            return ResponseEntity.status(503).body(healthDetails);
        }
    }

    @GetMapping("/models")
    @Operation(summary = "List available models", description = "Get list of models available in Ollama")
    public ResponseEntity<Map<String, Object>> models() {
        return ResponseEntity.ok(Map.of(
                "models", ollamaHealthService.getAvailableModels()
        ));
    }
}
