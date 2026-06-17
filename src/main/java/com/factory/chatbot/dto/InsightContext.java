package com.factory.chatbot.dto;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

// 이후 인사이트 분석 AI와 붙는 부분 
@Getter
@Setter
public class InsightContext {

    private String insightSummary;

    private List<String> suspectedFactors;

    private List<String> affectedEquipment;

    private Double confidence;
}