package com.factory.chatbot.infrastructure.repository;

import com.factory.chatbot.infrastructure.entity.ChatRoom;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ChatRoomRepository extends JpaRepository<ChatRoom, String> {
    List<ChatRoom> findAllByOrderByCreatedAtDesc();
}