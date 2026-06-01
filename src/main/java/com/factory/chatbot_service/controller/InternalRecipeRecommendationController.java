package com.factory.chatbot_service.controller;

import com.factory.chatbot_service.dto.RecipeRecommendRequest;
import com.factory.chatbot_service.dto.RecipeRecommendResponse;
import com.factory.chatbot_service.service.RecipeRecommendationService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal/recipe")
public class InternalRecipeRecommendationController {

    private final RecipeRecommendationService recipeRecommendationService;

    public InternalRecipeRecommendationController(RecipeRecommendationService recipeRecommendationService) {
        this.recipeRecommendationService = recipeRecommendationService;
    }

    @PostMapping("/recommend")
    public RecipeRecommendResponse recommend(@RequestBody RecipeRecommendRequest request) {
        return recipeRecommendationService.recommendForActionGroup(request);
    }
}