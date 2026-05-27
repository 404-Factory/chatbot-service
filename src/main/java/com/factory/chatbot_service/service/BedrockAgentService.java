package com.factory.chatbot_service.service;

import java.util.concurrent.CompletableFuture;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockagentruntime.BedrockAgentRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockagentruntime.model.InvokeAgentRequest;
import software.amazon.awssdk.services.bedrockagentruntime.model.InvokeAgentResponseHandler;

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





    // 🔍 인자에 roomId를 추가합니다.
    public String askInsightAI(String userPrompt, String roomId) {
        System.out.println("[DEBUG] BedrockAgentService(askInsightAI) - user prompt : " +  userPrompt);

        // ❌ 기존의 랜덤 UUID 생성을 과감히 제거합니다.
        // String sessionId = UUID.randomUUID().toString();

        InvokeAgentRequest request = InvokeAgentRequest.builder()
            .agentId(agentId)
            .agentAliasId(agentAliasId)
            .sessionId(roomId) // 🎯 [해결 포인트] 실제 DB 방 ID를 세션 ID로 매핑하여 메모리 유지!
            .inputText(userPrompt)
            .enableTrace(true)
            .build();


        System.out.println("[DEBUG] BedrockAgentService(askInsightAI) - request : " + request);

        StringBuilder finalResponse = new StringBuilder();
        CompletableFuture<Void> future = new CompletableFuture<>();

        try {
            runtimeAsyncClient.invokeAgent(request, InvokeAgentResponseHandler.builder()
                .onEventStream(stream -> stream.subscribe(event -> {

                    // 1. 챗봇의 답변 텍스트 조각(Payload) 누적 처리 (기존 동일)
                    if (event instanceof software.amazon.awssdk.services.bedrockagentruntime.model.PayloadPart) {
                        software.amazon.awssdk.services.bedrockagentruntime.model.PayloadPart payloadPart =
                            (software.amazon.awssdk.services.bedrockagentruntime.model.PayloadPart) event;
                        finalResponse.append(payloadPart.bytes().asUtf8String());
                    }

                    // 2. 🔍 [진단 모드] 조건문 필터를 완전히 제거하여 모든 트레이스를 무조건 출력합니다.
                    else if (event instanceof software.amazon.awssdk.services.bedrockagentruntime.model.TracePart) {
                        software.amazon.awssdk.services.bedrockagentruntime.model.TracePart tracePart =
                            (software.amazon.awssdk.services.bedrockagentruntime.model.TracePart) event;

                        if (tracePart.trace() != null) {
                            // 💡 에이전트가 단 한 조각의 사고 과정이라도 뱉으면 콘솔에 강제로 보라색 스트림을 쏩니다.
                            System.out.println("\n\u001B[35m[AWS BEDROCK RAW TRACE STREAM]\u001B[0m " + tracePart.trace().toString());
                            System.out.println("====================================================================\n");
                        }
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