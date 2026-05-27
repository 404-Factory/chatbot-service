package com.factory.chatbot_service.controller;

import com.factory.chatbot_service.service.BedrockAgentService;
import com.factory.chatbot_service.service.MainInsightService;
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

    @PostMapping("/insight")
    public ResponseEntity<Map<String, String>> queryInsightAI(@RequestBody Map<String, Object> request) {
        Integer equipmentId = (Integer) request.get("equipmentId");
        String userQuestion = (String) request.get("content");

        String aiResponse = mainInsightService.getEquipmentAnalysis(equipmentId, userQuestion);

        return ResponseEntity.ok(Map.of("reply", aiResponse));
    }
}
