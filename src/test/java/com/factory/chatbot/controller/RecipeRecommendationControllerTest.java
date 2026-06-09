package com.factory.chatbot.controller;

import com.factory.chatbot.dto.RecipeRecommendDto;
import com.factory.chatbot.service.RecipeRecommendationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

@ExtendWith(MockitoExtension.class)
class RecipeRecommendationControllerTest {

    private MockMvc mockMvc;

    @Mock
    private RecipeRecommendationService recipeRecommendationService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        RecipeRecommendationController controller = new RecipeRecommendationController(
                recipeRecommendationService,
                objectMapper
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void testRecommend_InvalidJson_ReturnsBadRequest() throws Exception {
        // Given
        String invalidJson = "{invalid-json-body";

        // When & Then
        mockMvc.perform(post("/api/ai/recipe/recommend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.warnings[0]").value("Invalid or empty JSON request body."));
        
        verifyNoInteractions(recipeRecommendationService);
    }

    @Test
    void testRecommend_EmptyBody_ReturnsBadRequest() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/ai/recipe/recommend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BAD_REQUEST"));
        
        verifyNoInteractions(recipeRecommendationService);
    }

    @Test
    void testRecommend_ValidRequest_ReturnsSuccess() throws Exception {
        // Given
        RecipeRecommendDto.Request request = RecipeRecommendDto.Request.builder()
                .equipmentId("1")
                .defectType("Scratch")
                .operatorQuestion("How to reduce scratch on EQP 1?")
                .build();

        RecipeRecommendDto.Response response = RecipeRecommendDto.Response.builder()
                .status("SUCCESS")
                .summary("Lower temperature by 5 degrees")
                .confidence(0.85)
                .build();

        when(recipeRecommendationService.recommend(any(RecipeRecommendDto.Request.class))).thenReturn(response);

        // When & Then
        mockMvc.perform(post("/api/ai/recipe/recommend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.summary").value("Lower temperature by 5 degrees"))
                .andExpect(jsonPath("$.confidence").value(0.85));

        verify(recipeRecommendationService).recommend(any(RecipeRecommendDto.Request.class));
    }

    @Test
    void testVersion() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/ai/recipe/version"))
                .andExpect(status().isOk())
                .andExpect(content().string("recipe-controller-raw-json-v2"));
    }
}
