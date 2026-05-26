package com.factory.chatbot_service.service;

import com.factory.chatbot_service.dto.AnomalyLogDTO;
import com.factory.chatbot_service.entity.AnomalyLog;
import com.factory.chatbot_service.repository.AnomalyLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MainInsightService {

    private final BedrockAgentService bedrockAgentService;
    private final AnomalyLogRepository anomalyLogRepository;

    public String getEquipmentAnalysis(int equipmentId, String userQuestion) {
        System.out.println("[DEBUG] MainInsightService(getEquipmentAnalysis)");
        System.out.println("[DEBUG] equipmentId: " + equipmentId);
        System.out.println("[DEBUG] userQuestion: " + userQuestion);


        List<AnomalyLog> logs = anomalyLogRepository.findTop5ByEquipmentIdOrderByOccurredTimeDesc(equipmentId);

        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("다음은 가동 중인 설비에서 발생한 실제 RDBMS 로그 데이터입니다.\n\n");

        if (logs.isEmpty()) {
            promptBuilder.append(String.format("- 현재 설비 %d번은 최근 발생한 이상 로그가 존재하지 않는 정상 상태입니다.\n", equipmentId));
        } else {
            for (AnomalyLog log : logs) {
                promptBuilder.append(String.format(
                    "- 로그 ID: %d | 설비 ID: %d | 점검 항목: %s | 탐지된 룰: %s | 심각도: %s | 발생 시각: %s\n",
                    log.getLogId(), log.getEquipmentId(), log.getRecipeParameter(), log.getRuleName(), log.getSeverity(), log.getOccurredTime()
                ));
            }
        }

        promptBuilder.append("\n[엔지니어의 추가 질문]\n");
        promptBuilder.append(userQuestion);

        promptBuilder.append("\n위의 팩트 데이터와 네가 가진 Knowledge Base 문서를 대조하여 가이드라인에 맞춰 분석 보고서를 반환해줘.");

        return bedrockAgentService.askInsightAI(promptBuilder.toString());
    }
}