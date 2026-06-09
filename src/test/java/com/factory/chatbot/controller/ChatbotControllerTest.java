package com.factory.chatbot.controller;

import com.factory.chatbot.dto.ChatDto;
import com.factory.chatbot.infrastructure.entity.ChatMessage;
import com.factory.chatbot.infrastructure.entity.ChatRoom;
import com.factory.chatbot.service.MainInsightService;
import com.factory.chatbot.service.RecipeChatService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class ChatbotControllerTest {

    private MockMvc mockMvc;

    @Mock
    private MainInsightService mainInsightService;

    @Mock
    private RecipeChatService recipeChatService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        ChatbotController chatbotController = new ChatbotController(mainInsightService, recipeChatService);
        mockMvc = MockMvcBuilders.standaloneSetup(chatbotController).build();
    }

    @Test
    void testQueryInsightAI() throws Exception {
        // Given
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("equipmentId", 1);
        requestBody.put("content", "세정 설비 1번 분석해줘");
        requestBody.put("roomId", "room-123");

        when(mainInsightService.getEquipmentAnalysis(eq(1), eq("세정 설비 1번 분석해줘"), eq("room-123")))
                .thenReturn("Mocked analysis response");

        // When & Then
        mockMvc.perform(post("/api/chat/insight")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value("Mocked analysis response"));
    }

    @Test
    void testSaveMessageDirectly() throws Exception {
        // Given
        Map<String, String> requestBody = new HashMap<>();
        requestBody.put("roomId", "room-123");
        requestBody.put("role", "USER");
        requestBody.put("content", "Hello");
        requestBody.put("title", "Title");

        doNothing().when(mainInsightService).saveMessageDirectly("room-123", "USER", "Hello", "Title");

        // When & Then
        mockMvc.perform(post("/api/chat/message")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestBody)))
                .andExpect(status().isOk());

        verify(mainInsightService).saveMessageDirectly("room-123", "USER", "Hello", "Title");
    }

    @Test
    void testGetAllRooms() throws Exception {
        // Given
        ChatRoom room1 = new ChatRoom();
        room1.setRoomId("room-1");
        room1.setTitle("Title 1");
        room1.setCreatedAt(LocalDateTime.now());

        ChatRoom room2 = new ChatRoom();
        room2.setRoomId("room-2");
        room2.setTitle("Title 2");
        room2.setCreatedAt(LocalDateTime.now());

        when(mainInsightService.getAllRooms()).thenReturn(Arrays.asList(room1, room2));

        // When & Then
        mockMvc.perform(get("/api/chat/rooms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].roomId").value("room-1"))
                .andExpect(jsonPath("$[0].title").value("Title 1"))
                .andExpect(jsonPath("$[1].roomId").value("room-2"))
                .andExpect(jsonPath("$[1].title").value("Title 2"));
    }

    @Test
    void testGetRoomMessages() throws Exception {
        // Given
        ChatMessage msg1 = new ChatMessage();
        msg1.setMessageId(1L);
        msg1.setRoomId("room-1");
        msg1.setRole("USER");
        msg1.setContent("Hello");

        ChatMessage msg2 = new ChatMessage();
        msg2.setMessageId(2L);
        msg2.setRoomId("room-1");
        msg2.setRole("ASSISTANT");
        msg2.setContent("Hi");

        when(mainInsightService.getRoomMessages("room-1")).thenReturn(Arrays.asList(msg1, msg2));

        // When & Then
        mockMvc.perform(get("/api/chat/rooms/room-1/messages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].content").value("Hello"))
                .andExpect(jsonPath("$[0].role").value("USER"))
                .andExpect(jsonPath("$[1].content").value("Hi"))
                .andExpect(jsonPath("$[1].role").value("ASSISTANT"));
    }

    @Test
    void testDeleteRoom() throws Exception {
        // Given
        doNothing().when(mainInsightService).deleteRoom("room-1");

        // When & Then
        mockMvc.perform(delete("/api/chat/rooms/room-1"))
                .andExpect(status().isNoContent());

        verify(mainInsightService).deleteRoom("room-1");
    }

    @Test
    void testQueryRecipeAI() throws Exception {
        // Given
        ChatDto.Request request = new ChatDto.Request("Recipe recommend please", "session-123");
        ChatDto.Response response = ChatDto.Response.builder()
                .status("SUCCESS")
                .answer("Here is your recipe")
                .sessionId("session-123")
                .build();

        when(recipeChatService.chat(any(ChatDto.Request.class))).thenReturn(response);

        // When & Then
        mockMvc.perform(post("/api/chat/recipe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.answer").value("Here is your recipe"))
                .andExpect(jsonPath("$.sessionId").value("session-123"));
    }
}
