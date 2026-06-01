package com.factory.chatbot_service.dto;

import com.factory.chatbot_service.dto.RecipeParameter;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SimilarRecipeCase {

    private Long id;

    private String equipmentId;

    private String processId;

    private String productId;

    private String defectType;

    private RecipeParameter recipe;

    private Double defectRate;

    private Integer defectCount;

    private Integer productQuantity;
}