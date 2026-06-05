package com.factory.chatbot_service.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Builder
public class ExpectedEffect {

    private String targetMetric;

    private String direction;

    private String description;

    public ExpectedEffect(String targetMetric, String direction, String description) {
        this.targetMetric = targetMetric;
        this.direction = direction;
        this.description = description;
    }
}