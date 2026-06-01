package com.factory.chatbot_service.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ChatResponse {

    private String status;

    private String answer;

    private String sessionId;

    private String rawResponse;
}