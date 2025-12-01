package com.enterprise.ai.data.service;

import com.enterprise.ai.data.entity.ChatSession;
import com.enterprise.ai.data.entity.ChatMessage;
import com.enterprise.ai.data.repository.ChatSessionRepository;
import com.enterprise.ai.data.repository.ChatMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Service for chat session operations.
 * Centralizes all session data access with proper caching.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionService {

    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;

    /**
     * Get paginated chat sessions.
     */
    public Page<ChatSession> getSessions(int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "lastActivityAt"));
        return sessionRepository.findAll(pageRequest);
    }

    /**
     * Get paginated chat sessions with filters.
     */
    public Page<ChatSession> getSessionsWithFilters(String userId, String sessionId, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "lastActivityAt"));

        // If both filters are empty, return all
        if ((userId == null || userId.trim().isEmpty()) &&
            (sessionId == null || sessionId.trim().isEmpty())) {
            return sessionRepository.findAll(pageRequest);
        }

        // Convert empty strings to null for query
        String userIdFilter = (userId != null && !userId.trim().isEmpty()) ? userId.trim() : null;
        String sessionIdFilter = (sessionId != null && !sessionId.trim().isEmpty()) ? sessionId.trim() : null;

        return sessionRepository.findByFilters(userIdFilter, sessionIdFilter, pageRequest);
    }

    /**
     * Get session by ID.
     */
    public Optional<ChatSession> getSessionById(Long id) {
        return sessionRepository.findById(id);
    }

    /**
     * Get session by session ID string.
     */
    public Optional<ChatSession> getSessionBySessionId(String sessionId) {
        return sessionRepository.findBySessionId(sessionId);
    }

    /**
     * Get total session count.
     * Cached for performance.
     */
    @Cacheable(value = "sessionStats", key = "'totalCount'")
    public long getTotalSessionCount() {
        return sessionRepository.count();
    }

    /**
     * Get message count for a specific session.
     */
    public int getMessageCountForSession(String sessionId) {
        List<ChatMessage> messages = messageRepository.findBySessionIdOrderByTimestampAsc(sessionId);
        return messages.size();
    }

    /**
     * Get messages for a session.
     */
    public List<ChatMessage> getMessagesForSession(String sessionId) {
        return messageRepository.findBySessionIdOrderByTimestampAsc(sessionId);
    }

    /**
     * Save a chat session.
     */
    public ChatSession save(ChatSession session) {
        return sessionRepository.save(session);
    }

    /**
     * Save a chat message.
     */
    public ChatMessage saveMessage(ChatMessage message) {
        return messageRepository.save(message);
    }

    /**
     * Get or create a session for a user.
     */
    public ChatSession getOrCreateSession(String userId, String sessionId) {
        return sessionRepository.findBySessionId(sessionId)
                .orElseGet(() -> {
                    ChatSession newSession = ChatSession.builder()
                            .sessionId(sessionId)
                            .userId(userId)
                            .build();
                    log.info("Creating new session for user: {} with sessionId: {}", userId, sessionId);
                    return sessionRepository.save(newSession);
                });
    }

    /**
     * Get active sessions (sessions with recent activity).
     */
    public List<ChatSession> getActiveSessions(int limit) {
        PageRequest pageRequest = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "lastActivityAt"));
        return sessionRepository.findAll(pageRequest).getContent();
    }
}
