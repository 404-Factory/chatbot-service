package com.factory.chatbot_service.dto;

import com.factory.chatbot_service.dto.RecipeParameter;
import com.factory.chatbot_service.dto.RecipeParameterValue;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RecipeHistoryCase {

    private Long id;

    private String equipmentId;

    private String processId;

    private String productId;

    private String defectType;

    private RecipeParameter recipe;

    private List<RecipeParameterValue> parameters;

    private Double defectRate;

    private Integer defectCount;

    private Integer productQuantity;
}