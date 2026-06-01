package com.factory.chatbot_service.controller;

import com.factory.chatbot_service.service.RecipeChatService;

import com.factory.chatbot_service.dto.ChatResponse;
import com.factory.chatbot_service.dto.ChatRequest;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
public class RecipeChatController {

    private final RecipeChatService recipeChatService;

    public RecipeChatController(RecipeChatService recipeChatService) {
        this.recipeChatService = recipeChatService;
    }

    @PostMapping("/recipe")
    public ChatResponse chat(@RequestBody ChatRequest request) {
        return recipeChatService.chat(request);
    }
}