package com.factory.chatbot_service.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.factory.chatbot_service.dto.RecipeRecommendRequest;
import com.factory.chatbot_service.dto.RecipeRecommendResponse;
import com.factory.chatbot_service.service.RecipeRecommendationService;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai/recipe")
public class RecipeRecommendationController {

    private final RecipeRecommendationService recipeRecommendationService;
    private final ObjectMapper objectMapper;

    public RecipeRecommendationController(
            RecipeRecommendationService recipeRecommendationService,
            ObjectMapper objectMapper
    ) {
        this.recipeRecommendationService = recipeRecommendationService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/recommend")
    public RecipeRecommendResponse recommend(@RequestBody(required = false) String body) {
        RecipeRecommendRequest request = parseRequest(body);
        if (request == null) {
            return RecipeRecommendResponse.builder()
                    .status("BAD_REQUEST")
                    .summary("Request body must be valid JSON. Example: {\"equipmentId\":\"1\",\"defectType\":\"Scratch\"}")
                    .recommendedRecipe(null)
                    .recommendedParameters(List.of())
                    .expectedEffect(null)
                    .evidence(List.of())
                    .warnings(List.of("Invalid or empty JSON request body."))
                    .confidence(0.0)
                    .build();
        }

        return recipeRecommendationService.recommend(request);
    }

    @GetMapping("/version")
    public String version() {
        return "recipe-controller-raw-json-v2";
    }

    private RecipeRecommendRequest parseRequest(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }

        try {
            return objectMapper.readValue(body, RecipeRecommendRequest.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}