package com.factory.chatbot_service.service;

import com.factory.chatbot_service.dto.RecipeParameter;
import com.factory.chatbot_service.dto.RecipeRecommendRequest;
import com.factory.chatbot_service.dto.SimilarRecipeCase;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.services.bedrockagentruntime.BedrockAgentRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockagentruntime.model.InvokeAgentRequest;
import software.amazon.awssdk.services.bedrockagentruntime.model.InvokeAgentResponseHandler;

@Service
public class BedrockRecipeExplanationService {

    private final BedrockAgentRuntimeAsyncClient bedrockAgentRuntimeAsyncClient;

    @Value("${chatbot.bedrock.agent-id:}")
    private String agentId;

    @Value("${chatbot.bedrock.agent-alias-id:}")
    private String agentAliasId;

    @Value("${chatbot.bedrock.enable-trace:false}")
    private boolean enableTrace;

    public BedrockRecipeExplanationService(BedrockAgentRuntimeAsyncClient bedrockAgentRuntimeAsyncClient) {
        this.bedrockAgentRuntimeAsyncClient = bedrockAgentRuntimeAsyncClient;
    }

    public String generateExplanation(
            RecipeRecommendRequest request,
            RecipeParameter recommendedRecipe,
            List<SimilarRecipeCase> similarCases,
            double confidence
    ) {
        if (!StringUtils.hasText(agentId) || !StringUtils.hasText(agentAliasId)) {
            throw new IllegalStateException("Bedrock agent-id and agent-alias-id must be configured.");
        }

        String prompt = buildPrompt(request, recommendedRecipe, similarCases, confidence);
        String sessionId = "recipe-" + UUID.randomUUID();
        StringBuilder completion = new StringBuilder();

        InvokeAgentRequest invokeAgentRequest = InvokeAgentRequest.builder()
                .agentId(agentId)
                .agentAliasId(agentAliasId)
                .sessionId(sessionId)
                .inputText(prompt)
                .enableTrace(enableTrace)
                .build();

        InvokeAgentResponseHandler.Visitor visitor = InvokeAgentResponseHandler.Visitor.builder()
                .onChunk(chunk -> completion.append(chunk.bytes().asUtf8String()))
                .build();

        InvokeAgentResponseHandler handler = InvokeAgentResponseHandler.builder()
                .subscriber(visitor)
                .build();

        bedrockAgentRuntimeAsyncClient.invokeAgent(invokeAgentRequest, handler).join();

        return completion.toString();
    }

    private String buildPrompt(
            RecipeRecommendRequest request,
            RecipeParameter recommendedRecipe,
            List<SimilarRecipeCase> similarCases,
            double confidence
    ) {
        return """
                너는 제조 공정 레시피 추천 AI다.

                아래 계산 결과를 바탕으로 운영자에게 제공할 추천 설명을 작성해라.

                반드시 지켜야 할 규칙:
                1. 데이터에 없는 수치를 새로 만들지 마라.
                2. 추천값을 자동 제어 명령처럼 말하지 마라.
                3. 운영자 검토용 제안이라고 명시해라.
                4. 안전 범위 검증이 필요하다고 말해라.
                5. 한국어로 답해라.
                6. 4문장 이내로 작성해라.

                현재 입력:
                - equipmentId: %s
                - processId: %s
                - productId: %s
                - defectType: %s
                - current temperature: %.1f
                - current pressure: %.1f
                - current speed: %.1f
                - current duration: %.1f

                추천 결과:
                - recommended temperature: %.1f
                - recommended pressure: %.1f
                - recommended speed: %.1f
                - recommended duration: %.1f
                - confidence: %.2f

                유사 레시피 후보:
                %s
                """.formatted(
                request.getEquipmentId(),
                request.getProcessId(),
                request.getProductId(),
                request.getDefectType(),
                request.getCurrentRecipe().getTemperature(),
                request.getCurrentRecipe().getPressure(),
                request.getCurrentRecipe().getSpeed(),
                request.getCurrentRecipe().getDuration(),
                recommendedRecipe.getTemperature(),
                recommendedRecipe.getPressure(),
                recommendedRecipe.getSpeed(),
                recommendedRecipe.getDuration(),
                confidence,
                buildSimilarCasePrompt(similarCases)
        );
    }

    private String buildSimilarCasePrompt(List<SimilarRecipeCase> similarCases) {
        StringBuilder builder = new StringBuilder();
        for (SimilarRecipeCase history : similarCases) {
            builder.append("- equipment_rec_id: ")
                    .append(history.getId())
                    .append(", defectRate: ")
                    .append(history.getDefectRate())
                    .append("%, defectCount: ")
                    .append(history.getDefectCount())
                    .append(", productQuantity: ")
                    .append(history.getProductQuantity())
                    .append(", recipe: temperature=")
                    .append(history.getRecipe().getTemperature())
                    .append(", pressure=")
                    .append(history.getRecipe().getPressure())
                    .append(", speed=")
                    .append(history.getRecipe().getSpeed())
                    .append(", duration=")
                    .append(history.getRecipe().getDuration())
                    .append(System.lineSeparator());
        }
        return builder.toString();
    }
}