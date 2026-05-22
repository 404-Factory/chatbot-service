package com.factory.chatbot_service.controller;

import com.factory.chatbot_service.dto.ChatRequestDTO;
import com.factory.chatbot_service.service.ChatbotService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatbotService chatbotService;

    public ChatController(ChatbotService chatbotService) {
        this.chatbotService = chatbotService;
    }

    @PostMapping
    public String chat(@RequestBody ChatRequestDTO request) {
        System.out.println("[DEBUG] chat() called with prompt: " + request.getPrompt());
        return chatbotService.askInsightAI(request.getPrompt());
    }

    @GetMapping
    public String testGet() {
        return "Chat Controller is running! But please use POST method with a JSON body for actual chat.";
    }

}
