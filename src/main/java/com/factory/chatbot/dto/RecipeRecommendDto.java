package com.factory.chatbot.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

public class RecipeRecommendDto {

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Request {
        private String operatorQuestion;
        private String equipmentId;
        private String processId;
        private String productId;
        private String defectType;
        private RecipeParameter currentRecipe;
        private InsightContext insightContext;
        private SensorContext sensorContext;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Response {
        private String status;
        private String summary;
        private RecipeParameter recommendedRecipe;
        private List<RecipeParameterValue> recommendedParameters;
        private ExpectedEffect expectedEffect;
        private List<String> evidence;
        private List<String> warnings;
        private Double confidence;
    }
}
