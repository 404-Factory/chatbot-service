package com.factory.chatbot.service;
import com.factory.chatbot.dto.RecipeRecommendDto;

import com.factory.chatbot.dto.RecipeHistoryCase;
import com.factory.chatbot.dto.RecipeParameter;

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
public class BedrockRecipeAnswerService {

    private final BedrockRuntimeClient bedrockRuntimeClient;
    private final ObjectMapper objectMapper;

    @Value("${chatbot.bedrock.model-id:}")
    private String modelId;

    public BedrockRecipeAnswerService(
            BedrockRuntimeClient bedrockRuntimeClient,
            ObjectMapper objectMapper
    ) {
        this.bedrockRuntimeClient = bedrockRuntimeClient;
        this.objectMapper = objectMapper;
    }

    public String explain(RecipeRecommendDto.Request request, RecipeRecommendDto.Response response) {
        if (!StringUtils.hasText(modelId) || response == null) {
            return null;
        }

        try {
            // 모델은 추천 숫자를 계산하지 않고, 백엔드 계산 결과를 이해하기 쉬운 답변으로 바꾼다.
            String prompt = buildPrompt(request, response);
            Map<String, Object> body = Map.of(
                    "anthropic_version", "bedrock-2023-05-31",
                    "max_tokens", 900,
                    "temperature", 0.2,
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
            return extractText(responseBody);
        } catch (Exception e) {
            return null;
        }
    }

    private String buildPrompt(RecipeRecommendDto.Request request, RecipeRecommendDto.Response response) throws Exception {
        return """
                다음 추천 JSON은 백엔드가 RDS/S3 데이터를 기반으로 안전 범위 안에서 계산한 공정 레시피 파라미터 추천 결과입니다.
                숫자 추천값을 새로 만들거나 변경하지 말고, 제공된 값과 근거만 설명하세요.

                답변 형식:
                1. 한 문장 요약
                2. 핵심 추천 범위
                   - 각 파라미터별 recommendedMin ~ recommendedMax를 표시
                   - 기준 범위 min ~ max도 함께 표시
                3. 추천 근거
                   - 각 파라미터별로 왜 그 범위가 추천됐는지 설명
                   - evidence의 baseSensorLimit, recommendedSensorRange, sensorAverage, historical RDS cases를 활용
                   - "유의점"이라는 제목 대신 "추천 근거"를 중심으로 작성
                4. 데이터 신뢰도
                   - confidence, S3 sampleCount, RDS history 개수를 바탕으로 간단히 설명
                5. 마지막 문장
                   - 이 추천은 자동 제어 명령이 아니라 작업자 검토용 제안이라고 명시

                작성 규칙:
                - 한국어로 작성
                - 내부 구현 설명은 하지 말 것
                - Bedrock Agent, Lambda, recursive invocation 같은 내부 용어는 언급하지 말 것
                - 추천 숫자를 변경하지 말 것
                - 추천 범위 밖의 값을 새로 제안하지 말 것
                - Markdown 표는 쓰지 말고 짧은 문단과 bullet만 사용할 것

                사용자 질문:
                %s

                추천 JSON:
                %s
                """.formatted(
                request == null ? "" : nullToEmpty(request.getOperatorQuestion()),
                objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(response)
        );
    }

    private String extractText(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode content = root.path("content");
        if (!content.isArray()) {
            return null;
        }

        // Claude 응답의 content 배열에서 text 블록만 모아 최종 답변으로 사용한다.
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

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}