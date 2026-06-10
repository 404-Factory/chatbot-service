package com.factory.chatbot.dto;

import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

public class AnomalyAnalysisDto {

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Request {
        private String equipmentName;
        private String recipeParameter;
        private String ruleName;
        private String anomalyType;
        private String detectionReason;
        private Instant occurredTime;
        private List<DefectDto> defects;
        private String summaryText;
        private String recommendedAnalysisType;
        private List<String> analysisFocus;
        private String llmPromptHint;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DefectDto {
        private Long lotId;
        private String defectType;
        private String defectCode;
        private Instant occurredTime;
        private Instant detectedTime;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Response {
        private String analysisResult;
    }
}
