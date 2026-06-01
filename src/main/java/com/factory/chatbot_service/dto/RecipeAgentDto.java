package com.factory.chatbot_service.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

public class RecipeAgentDto {

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Request {
        private String operatorQuestion;
        private RecipeRecommendationContext recipeContext;
        private SensorSnapshot sensorSnapshot;
        private List<RecipeHistoryCase> historyCases;

        public static Request from(
                RecipeRecommendDto.Request request,
                RecipeRecommendationContext context,
                SensorSnapshot sensorSnapshot,
                List<RecipeHistoryCase> histories
        ) {
            return Request.builder()
                    .operatorQuestion(request.getOperatorQuestion())
                    .recipeContext(context)
                    .sensorSnapshot(sensorSnapshot)
                    .historyCases(histories)
                    .build();
        }
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
