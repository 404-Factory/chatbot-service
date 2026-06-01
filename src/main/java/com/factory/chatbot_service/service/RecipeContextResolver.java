package com.factory.chatbot_service.service;

import com.factory.chatbot_service.dto.RecipeRecommendationContext;

import com.factory.chatbot_service.dto.RecipeRecommendRequest;

public interface RecipeContextResolver {

    RecipeRecommendationContext resolve(RecipeRecommendRequest request);
}