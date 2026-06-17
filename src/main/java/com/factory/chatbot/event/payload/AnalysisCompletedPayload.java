package com.factory.chatbot.event.payload;

import com.factory.common.event.domain.EventPayload;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
public class AnalysisCompletedPayload implements EventPayload {
    private Long anomalyId;
    private String status;
    private String summary;
    private Instant completedAt;
}
