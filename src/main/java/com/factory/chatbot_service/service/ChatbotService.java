package com.factory.chatbot_service.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;

@Service
public class ChatbotService {

    private final BedrockRuntimeClient bedrockClient;
    private final ObjectMapper objectMapper;

    // Bedrock 관련 설정을 Properties 클래스 없이 개별 필드로 주입받습니다.
    @Value("${chatbot.bedrock.model-id}")
    private String modelId;

    @Value("${chatbot.bedrock.max-tokens}")
    private int maxTokens;

    @Value("${chatbot.bedrock.temperature}")
    private double temperature;

    @Value("${chatbot.bedrock.top-p}")
    private double topP;

    // 생성자에서 프로퍼티 클래스 의존성을 완전히 제거했습니다.
    public ChatbotService(BedrockRuntimeClient bedrockClient, ObjectMapper objectMapper) {
        this.bedrockClient = bedrockClient;
        this.objectMapper = objectMapper;
    }

    /// Function Start Here

    public String askInsightAI(String prompt) {
        System.out.println("[DEBUG] askInsightAI 시작");

        String normalizedPrompt = StringUtils.hasText(prompt) ? prompt.trim() : "";
        System.out.println("[DEBUG] Normalized Prompt: " + normalizedPrompt);

        try {
            /// Constructing the request body for Bedrock API call
            ObjectNode bodyNode = objectMapper.createObjectNode();
            bodyNode.put("anthropic_version", "bedrock-2023-05-31");
            bodyNode.put("max_tokens", maxTokens);
            bodyNode.put("temperature", temperature);
            bodyNode.put("top_p", topP);
            bodyNode.put("system", buildSystemPrompt());

            /// Constructing the message array
            ArrayNode messagesArray = objectMapper.createArrayNode();
            ObjectNode messageNode = objectMapper.createObjectNode();
            messageNode.put("role", "user");

            ArrayNode contentArray = objectMapper.createArrayNode();
            ObjectNode contentNode = objectMapper.createObjectNode();
            contentNode.put("type", "text");
            contentNode.put("text", normalizedPrompt);

            /// Adding the content array to the message array
            contentArray.add(contentNode);
            messageNode.set("content", contentArray);
            messagesArray.add(messageNode);
            bodyNode.set("messages", messagesArray);

            String jsonPayload = objectMapper.writeValueAsString(bodyNode);

            InvokeModelRequest request = InvokeModelRequest.builder()
                    .modelId(modelId)
                    .contentType("application/json")
                    .accept("application/json")
                    .body(SdkBytes.fromUtf8String(jsonPayload))
                    .build();

            InvokeModelResponse response = bedrockClient.invokeModel(request);
            String responseBody = response.body().asUtf8String();


            return objectMapper.readTree(responseBody)
                    .path("content")
                    .get(0)
                    .path("text")
                    .asText();
        } catch (Exception e) {
            e.printStackTrace();
            return "Error calling Bedrock: " + e.getMessage();
        }
    }

    private String buildSystemPrompt() {
        return "<role>\n"
                + "당신은 SIGMA 스마트 팩토리 플랫폼의 수석 시스템 엔지니어이자 데이터 분석 전문가 'DANAI'입니다.\n"
                + "어조는 매우 현실적이고, 객관적이며, 논리적이어야 합니다.\n"
                + "</role>\n\n"
                + "<rules>\n"
                + "1. 당신은 오직 스마트 팩토리 데이터 분석만 수행합니다. 점심 메뉴 추천, 일상 잡담 등 공장 업무와 관련 없는 질문은 절대 답변하지 마십시오.\n"
                + "2. 만약 무관한 질문이 들어오면, 정중히 거절하여 답변을 할 수 없다고 말한다.\n"
                + "</rules>\n\n"
                + "<output_format>\n"
                + "- 답변 시작 시 현재 공장의 위험도를 [정상], [주의], [위험] 중 하나로 명시할 것. 만약 무관한 질문이 들어오면, 표시하지 않는다.\n"
                + "- 항목별로 깔끔하게 마크다운(bullet points)을 사용할 것.\n"
                + "- 반드시 한국어로 답변할 것.\n"
                + "- 동일한 답변을 했을 경우, 반복하지 말 것.\n"
                + "</output_format>";
    }
}