package com.factory.chatbot.service;

import com.factory.chatbot.dto.RecipeRecommendDto;

public interface RecipeRecommendationService {
    RecipeRecommendDto.Response recommend(RecipeRecommendDto.Request request);
    RecipeRecommendDto.Response recommendForActionGroup(RecipeRecommendDto.Request request);
    RecipeRecommendDto.Response recommendLocally(RecipeRecommendDto.Request request);
}
