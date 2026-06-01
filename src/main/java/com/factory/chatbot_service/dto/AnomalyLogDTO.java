package com.factory.chatbot_service.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AnomalyLogDTO {
    private Long logId;
    private int equipmentId;
    private String recipeParameter;
    private String ruleName;
    private String severity;
    private String occurredTime;
}