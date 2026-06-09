package com.factory.chatbot.dto;

import com.factory.chatbot.dto.RecipeParameter;
import com.factory.chatbot.dto.RecipeParameterValue;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RecipeRecommendationContext {

    private String equipmentId;

    private String processId;

    private String productId;

    private String defectType;

    private RecipeParameter currentRecipe;

    private List<RecipeParameterValue> currentRecipeParameters;

    private String currentRecipeSource;

    private Long lotId;

    private Long masterRecipeId;

    private List<String> warnings;

    public boolean hasRequiredRecommendationContext() {
        return hasText(equipmentId)
                && hasText(processId)
                && hasText(productId)
                && hasText(defectType)
                && ((currentRecipeParameters != null && !currentRecipeParameters.isEmpty())
                        || currentRecipe != null);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}