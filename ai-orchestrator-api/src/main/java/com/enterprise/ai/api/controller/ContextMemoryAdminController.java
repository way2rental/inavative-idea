package com.enterprise.ai.api.controller;

import com.enterprise.ai.data.entity.ContextMemory;
import com.enterprise.ai.data.repository.ContextMemoryRepository;
import com.enterprise.ai.intelligence.service.context.ContextMemoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for Context Memory admin operations.
 * Manages session-based entity memory.
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/context-memory")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Context Memory Admin", description = "Admin API for managing context memory")
public class ContextMemoryAdminController {

    private final ContextMemoryRepository memoryRepository;
    private final ContextMemoryService memoryService;

    @GetMapping
    @Operation(summary = "Get context memory")
    public ResponseEntity<List<ContextMemory>> getMemory(
            @RequestParam(required = false) String sessionId,
            @RequestParam(required = false) String entityType) {
        log.info("Admin requested context memory, sessionId: {}, entityType: {}", sessionId, entityType);
        
        List<ContextMemory> memories;
        
        if (sessionId != null && entityType != null) {
            memories = memoryRepository.findBySessionIdAndEntityType(sessionId, entityType);
        } else if (sessionId != null) {
            memories = memoryRepository.findBySessionId(sessionId);
        } else if (entityType != null) {
            // Get all and filter by entity type (simple approach)
            memories = memoryRepository.findAll().stream()
                .filter(m -> entityType.equals(m.getEntityType()))
                .toList();
        } else {
            memories = memoryRepository.findAll();
        }
        
        return ResponseEntity.ok(memories);
    }

    @DeleteMapping("/session/{sessionId}")
    @Operation(summary = "Clear context memory for session")
    public ResponseEntity<Map<String, String>> clearSession(@PathVariable String sessionId) {
        log.info("Admin clearing context memory for session: {}", sessionId);
        memoryService.clearContext(sessionId).block();
        return ResponseEntity.ok(Map.of("message", "Context memory cleared for session: " + sessionId));
    }

    @PostMapping("/cleanup")
    @Operation(summary = "Cleanup old context memory")
    public ResponseEntity<Map<String, String>> cleanupOldMemory(
            @RequestParam(defaultValue = "7") int daysOld) {
        log.info("Admin cleaning up context memory older than {} days", daysOld);
        memoryService.cleanupOldMemory(daysOld).block();
        return ResponseEntity.ok(Map.of("message", "Cleaned up context memory older than " + daysOld + " days"));
    }
}
