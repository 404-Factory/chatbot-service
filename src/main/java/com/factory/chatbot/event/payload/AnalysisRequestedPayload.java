package com.factory.chatbot.event.payload;

import com.factory.common.event.domain.EventPayload;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class AnalysisRequestedPayload implements EventPayload {
    private Long anomalyId;
    private Long equipmentId;
    private String recipeParameter;
    private String ruleName;
    private String anomalyType;
    private String detectionReason;
    private Instant firstDetectedAt;
    private String summaryText;
    private String recommendedAnalysisType;
    private java.util.List<String> analysisFocus;
    private String llmPromptHint;
}
