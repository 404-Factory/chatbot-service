package com.factory.chatbot.service;

import com.factory.chatbot.infrastructure.entity.ChatRoom;
import com.factory.chatbot.infrastructure.entity.ChatMessage;
import java.util.List;

public interface MainInsightService {
    String getEquipmentAnalysis(Integer equipmentId, String userQuestion, String roomId);
    List<ChatRoom> getAllRooms();
    List<ChatMessage> getRoomMessages(String roomId);
    void deleteRoom(String roomId);
    void saveMessageDirectly(String roomId, String role, String content, String title);
}
