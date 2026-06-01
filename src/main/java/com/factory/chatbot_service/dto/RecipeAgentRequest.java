package com.factory.chatbot_service.dto;

import com.factory.chatbot_service.dto.RecipeRecommendationContext;
import com.factory.chatbot_service.dto.InsightContext;
import com.factory.chatbot_service.dto.RecipeParameter;
import com.factory.chatbot_service.dto.RecipeParameterValue;
import com.factory.chatbot_service.dto.RecipeRecommendRequest;
import com.factory.chatbot_service.dto.RecipeHistoryCase;
import com.factory.chatbot_service.dto.SensorSnapshot;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RecipeAgentRequest {

    private String operatorQuestion;

    private String equipmentId;

    private String processId;

    private String productId;

    private String defectType;

    private RecipeParameter currentRecipe;

    private List<RecipeParameterValue> currentRecipeParameters;

    private String currentRecipeSource;

    private Long lotId;

    private Long masterRecipeId;

    private List<String> contextWarnings;

    private InsightContext insightContext;

    private SensorSnapshot sensorSnapshot;

    private List<RecipeHistoryCase> histories;

    public static RecipeAgentRequest from(
            RecipeRecommendRequest request,
            RecipeRecommendationContext context,
            SensorSnapshot sensorSnapshot,
            List<RecipeHistoryCase> histories
    ) {
        return RecipeAgentRequest.builder()
                .operatorQuestion(request.getOperatorQuestion())
                .equipmentId(context.getEquipmentId())
                .processId(context.getProcessId())
                .productId(context.getProductId())
                .defectType(context.getDefectType())
                .currentRecipe(context.getCurrentRecipe())
                .currentRecipeParameters(context.getCurrentRecipeParameters())
                .currentRecipeSource(context.getCurrentRecipeSource())
                .lotId(context.getLotId())
                .masterRecipeId(context.getMasterRecipeId())
                .contextWarnings(context.getWarnings())
                .insightContext(request.getInsightContext())
                .sensorSnapshot(sensorSnapshot)
                .histories(histories)
                .build();
    }
}