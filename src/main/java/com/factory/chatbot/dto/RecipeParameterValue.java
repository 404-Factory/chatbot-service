package com.factory.chatbot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecipeParameterValue {

    private String name;

    private Double min;

    private Double max;

    private Double currentValue;

    private Double recommendedMin;

    private Double recommendedMax;

    private Double recommendedValue;

    private String unit;
}