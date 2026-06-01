package com.factory.chatbot_service.dto;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RecipeRecommendResponse {

    private String status;

    private String summary;

    private RecipeParameter recommendedRecipe;

    private List<RecipeParameterValue> recommendedParameters;

    private ExpectedEffect expectedEffect;

    private List<String> evidence;

    private List<String> warnings;

    private Double confidence;
}