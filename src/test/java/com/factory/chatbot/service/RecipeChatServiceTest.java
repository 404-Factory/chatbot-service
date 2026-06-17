package com.factory.chatbot.service;

import com.factory.chatbot.dto.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecipeChatServiceTest {

    @Mock
    private RecipeRecommendationService recipeRecommendationService;
    @Mock
    private BedrockRecipeAnswerService bedrockRecipeAnswerService;
    @Mock
    private BedrockRecipeCandidateService bedrockRecipeCandidateService;
    @Mock
    private RecommendationSelectionService recommendationSelectionService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    private RecipeChatServiceImpl recipeChatService;

    @BeforeEach
    void setUp() {
        recipeChatService = new RecipeChatServiceImpl(
                recipeRecommendationService,
                bedrockRecipeAnswerService,
                bedrockRecipeCandidateService,
                recommendationSelectionService,
                objectMapper
        );
    }

    @Test
    void testChat_NullRequest_ReturnsBadRequest() {
        // When
        ChatDto.Response response = recipeChatService.chat(null);

        // Then
        assertThat(response.getStatus()).isEqualTo("BAD_REQUEST");
        assertThat(response.getAnswer()).contains("질문 내용을 입력해 주세요.");
    }

    @Test
    void testChat_EmptyMessage_ReturnsBadRequest() {
        // Given
        ChatDto.Request request = new ChatDto.Request("", "session-123");

        // When
        ChatDto.Response response = recipeChatService.chat(request);

        // Then
        assertThat(response.getStatus()).isEqualTo("BAD_REQUEST");
    }

    @Test
    void testChat_NoEquipmentId_ReturnsInsufficientContext() {
        // Given
        ChatDto.Request request = new ChatDto.Request("레시피 추천을 해줘", "session-123");

        // When
        ChatDto.Response response = recipeChatService.chat(request);

        // Then
        assertThat(response.getStatus()).isEqualTo("INSUFFICIENT_CONTEXT");
        assertThat(response.getAnswer()).contains("추천할 설비 ID를 찾지 못했습니다.");
    }

    @Test
    void testChat_NoRecipeIntent_ReturnsUnsupportedIntent() {
        // Given
        ChatDto.Request request = new ChatDto.Request("1번 설비에 대해서 인사해줘", "session-123");

        // When
        ChatDto.Response response = recipeChatService.chat(request);

        // Then
        assertThat(response.getStatus()).isEqualTo("UNSUPPORTED_INTENT");
        assertThat(response.getAnswer()).contains("레시피 추천을 안전하게 수행하려면");
    }

    @Test
    void testChat_NormalStatus_NoAnomaly() {
        // Given
        ChatDto.Request request = new ChatDto.Request("EQP-DEPOSITION-001 설비의 레시피 추천해줘", "session-123");
        
        RecipeParameterValue param = RecipeParameterValue.builder()
                .name("Temperature")
                .currentValue(50.0)
                .min(10.0)
                .max(100.0)
                .build();
        
        RecipeRecommendDto.Response mockLocalResponse = RecipeRecommendDto.Response.builder()
                .status("SUCCESS")
                .recommendedParameters(List.of(param))
                .build();

        when(recipeRecommendationService.recommendLocally(any())).thenReturn(mockLocalResponse);

        // When
        ChatDto.Response response = recipeChatService.chat(request);

        // Then
        assertThat(response.getStatus()).isEqualTo("NORMAL_STATUS");
        assertThat(response.getAnswer()).contains("정상 가동 중");
        verifyNoInteractions(bedrockRecipeCandidateService, recommendationSelectionService, bedrockRecipeAnswerService);
    }

    @Test
    void testChat_AnomalyDetected_TriggersLlmAndSelection() throws Exception {
        // Given
        ChatDto.Request request = new ChatDto.Request("EQP-DEPOSITION-001 설비의 레시피 추천해줘", "session-123");
        
        // currentValue (120.0) exceeds max (100.0) -> Anomaly Detected!
        RecipeParameterValue param = RecipeParameterValue.builder()
                .name("Temperature")
                .currentValue(120.0)
                .min(10.0)
                .max(100.0)
                .build();
        
        RecipeRecommendDto.Response mockLocalResponse = RecipeRecommendDto.Response.builder()
                .status("SUCCESS")
                .recommendedParameters(List.of(param))
                .build();

        LlmRecommendationDto.Recommendation mockCandidate = new LlmRecommendationDto.Recommendation();
        RecipeRecommendDto.Response finalResponse = RecipeRecommendDto.Response.builder()
                .status("SUCCESS")
                .summary("Adjust temperature")
                .recommendedParameters(List.of(param))
                .build();

        RecommendationSelectionService.SelectionResult mockSelectionResult = mock(RecommendationSelectionService.SelectionResult.class);
        when(mockSelectionResult.getRecommendation()).thenReturn(finalResponse);

        when(recipeRecommendationService.recommendLocally(any())).thenReturn(mockLocalResponse);
        when(bedrockRecipeCandidateService.recommendCandidate(any(), any())).thenReturn(mockCandidate);
        when(recommendationSelectionService.select(any(), any())).thenReturn(mockSelectionResult);
        when(bedrockRecipeAnswerService.explain(any(), any())).thenReturn("LLM Explanation of recommendation");

        // When
        ChatDto.Response response = recipeChatService.chat(request);

        // Then
        assertThat(response.getStatus()).isEqualTo("SUCCESS");
        assertThat(response.getAnswer()).isEqualTo("LLM Explanation of recommendation");
        verify(bedrockRecipeCandidateService).recommendCandidate(any(), eq(mockLocalResponse));
        verify(recommendationSelectionService).select(eq(mockLocalResponse), eq(mockCandidate));
        verify(bedrockRecipeAnswerService).explain(any(), eq(finalResponse));
    }
}
