package com.factory.chatbot_service.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SensorContext {

    private String equipmentId;

    private String source;

    private String measuredAt;

    private Double temperature;

    private Double pressure;

    private Double speed;

    private Double vibration;

    private Double humidity;
}