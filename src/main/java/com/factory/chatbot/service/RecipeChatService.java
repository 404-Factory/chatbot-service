package com.factory.chatbot.service;

import com.factory.chatbot.dto.ChatDto;

public interface RecipeChatService {
    ChatDto.Response chat(ChatDto.Request request);
}
