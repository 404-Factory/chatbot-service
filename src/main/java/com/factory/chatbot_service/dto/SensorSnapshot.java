package com.factory.chatbot_service.dto;

import java.util.Map;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SensorSnapshot {

    private String equipmentId;

    private String processId;

    private String source;

    private String measuredAt;

    private Double temperature;

    private Double pressure;

    private Double speed;

    private Double vibration;

    private Double humidity;

    private Map<String, Double> latestSensorValues;

    private Integer sampleCount;

    private Double averageTemperature;

    private Double minTemperature;

    private Double maxTemperature;

    private Double averagePressure;

    private Double minPressure;

    private Double maxPressure;

    private Double averageSpeed;

    private Double minSpeed;

    private Double maxSpeed;

    private Double averageVibration;

    private Double minVibration;

    private Double maxVibration;

    private Double averageHumidity;

    private Double minHumidity;

    private Double maxHumidity;

    private Map<String, Double> averageSensorValues;

    private Map<String, Double> minSensorValues;

    private Map<String, Double> maxSensorValues;
}