package com.factory.chatbot_service.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

public class LlmRecommendationDto {

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Recommendation {
        private String status;
        private String summary;
        private List<Parameter> recommendedParameters;
        private List<String> evidence;
        private List<String> warnings;
        private Double confidence;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Parameter {
        private String name;
        private double recommendedMin;
        private double recommendedMax;
        private double recommendedValue;
        private String reason;
    }
}
