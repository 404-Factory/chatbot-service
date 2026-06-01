package com.factory.chatbot_service.controller;

import com.factory.chatbot_service.entity.ChatMessage;
import com.factory.chatbot_service.entity.ChatRoom;
import com.factory.chatbot_service.service.BedrockAgentService;
import com.factory.chatbot_service.service.MainInsightService;
import com.factory.chatbot_service.service.RecipeChatService;
import com.factory.chatbot_service.dto.ChatRequest;
import com.factory.chatbot_service.dto.ChatResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ChatbotController {
    private final MainInsightService mainInsightService;
    private final RecipeChatService recipeChatService;

    @PostMapping("/insight")
    public ResponseEntity<Map<String, String>> queryInsightAI(@RequestBody Map<String, Object> request) {
        Integer equipmentId = (Integer) request.get("equipmentId");
        String userQuestion = (String) request.get("content");
        String roomId = (String) request.get("roomId");

        // 서비스로 roomId도 함께 전달
        String aiResponse = mainInsightService.getEquipmentAnalysis(equipmentId, userQuestion, roomId);

        return ResponseEntity.ok(Map.of("reply", aiResponse));
    }

    @PostMapping("/message/save")
    public ResponseEntity<Void> saveMessageDirectly(@RequestBody Map<String, String> request) {
        String roomId = request.get("roomId");
        String role = request.get("role");
        String content = request.get("content");
        String title = request.get("title");

        mainInsightService.saveMessageDirectly(roomId, role, content, title);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/rooms")
    public ResponseEntity<List<ChatRoom>> getAllRooms() {
        return ResponseEntity.ok(mainInsightService.getAllRooms());
    }

    @GetMapping("/rooms/{roomId}/messages")
    public ResponseEntity<List<ChatMessage>> getRoomMessages(@PathVariable String roomId) {
        return ResponseEntity.ok(mainInsightService.getRoomMessages(roomId));
    }

    @DeleteMapping("/rooms/{roomId}")
    public ResponseEntity<Void> deleteRoom(@PathVariable String roomId) {
        mainInsightService.deleteRoom(roomId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/recipe")
    public ResponseEntity<ChatResponse> queryRecipeAI(@RequestBody ChatRequest request) {
        ChatResponse response = recipeChatService.chat(request);
        return ResponseEntity.ok(response);
    }
}