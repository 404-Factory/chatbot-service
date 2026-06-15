package com.factory.chatbot.event.handler;

import com.factory.chatbot.controller.InternalAnomalyAnalysisController;
import com.factory.chatbot.dto.AnomalyAnalysisDto;
import com.factory.chatbot.event.payload.AnalysisCompletedPayload;
import com.factory.chatbot.event.payload.AnalysisRequestedPayload;
import com.factory.chatbot.event.type.AnalysisEventType;
import com.factory.chatbot.infrastructure.entity.AnomalyLog;
import com.factory.chatbot.infrastructure.entity.DefectInfo;
import com.factory.chatbot.infrastructure.entity.EquipmentInfo;
import com.factory.chatbot.infrastructure.repository.AnomalyLogRepository;
import com.factory.chatbot.infrastructure.repository.DefectInfoRepository;
import com.factory.chatbot.infrastructure.repository.EquipmentInfoRepository;
import com.factory.common.event.domain.Event;
import com.factory.common.event.support.DomainEventFactory;
import com.factory.common.inbox.jpa.aop.InboxProcessed;
import com.factory.common.kafka.publisher.EventPublisher;
import com.factory.common.kafka.support.EventHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AnalysisRequestedHandler implements EventHandler<AnalysisRequestedPayload> {

    private final AnomalyLogRepository anomalyLogRepository;
    private final EquipmentInfoRepository equipmentInfoRepository;
    private final DefectInfoRepository defectInfoRepository;
    private final InternalAnomalyAnalysisController internalAnomalyAnalysisController;
    private final EventPublisher eventPublisher;
    private final DomainEventFactory domainEventFactory;

    @Override
    public String getEventType() {
        return "AnalysisRequested";
    }

    @Override
    @Transactional
    @InboxProcessed
    public void process(Event<AnalysisRequestedPayload> event) {
        log.info("AnalysisRequestHandler process executed");
        AnalysisRequestedPayload payload = event.getPayload();
        Long anomalyId = payload.getAnomalyId();
        log.info("Processing AnalysisRequested event for anomalyId={}", anomalyId);

        // 1. Retrieve Anomaly Log
        AnomalyLog anomalyLog = anomalyLogRepository.findById(anomalyId).orElse(null);
        if (anomalyLog == null) {
            log.warn("AnomalyLog not found for id={}. Skipping analysis.", anomalyId);
            return;
        }

        // 2. Retrieve Equipment Info
        EquipmentInfo equipmentInfo = equipmentInfoRepository.findById(anomalyLog.getEquipmentId()).orElse(null);

        // 3. Retrieve Related Defects (within 30 minutes after anomaly occurred time)
        Instant start = anomalyLog.getOccurredTime();
        Instant end = start.plusSeconds(1800);
        List<DefectInfo> defects = defectInfoRepository
                .findByCauseEquipmentIdAndOccurredTimeBetweenOrderByOccurredTimeDesc(
                        anomalyLog.getEquipmentId(), start, end);

        // 4. Construct Request DTO for AI analysis
        List<AnomalyAnalysisDto.DefectDto> defectDtos = defects.stream()
                .map(d -> new AnomalyAnalysisDto.DefectDto(
                        d.getLotId(),
                        d.getDefectType(),
                        d.getDefectCode(),
                        d.getOccurredTime(),
                        d.getDetectedTime()
                ))
                .toList();

        AnomalyAnalysisDto.Request request = AnomalyAnalysisDto.Request.builder()
                .equipmentName(equipmentInfo != null ? equipmentInfo.getName() : "N/A")
                .recipeParameter(anomalyLog.getRecipeParameter())
                .ruleName(anomalyLog.getRuleName())
                .anomalyType(anomalyLog.getAnomalyType())
                .detectionReason(anomalyLog.getDetectionReason())
                .occurredTime(anomalyLog.getOccurredTime())
                .defects(defectDtos)
                .summaryText(payload.getSummaryText())
                .recommendedAnalysisType(payload.getRecommendedAnalysisType())
                .analysisFocus(payload.getAnalysisFocus())
                .llmPromptHint(payload.getLlmPromptHint())
                .build();

        // 5. Trigger Bedrock AI Analysis
        String status = "SUCCESS";
        String summary = "";
        try {
            AnomalyAnalysisDto.Response response = internalAnomalyAnalysisController.analyze(request);
            summary = response.getAnalysisResult();
            if (summary == null || summary.startsWith("AI 분석 리포트 생성 중 예외가 발생했습니다")) {
                status = "FAILURE";
            }
        } catch (Exception e) {
            log.error("Failed to invoke AI analysis for anomalyId={}", anomalyId, e);
            status = "FAILURE";
            summary = "AI 분석 리포트 생성 실패: " + e.getMessage();
        }

        // 6. Publish AnalysisCompleted event via Transactional Outbox
        AnalysisCompletedPayload completedPayload = AnalysisCompletedPayload.builder()
                .anomalyId(anomalyId)
                .status(status)
                .summary(summary)
                .completedAt(Instant.now())
                .build();

        log.info("Publishing AnalysisCompleted event for anomalyId={}, status={}", anomalyId, status);
        eventPublisher.publish(
                domainEventFactory.create(
                        AnalysisEventType.ANALYSIS_COMPLETED,
                        "Analysis",
                        String.valueOf(anomalyId),
                        completedPayload
                )
        );
    }
}
