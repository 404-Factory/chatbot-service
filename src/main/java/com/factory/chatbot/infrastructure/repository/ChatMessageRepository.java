package com.factory.chatbot.infrastructure.repository;

import com.factory.chatbot.infrastructure.entity.ChatMessage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findByRoomIdOrderByCreatedAtAsc(String roomId);
}