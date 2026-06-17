package com.factory.chatbot.service;
import com.factory.chatbot.dto.RecipeAgentDto;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.services.bedrockagentruntime.BedrockAgentRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockagentruntime.model.InvokeAgentRequest;
import software.amazon.awssdk.services.bedrockagentruntime.model.InvokeAgentResponseHandler;

@Service
public class RecipeAgentClient {

    private static final Logger log = LoggerFactory.getLogger(RecipeAgentClient.class);

    private final BedrockAgentRuntimeAsyncClient bedrockAgentRuntimeAsyncClient;
    private final ObjectMapper objectMapper;

    @Value("${chatbot.bedrock.agent-id:}")
    private String agentId;

    @Value("${chatbot.bedrock.agent-alias-id:}")
    private String agentAliasId;

    @Value("${chatbot.bedrock.enable-trace:false}")
    private boolean enableTrace;

    @Value("${chatbot.bedrock.chat-timeout-seconds:60}")
    private long chatTimeoutSeconds;

    public RecipeAgentClient(
            @Qualifier("recipeBedrockAgentClient") BedrockAgentRuntimeAsyncClient bedrockAgentRuntimeAsyncClient,
            ObjectMapper objectMapper
    ) {
        this.bedrockAgentRuntimeAsyncClient = bedrockAgentRuntimeAsyncClient;
        this.objectMapper = objectMapper;
    }

    public RecipeAgentDto.Response recommend(RecipeAgentDto.Request request) {
        if (!StringUtils.hasText(agentId) || !StringUtils.hasText(agentAliasId)) {
            throw new IllegalStateException("Bedrock agent-id and agent-alias-id must be configured.");
        }

        String sessionId = "recipe-" + UUID.randomUUID();
        String prompt = buildPrompt(request);
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

        try {
            log.info("Calling Bedrock Agent Runtime: agentId={}, aliasId={}, timeoutSeconds={}",
                    agentId, agentAliasId, chatTimeoutSeconds);
            bedrockAgentRuntimeAsyncClient.invokeAgent(invokeAgentRequest, handler)
                    .orTimeout(chatTimeoutSeconds, TimeUnit.SECONDS)
                    .join();
            log.info("Bedrock Agent Runtime completed: responseChars={}", completion.length());
        } catch (CompletionException e) {
            if (e.getCause() instanceof TimeoutException) {
                throw new IllegalStateException("Bedrock Agent response timed out after "
                        + Duration.ofSeconds(chatTimeoutSeconds).toSeconds() + " seconds.", e);
            }
            throw new IllegalStateException("Bedrock Agent invocation failed.", e);
        }

        return parseResponse(completion.toString());
    }

    private String buildPrompt(RecipeAgentDto.Request request) {
        return """
                You are a manufacturing process recipe recommendation agent.

                Recommend process recipe parameters using:
                - the operator's natural-language question
                - backend-resolved production context
                - the current recipe requested by the operator
                  or resolved from RDS when the operator did not provide it
                - the latest S3 sensor snapshot
                - historical RDS recipe and defect records
                - optional insight analysis context

                Rules:
                1. Return only valid JSON. Do not wrap it in markdown.
                2. Do not invent evidence that is not present in the provided context.
                3. Treat the result as an operator review suggestion, not an automatic control command.
                4. If data is insufficient, set status to "INSUFFICIENT_DATA" and recommendedRecipe to null.
                5. If you recommend a recipe, include temperature, pressure, speed, and duration.
                6. Keep all text in Korean.
                7. Prefer backend-resolved fields over assumptions. If contextWarnings contains gaps,
                   mention the uncertainty in warnings.
                8. Do not call an action group, Lambda, or external API. The backend already provided
                   the RDS/S3 context needed for this recommendation.

                JSON schema:
                {
                  "status": "SUCCESS | INSUFFICIENT_DATA",
                  "summary": "short Korean explanation",
                  "recommendedParameters": [
                    {
                      "name": "same parameter name from currentRecipeParameters",
                      "min": 0.0,
                      "max": 0.0,
                      "currentValue": 0.0,
                      "recommendedMin": 0.0,
                      "recommendedMax": 0.0,
                      "recommendedValue": 0.0,
                      "unit": null
                    }
                  ],
                  "recommendedRecipe": {
                    "temperature": 0.0,
                    "pressure": 0.0,
                    "speed": 0.0,
                    "duration": 0.0
                  },
                  "expectedEffect": {
                    "targetMetric": "defect_rate",
                    "direction": "decrease",
                    "description": "Korean description"
                  },
                  "evidence": ["Korean evidence"],
                  "warnings": ["Korean warning"],
                  "confidence": 0.0
                }

                The database uses dynamic recipe parameter names. Prefer recommendedParameters.
                For recommendedParameters, min and max are the base sensor limit range.
                recommendedMin and recommendedMax are the equipment-specific recommended sensor measurement range.
                recommendedValue is only the midpoint of recommendedMin and recommendedMax for compatibility.
                Only fill recommendedRecipe when temperature, pressure, speed, and duration
                actually exist in the provided context.

                Input context:
                %s
                """.formatted(toJson(request));
    }

    private String toJson(RecipeAgentDto.Request request) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(request);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize recipe agent request.", e);
        }
    }

    private RecipeAgentDto.Response parseResponse(String completion) {
        String json = extractJsonObject(completion);
        try {
            RecipeAgentDto.Response response = objectMapper.readValue(json, RecipeAgentDto.Response.class);
            if (response.getEvidence() == null) {
                response.setEvidence(List.of());
            }
            if (response.getWarnings() == null) {
                response.setWarnings(List.of());
            }
            if (response.getStatus() == null) {
                response.setStatus("SUCCESS");
            }
            return response;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Bedrock agent returned a non-JSON recipe response: " + completion, e);
        }
    }

    private String extractJsonObject(String value) {
        int start = value.indexOf('{');
        int end = value.lastIndexOf('}');
        if (start < 0 || end < start) {
            throw new IllegalStateException("Bedrock agent response does not contain a JSON object.");
        }
        return value.substring(start, end + 1);
    }
}