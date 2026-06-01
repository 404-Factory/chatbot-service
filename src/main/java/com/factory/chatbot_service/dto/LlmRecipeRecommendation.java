package com.factory.chatbot_service.dto;

import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class LlmRecipeRecommendation {

    private String status;

    private String summary;

    private List<LlmRecommendedParameter> recommendedParameters;

    private List<String> evidence;

    private List<String> warnings;

    private Double confidence;
}