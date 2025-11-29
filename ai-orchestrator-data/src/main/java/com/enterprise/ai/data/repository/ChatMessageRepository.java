package com.enterprise.ai.data.repository;

import com.enterprise.ai.data.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for chat messages.
 */
@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findBySessionIdOrderByTimestampAsc(String sessionId);

    List<ChatMessage> findTop10BySessionIdOrderByTimestampDesc(String sessionId);
}
