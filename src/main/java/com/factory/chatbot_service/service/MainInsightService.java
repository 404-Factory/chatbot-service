package com.factory.chatbot_service.service;

import com.factory.chatbot_service.entity.AnomalyLog;
import com.factory.chatbot_service.entity.ChatRoom;
import com.factory.chatbot_service.entity.ChatMessage;
import com.factory.chatbot_service.repository.AnomalyLogRepository;
import com.factory.chatbot_service.repository.ChatRoomRepository; // 주입
import com.factory.chatbot_service.repository.ChatMessageRepository; // 주입
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import com.factory.chatbot_service.entity.DefectInfo;
import com.factory.chatbot_service.entity.EquipmentInfo;
import com.factory.chatbot_service.repository.DefectInfoRepository;
import com.factory.chatbot_service.repository.EquipmentInfoRepository;

@Service
@RequiredArgsConstructor
public class MainInsightService {

    private final BedrockAgentService bedrockAgentService;
    private final AnomalyLogRepository anomalyLogRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final DefectInfoRepository defectInfoRepository;
    private final EquipmentInfoRepository equipmentInfoRepository;

    public String getEquipmentAnalysis(Integer equipmentId, String userQuestion, String roomId) {
        System.out.println("[DEBUG] MainInsightService - getEquipmentAnalysis called");
        System.out.println("[DEBUG] equipmentId: " + equipmentId);
        System.out.println("[DEBUG] userQuestion: " + userQuestion);
        System.out.println("[DEBUG] roomId: " + roomId);

        // 🔍 [JPA 영속화 1] 해당 방이 DB에 아직 없다면 첫 질문이므로 방부터 개설 (Lazy DB Creation)
        if (roomId != null && !chatRoomRepository.existsById(roomId)) {
            ChatRoom room = new ChatRoom();
            room.setRoomId(roomId);
            // 첫 질문이 너무 길면 타이틀이 깨지므로 12자 자르기 규칙 반영
            String title = userQuestion.length() > 12 ? userQuestion.substring(0, 12) + "..." : userQuestion;
            room.setTitle(title);
            room.setCreatedAt(LocalDateTime.now());
            chatRoomRepository.save(room);
        }

        // 🔍 [JPA 영속화 2] 유저가 던진 진짜 질문을 대화 기록 테이블에 세이브
        if (roomId != null) {
            ChatMessage userMsg = new ChatMessage();
            userMsg.setRoomId(roomId);
            userMsg.setRole("USER");
            userMsg.setContent(userQuestion);
            userMsg.setCreatedAt(LocalDateTime.now());
            chatMessageRepository.save(userMsg);
        }

        // 🎯 [해결 포인트] 시스템에 존재하지 않는 설비 ID 요청 시 에이전트 호출을 차단하고 예외 메시지를 반환합니다.
        if (equipmentId != null) {
            Optional<EquipmentInfo> eqInfoOpt = equipmentInfoRepository.findById(equipmentId.longValue());
            if (eqInfoOpt.isEmpty()) {
                System.out.println("[WARN] Non-existent Equipment ID requested: " + equipmentId);
                String fallbackResponse = "해당 설비는 시스템에 존재하지 않는 설비입니다.";
                if (roomId != null) {
                    ChatMessage aiMsg = new ChatMessage();
                    aiMsg.setRoomId(roomId);
                    aiMsg.setRole("ASSISTANT");
                    aiMsg.setContent(fallbackResponse);
                    aiMsg.setCreatedAt(LocalDateTime.now());
                    chatMessageRepository.save(aiMsg);
                }
                return fallbackResponse;
            }
        }

        // 3. 기존 AI 프롬프트 조립 및 Bedrock 호출 로직
        String aiResponse;
        if (equipmentId == null) {
            aiResponse = bedrockAgentService.askInsightAI(userQuestion, roomId); // 🔍 roomId 추가
        } else {
            List<AnomalyLog> logs = anomalyLogRepository.findTop5ByEquipmentIdOrderByOccurredTimeDesc(equipmentId);
            
            StringBuilder promptBuilder = new StringBuilder();
            promptBuilder.append("다음은 가동 중인 설비에서 발생한 실제 RDBMS 데이터입니다.\n\n");
            promptBuilder.append("[이상 로그 데이터]\n");
            for (AnomalyLog log : logs) {
                promptBuilder.append(String.format("- 로그 ID: %d | 점검 항목: %s | 탐지된 룰: %s | 심각도: %s\n",
                    log.getLogId(), log.getRecipeParameter(), log.getRuleName(), log.getSeverity()));
            }

            Optional<EquipmentInfo> eqInfoOpt = equipmentInfoRepository.findById(equipmentId.longValue());
            if (eqInfoOpt.isPresent()) {
                EquipmentInfo eqInfo = eqInfoOpt.get();
                List<DefectInfo> defects = defectInfoRepository.findByCauseEquipmentIdOrderByOccurredTimeDesc(eqInfo.getEquipmentId());
                if (!defects.isEmpty()) {
                    promptBuilder.append("\n[불량 현황 데이터]\n");
                    int count = 0;
                    for (DefectInfo defect : defects) {
                        if (count++ >= 5) break; // 최근 5개만
                        promptBuilder.append(String.format("- 불량 타입: %s | 원인 공정: %s | 발생 시각: %s\n",
                            defect.getDefectType(), defect.getCauseProcessName(), defect.getOccurredTime()));
                    }
                }
            }

            promptBuilder.append("\n[엔지니어의 추가 질문]\n").append(userQuestion);
            aiResponse = bedrockAgentService.askInsightAI(promptBuilder.toString(), roomId);
        }

        // 🔍 [JPA 영속화 3] Bedrock이 최종 대답한 완성형 리포트를 방 번호에 매핑하여 세이브
        if (roomId != null) {
            ChatMessage aiMsg = new ChatMessage();
            aiMsg.setRoomId(roomId);
            aiMsg.setRole("ASSISTANT");
            aiMsg.setContent(aiResponse);
            aiMsg.setCreatedAt(LocalDateTime.now());
            chatMessageRepository.save(aiMsg);
        }

        System.out.println("[DEBUG] ai Response: " + aiResponse);
        return aiResponse;
    }

    // MainInsightService 클래스 내부에 아래 두 메서드를 추가해 주세요.

    public List<ChatRoom> getAllRooms() {
        return chatRoomRepository.findAllByOrderByCreatedAtDesc();
    }

    public List<ChatMessage> getRoomMessages(String roomId) {
        return chatMessageRepository.findByRoomIdOrderByCreatedAtAsc(roomId);
    }

    public void deleteRoom(String roomId) {
        chatRoomRepository.deleteById(roomId);
    }
}