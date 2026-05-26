package com.factory.chatbot_service.service;

import java.util.concurrent.CompletableFuture;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockagentruntime.BedrockAgentRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockagentruntime.model.InvokeAgentRequest;
import software.amazon.awssdk.services.bedrockagentruntime.model.InvokeAgentResponseHandler; // Handler 추가

import java.util.UUID;

@Service
public class BedrockAgentService {
    private final BedrockAgentRuntimeAsyncClient runtimeAsyncClient;


    @Value("${aws.bedrock.agent-id}")
    private String agentId;

    @Value("${aws.bedrock.agent-alias-id}")
    private String agentAliasId;


    public BedrockAgentService(@Value("${aws.region}") String region) {
        this.runtimeAsyncClient = BedrockAgentRuntimeAsyncClient.builder()
            .region(Region.of(region))
            .credentialsProvider(DefaultCredentialsProvider.create())
            .build();
    }



    public String askInsightAI(String userPrompt) {
        System.out.println("[DEBUG] BedrockAgentService(askInsightAI) - user prompt : " +  userPrompt);

        String sessionId = UUID.randomUUID().toString();

        InvokeAgentRequest request = InvokeAgentRequest.builder()
            .agentId(agentId)
            .agentAliasId(agentAliasId)
            .sessionId(sessionId)
            .inputText(userPrompt)
            .build();

        StringBuilder finalResponse = new StringBuilder();
        CompletableFuture<Void> future = new CompletableFuture<>();

        try {
            runtimeAsyncClient.invokeAgent(request, InvokeAgentResponseHandler.builder()
                .onEventStream(stream -> stream.subscribe(event -> {
                    if (event instanceof software.amazon.awssdk.services.bedrockagentruntime.model.PayloadPart) {
                        software.amazon.awssdk.services.bedrockagentruntime.model.PayloadPart payloadPart =
                            (software.amazon.awssdk.services.bedrockagentruntime.model.PayloadPart) event;
                        finalResponse.append(payloadPart.bytes().asUtf8String());
                    }
                }))
                .onComplete(() -> future.complete(null))
                .onError(future::completeExceptionally)
                .build());

            future.join();
            return finalResponse.toString();
        } catch (Exception e) {
            return "Bedrock 호출 실패: " + e.getMessage();
        }
    }
}