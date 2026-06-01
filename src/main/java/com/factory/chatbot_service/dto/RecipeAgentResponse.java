package com.factory.chatbot_service.dto;

import com.factory.chatbot_service.dto.ExpectedEffect;
import com.factory.chatbot_service.dto.RecipeParameter;
import com.factory.chatbot_service.dto.RecipeParameterValue;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class RecipeAgentResponse {

    private String status;

    private String summary;

    private RecipeParameter recommendedRecipe;

    private List<RecipeParameterValue> recommendedParameters;

    private ExpectedEffect expectedEffect;

    private List<String> evidence;

    private List<String> warnings;

    private Double confidence;
}