package com.enterprise.ai.intelligence.kernel.memory;

import com.enterprise.ai.intelligence.service.context.ContextMemoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Optional;

/**
 * Memory Manager Implementation.
 * 
 * Wraps ContextMemoryService to provide kernel-level memory interface.
 * 
 * REUSES existing ContextMemoryService.
 * NO HARDCODING - All entity types from database.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryManagerImpl implements MemoryManager {

    private final ContextMemoryService contextMemoryService;

    @Override
    public Mono<Void> storeEntity(String sessionId, String userId, String entityType, 
                                  String entityValue, Map<String, Object> metadata) {
        return contextMemoryService.storeEntity(sessionId, userId, entityType, entityValue, metadata)
                .doOnSuccess(v -> log.debug("Stored entity in memory: session={}, type={}, value={}", 
                        sessionId, entityType, entityValue))
                .doOnError(error -> log.error("Failed to store entity in memory: {}", error.getMessage(), error));
    }

    @Override
    public Mono<Optional<String>> resolveReference(String sessionId, String query, String entityType) {
        return contextMemoryService.resolveReference(sessionId, query, entityType)
                .doOnSuccess(opt -> {
                    if (opt.isPresent()) {
                        log.debug("Resolved reference: session={}, type={}, value={}", 
                                sessionId, entityType, opt.get());
                    } else {
                        log.debug("No reference resolved: session={}, type={}", sessionId, entityType);
                    }
                });
    }

    @Override
    public Mono<Optional<String>> getMostRecentEntity(String sessionId, String entityType) {
        return contextMemoryService.getMostRecentEntity(sessionId, entityType)
                .doOnSuccess(opt -> {
                    if (opt.isPresent()) {
                        log.debug("Retrieved most recent entity: session={}, type={}, value={}", 
                                sessionId, entityType, opt.get());
                    }
                });
    }

    @Override
    public Mono<Void> clearContext(String sessionId) {
        return contextMemoryService.clearContext(sessionId)
                .doOnSuccess(v -> log.info("Cleared context memory for session: {}", sessionId));
    }
}
