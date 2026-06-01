package com.factory.chatbot_service.service;

import com.factory.chatbot_service.dto.RecipeAgentResponse;
import com.factory.chatbot_service.dto.RecipeAgentRequest;

public interface RecipeAgentClient {

    RecipeAgentResponse recommend(RecipeAgentRequest request);
}