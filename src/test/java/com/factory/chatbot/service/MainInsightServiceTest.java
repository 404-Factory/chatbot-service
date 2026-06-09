package com.factory.chatbot.service;

import com.factory.chatbot.infrastructure.entity.*;
import com.factory.chatbot.infrastructure.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MainInsightServiceTest {

    @Mock
    private BedrockAgentService bedrockAgentService;
    @Mock
    private AnomalyLogRepository anomalyLogRepository;
    @Mock
    private ChatRoomRepository chatRoomRepository;
    @Mock
    private ChatMessageRepository chatMessageRepository;
    @Mock
    private DefectInfoRepository defectInfoRepository;
    @Mock
    private EquipmentInfoRepository equipmentInfoRepository;

    @InjectMocks
    private MainInsightServiceImpl mainInsightService;

    private void setPrivateField(Object target, String fieldName, Object value) throws Exception {
        java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private AnomalyLog createAnomalyLog(Long logId, Long eqId, String param, String rule, String severity) throws Exception {
        AnomalyLog log = new AnomalyLog();
        setPrivateField(log, "logId", logId);
        setPrivateField(log, "equipmentId", eqId);
        setPrivateField(log, "recipeParameter", param);
        setPrivateField(log, "ruleName", rule);
        setPrivateField(log, "severity", severity);
        return log;
    }

    private DefectInfo createDefectInfo(Long id, String type, String processName, LocalDateTime occurredTime, Long eqId) throws Exception {
        DefectInfo defect = new DefectInfo();
        setPrivateField(defect, "id", id);
        setPrivateField(defect, "defectType", type);
        setPrivateField(defect, "causeProcessName", processName);
        setPrivateField(defect, "occurredTime", occurredTime);
        setPrivateField(defect, "causeEquipmentId", eqId);
        return defect;
    }

    private EquipmentInfo createEquipmentInfo(Long eqId, String name, Long processId) throws Exception {
        EquipmentInfo eq = new EquipmentInfo();
        setPrivateField(eq, "equipmentId", eqId);
        setPrivateField(eq, "equipmentName", name);
        setPrivateField(eq, "processId", processId);
        return eq;
    }

    @Test
    void testGetEquipmentAnalysis_OutOfDomain() {
        // Given
        String roomId = "room-123";
        String outOfDomainQuestion = "오늘 날씨 알려줘";
        
        when(chatRoomRepository.existsById(roomId)).thenReturn(false);

        // When
        String response = mainInsightService.getEquipmentAnalysis(null, outOfDomainQuestion, roomId);

        // Then
        assertThat(response).isEqualTo("공정 및 레시피, 설비 등 관련성이 없는 질문에는 답변할 수 없습니다.");
        
        verify(chatRoomRepository).save(any(ChatRoom.class));
        verify(chatMessageRepository, times(2)).save(any(ChatMessage.class));
        verifyNoInteractions(bedrockAgentService);
    }

    @Test
    void testGetEquipmentAnalysis_NonExistentEquipment() throws Exception {
        // Given
        String roomId = "room-123";
        String question = "세정 설비 99번 분석해줘";
        Integer equipmentId = 99;

        when(chatRoomRepository.existsById(roomId)).thenReturn(true);
        when(equipmentInfoRepository.findById(99L)).thenReturn(Optional.empty());

        // When
        String response = mainInsightService.getEquipmentAnalysis(equipmentId, question, roomId);

        // Then
        assertThat(response).isEqualTo("해당 설비는 시스템에 존재하지 않는 설비입니다.");
        verify(chatMessageRepository, times(2)).save(any(ChatMessage.class));
        verifyNoInteractions(bedrockAgentService);
    }

    @Test
    void testGetEquipmentAnalysis_ValidEquipment_NoLogsNoDefects() throws Exception {
        // Given
        String roomId = "room-123";
        String question = "세정 설비 1번 분석해줘";
        Integer equipmentId = 1;
        EquipmentInfo eqInfo = createEquipmentInfo(1L, "EQP-CLEANING-001", 100L);

        when(chatRoomRepository.existsById(roomId)).thenReturn(true);
        when(equipmentInfoRepository.findById(1L)).thenReturn(Optional.of(eqInfo));
        when(anomalyLogRepository.findTop5ByEquipmentIdOrderByOccurredTimeDesc(1L)).thenReturn(Collections.emptyList());
        when(bedrockAgentService.askInsightAI(anyString(), eq(roomId))).thenReturn("Mocked analysis response");

        // When
        String response = mainInsightService.getEquipmentAnalysis(equipmentId, question, roomId);

        // Then
        assertThat(response).isEqualTo("Mocked analysis response");
        verify(chatMessageRepository, times(2)).save(any(ChatMessage.class));
        
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(bedrockAgentService).askInsightAI(promptCaptor.capture(), eq(roomId));
        assertThat(promptCaptor.getValue()).contains("이상 로그 데이터");
        assertThat(promptCaptor.getValue()).contains("세정 설비 1번 분석해줘");
    }

    @Test
    void testGetEquipmentAnalysis_WithLogsAndDefects() throws Exception {
        // Given
        String roomId = "room-123";
        String question = "세정 설비 1번 이상 분석";
        Integer equipmentId = 1;
        EquipmentInfo eqInfo = createEquipmentInfo(1L, "EQP-CLEANING-001", 100L);
        
        List<AnomalyLog> logs = Arrays.asList(
                createAnomalyLog(10L, 1L, "Temperature", "Rule 1", "HIGH"),
                createAnomalyLog(11L, 1L, "Pressure", "Rule 2", "CRITICAL")
        );
        List<DefectInfo> defects = Arrays.asList(
                createDefectInfo(20L, "Scratch", "CLEANING", LocalDateTime.now(), 1L)
        );

        when(chatRoomRepository.existsById(roomId)).thenReturn(true);
        when(equipmentInfoRepository.findById(1L)).thenReturn(Optional.of(eqInfo));
        when(anomalyLogRepository.findTop5ByEquipmentIdOrderByOccurredTimeDesc(1L)).thenReturn(logs);
        when(defectInfoRepository.findByCauseEquipmentIdOrderByOccurredTimeDesc(1L)).thenReturn(defects);
        when(bedrockAgentService.askInsightAI(anyString(), eq(roomId))).thenReturn("Mocked response with details");

        // When
        String response = mainInsightService.getEquipmentAnalysis(equipmentId, question, roomId);

        // Then
        assertThat(response).isEqualTo("Mocked response with details");
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(bedrockAgentService).askInsightAI(promptCaptor.capture(), eq(roomId));
        
        String capturedPrompt = promptCaptor.getValue();
        assertThat(capturedPrompt).contains("Temperature");
        assertThat(capturedPrompt).contains("Rule 2");
        assertThat(capturedPrompt).contains("Scratch");
        assertThat(capturedPrompt).contains("세정 설비 1번 이상 분석");
    }

    @Test
    void testGetEquipmentAnalysis_NullEquipmentId() {
        // Given
        String roomId = "room-123";
        String question = "일반 공정 현황 알려줘";

        when(chatRoomRepository.existsById(roomId)).thenReturn(true);
        when(bedrockAgentService.askInsightAI(question, roomId)).thenReturn("Mocked general response");

        // When
        String response = mainInsightService.getEquipmentAnalysis(null, question, roomId);

        // Then
        assertThat(response).isEqualTo("Mocked general response");
        verify(bedrockAgentService).askInsightAI(question, roomId);
        verify(chatMessageRepository, times(2)).save(any(ChatMessage.class));
    }

    @Test
    void testGetAllRooms() {
        // Given
        List<ChatRoom> expectedRooms = Arrays.asList(new ChatRoom(), new ChatRoom());
        when(chatRoomRepository.findAllByOrderByCreatedAtDesc()).thenReturn(expectedRooms);

        // When
        List<ChatRoom> actualRooms = mainInsightService.getAllRooms();

        // Then
        assertThat(actualRooms).hasSize(2);
        verify(chatRoomRepository).findAllByOrderByCreatedAtDesc();
    }

    @Test
    void testGetRoomMessages() {
        // Given
        String roomId = "room-123";
        List<ChatMessage> expectedMsgs = Arrays.asList(new ChatMessage(), new ChatMessage());
        when(chatMessageRepository.findByRoomIdOrderByCreatedAtAsc(roomId)).thenReturn(expectedMsgs);

        // When
        List<ChatMessage> actualMsgs = mainInsightService.getRoomMessages(roomId);

        // Then
        assertThat(actualMsgs).hasSize(2);
        verify(chatMessageRepository).findByRoomIdOrderByCreatedAtAsc(roomId);
    }

    @Test
    void testDeleteRoom() {
        // When
        mainInsightService.deleteRoom("room-123");

        // Then
        verify(chatRoomRepository).deleteById("room-123");
    }

    @Test
    void testSaveMessageDirectly_NewRoom() {
        // Given
        String roomId = "new-room-id";
        String title = "Custom Title";
        String content = "Hello content";
        
        when(chatRoomRepository.existsById(roomId)).thenReturn(false);

        // When
        mainInsightService.saveMessageDirectly(roomId, "USER", content, title);

        // Then
        verify(chatRoomRepository).save(any(ChatRoom.class));
        verify(chatMessageRepository).save(any(ChatMessage.class));
    }
    
    @Test
    void testSaveMessageDirectly_ExistingRoom() {
        // Given
        String roomId = "existing-room-id";
        String content = "Hello content";
        
        when(chatRoomRepository.existsById(roomId)).thenReturn(true);

        // When
        mainInsightService.saveMessageDirectly(roomId, "USER", content, null);

        // Then
        verify(chatRoomRepository, never()).save(any(ChatRoom.class));
        verify(chatMessageRepository).save(any(ChatMessage.class));
    }
}
