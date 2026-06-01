package com.factory.chatbot_service.dto;

import jakarta.validation.Valid;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RecipeRecommendRequest {

    private String operatorQuestion;

    private String equipmentId;

    private String processId;

    private String productId;

    private String defectType;

    @Valid
    private RecipeParameter currentRecipe;

    private InsightContext insightContext;

    private SensorContext sensorContext;
}