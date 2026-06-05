package com.factory.chatbot_service.service;
import com.factory.chatbot_service.dto.RecipeRecommendDto;
import com.factory.chatbot_service.dto.LlmRecommendationDto;

import com.factory.chatbot_service.dto.RecipeHistoryCase;
import com.factory.chatbot_service.dto.SensorSnapshot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;

@Service
public class BedrockRecipeCandidateService {

    private final BedrockRuntimeClient bedrockRuntimeClient;
    private final ObjectMapper objectMapper;

    @Value("${chatbot.bedrock.model-id:}")
    private String modelId;

    public BedrockRecipeCandidateService(
            BedrockRuntimeClient bedrockRuntimeClient,
            ObjectMapper objectMapper
    ) {
        this.bedrockRuntimeClient = bedrockRuntimeClient;
        this.objectMapper = objectMapper;
    }

    public LlmRecommendationDto.Recommendation recommendCandidate(
            RecipeRecommendDto.Request request,
            RecipeRecommendDto.Response backendRecommendation
    ) {
        if (!StringUtils.hasText(modelId) || backendRecommendation == null) {
            return null;
        }

        try {
            String prompt = buildPrompt(request, backendRecommendation);
            Map<String, Object> body = Map.of(
                    "anthropic_version", "bedrock-2023-05-31",
                    "max_tokens", 1000,
                    "temperature", 0.1,
                    "messages", List.of(Map.of(
                            "role", "user",
                            "content", List.of(Map.of("type", "text", "text", prompt))
                    ))
            );

            InvokeModelRequest invokeModelRequest = InvokeModelRequest.builder()
                    .modelId(modelId)
                    .contentType("application/json")
                    .accept("application/json")
                    .body(SdkBytes.fromUtf8String(objectMapper.writeValueAsString(body)))
                    .build();

            String responseBody = bedrockRuntimeClient.invokeModel(invokeModelRequest)
                    .body()
                    .asUtf8String();
            String text = extractText(responseBody);
            return parseCandidateJson(text);
        } catch (Exception e) {
            return null;
        }
    }

    private String buildPrompt(
            RecipeRecommendDto.Request request,
            RecipeRecommendDto.Response backendRecommendation
    ) throws Exception {
        return """
                너는 반도체 공정 레시피 추천 후보를 생성하는 AI다.
                단, 최종 결정권자는 아니며 추천 후보는 백엔드 안전 검증을 반드시 통과해야 한다.

                반드시 지켜야 할 규칙:
                - 제공된 추천 JSON 안의 데이터만 사용한다.
                - recommendedMin, recommendedMax, recommendedValue 후보만 제안한다.
                - 각 추천값은 해당 파라미터의 min/max 범위 안에 있어야 한다.
                - 추천 JSON에 없는 파라미터를 새로 만들지 않는다.
                - 숫자는 소수점 첫째 자리까지 사용한다.
                - status, summary, recommendedParameters, evidence, warnings, confidence만 포함한 JSON만 반환한다.
                - Markdown, 설명 문장, 코드블록 없이 JSON 객체만 반환한다.

                반환 JSON schema:
                {
                  "status": "SUCCESS",
                  "summary": "추천 후보 요약",
                  "recommendedParameters": [
                    {
                      "name": "기존 파라미터명",
                      "recommendedMin": 0.0,
                      "recommendedMax": 0.0,
                      "recommendedValue": 0.0,
                      "reason": "추천 근거"
                    }
                  ],
                  "evidence": ["근거"],
                  "warnings": ["주의사항"],
                  "confidence": 0.0
                }

                사용자 질문:
                %s

                백엔드 기준 추천 JSON:
                %s
                """.formatted(
                request == null ? "" : nullToEmpty(request.getOperatorQuestion()),
                objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(backendRecommendation)
        );
    }

    private String extractText(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode content = root.path("content");
        if (!content.isArray()) {
            return null;
        }

        StringBuilder text = new StringBuilder();
        for (JsonNode item : content) {
            if ("text".equals(item.path("type").asText()) && item.path("text").isTextual()) {
                if (!text.isEmpty()) {
                    text.append(System.lineSeparator());
                }
                text.append(item.path("text").asText());
            }
        }
        return StringUtils.hasText(text.toString()) ? text.toString().trim() : null;
    }

    private LlmRecommendationDto.Recommendation parseCandidateJson(String text) throws Exception {
        if (!StringUtils.hasText(text)) {
            return null;
        }

        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }

        String json = text.substring(start, end + 1);
        return objectMapper.readValue(json, LlmRecommendationDto.Recommendation.class);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}