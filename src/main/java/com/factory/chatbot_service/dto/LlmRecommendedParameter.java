package com.factory.chatbot_service.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class LlmRecommendedParameter {

    private String name;

    private Double recommendedMin;

    private Double recommendedMax;

    private Double recommendedValue;

    private String reason;
}