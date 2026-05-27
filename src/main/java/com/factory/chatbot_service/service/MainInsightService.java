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

    public String getEquipmentAnalysis(Integer equipmentId, String userQuestion) {
        System.out.println("[DEBUG] MainInsightService(getEquipmentAnalysis)");
        System.out.println("[DEBUG] equipmentId: " + equipmentId);
        System.out.println("[DEBUG] userQuestion: " + userQuestion);

        if (equipmentId == null) {
            return bedrockAgentService.askInsightAI(userQuestion);
        }

        List<AnomalyLog> logs = anomalyLogRepository.findTop5ByEquipmentIdOrderByOccurredTimeDesc(equipmentId);

        String targetEquipmentCode = getEquipmentCodeName(equipmentId);

        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("다음은 가동 중인 설비에서 발생한 실제 RDBMS 로그 데이터입니다.\n\n");
        promptBuilder.append(String.format("[대상 설비 식별정보] RDBMS ID: %d | 물리 장비 코드: %s\n\n", equipmentId, targetEquipmentCode));

        for (AnomalyLog log : logs) {
            promptBuilder.append(String.format(
                "- 로그 ID: %d | 점검 항목: %s | 탐지된 룰: %s | 심각도: %s | 발생 시각: %s\n",
                log.getLogId(), log.getRecipeParameter(), log.getRuleName(), log.getSeverity(), log.getOccurredTime()
            ));
        }

        promptBuilder.append("\n[엔지니어의 추가 질문]\n").append(userQuestion);
        promptBuilder.append("\n위의 팩트 데이터와 네가 가진 Knowledge Base 문서를 대조하여 가이드라인에 맞춰 분석 보고서를 반환해줘.");

        return bedrockAgentService.askInsightAI(promptBuilder.toString());
    }


    private String getEquipmentCodeName(int id) {
        return switch (id) {
            case 1 -> "EQP-DEPOSITION-001"; case 2 -> "EQP-DEPOSITION-002";
            case 3 -> "EQP-PHOTO-001";      case 4 -> "EQP-PHOTO-002";
            case 5 -> "EQP-PHOTO-003";      case 6 -> "EQP-PHOTO-004";
            case 7 -> "EQP-ETCH-001";       case 8 -> "EQP-ETCH-002";
            case 9 -> "EQP-CLEANING-001";   case 10 -> "EQP-CLEANING-002";
            default -> "UNKNOWN-EQP";
        };
    }
}