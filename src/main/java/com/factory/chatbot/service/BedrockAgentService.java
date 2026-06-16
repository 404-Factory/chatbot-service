// ANTIGRAVITY_INTELLIJ_CONNECTION_TEST: SUCCESS
package com.factory.chatbot.service;

import java.util.concurrent.CompletableFuture;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockagentruntime.BedrockAgentRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockagentruntime.model.InvokeAgentRequest;
import software.amazon.awssdk.services.bedrockagentruntime.model.InvokeAgentResponseHandler;
import jakarta.annotation.PostConstruct;

@Service
public class BedrockAgentService {
    private final BedrockAgentRuntimeAsyncClient runtimeAsyncClient;

    @Value("${aws.bedrock.agent-id}")
    private String agentId;

    @Value("${aws.bedrock.agent-alias-id}")
    private String agentAliasId;

    public BedrockAgentService(
            @Qualifier("insightBedrockAgentClient") BedrockAgentRuntimeAsyncClient runtimeAsyncClient
    ) {
        this.runtimeAsyncClient = runtimeAsyncClient;
    }

    @PostConstruct
    public void initClockSkew() {
        System.out.println("[INIT] Starting clock skew synchronization with AWS Bedrock...");
        try {
            runtimeAsyncClient.invokeAgent(
                InvokeAgentRequest.builder()
                    .agentId(agentId)
                    .agentAliasId(agentAliasId)
                    .sessionId("skew-sync-session")
                    .inputText("skew-sync-ping")
                    .build(),
                InvokeAgentResponseHandler.builder()
                    .onEventStream(stream -> {})
                    .build()
            ).handle((res, ex) -> {
                System.out.println("[INIT] Clock skew sync completed (successfully parsed headers or handled error).");
                return null;
            });
        } catch (Exception e) {
            System.out.println("[WARN] Clock skew sync initiation warning: " + e.getMessage());
        }
    }

    public String askInsightAI(String userPrompt, String roomId) {
        System.out.println("[DEBUG] BedrockAgentService(askInsightAI) - user prompt : " +  userPrompt);

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
                            System.out.println("\n\u001B[35m[AWS BEDROCK RAW TRACE STREAM]\u001B[0m " + tracePart.trace().toString());
                            System.out.println("====================================================================\n");
                        }
                    }

                }))
                .onComplete(() -> future.complete(null))
                .onError(future::completeExceptionally)
                .build());

            future.join();
            String rawResponse = finalResponse.toString();
            System.out.println("[DEBUG] Bedrock RAW Response: " + rawResponse);
            return cleanResponse(rawResponse, userPrompt);
        } catch (Exception e) {
            return "Bedrock 호출 실패: " + e.getMessage();
        }
    }

    private String cleanResponse(String response, String userPrompt) {
        if (response == null) return "";
        
        // Remove any XML/HTML tags that might leak from prompt structures or tool responses
        response = response.replaceAll("<[^>]*>", "");
        
        // If it's a normal report containing the data timestamp, keep it as-is (but check/prepend fallback warning if applicable)
        if (response.contains("데이터 기준 시각:")) {
            // 1. Extract requested date from userPrompt (e.g. YYYY-MM-DD or YYYY년 MM월 DD일)
            String requestedDate = null;
            if (userPrompt != null) {
                java.util.regex.Pattern p1 = java.util.regex.Pattern.compile("(\\d{4})[-/](\\d{2})[-/](\\d{2})");
                java.util.regex.Matcher m1 = p1.matcher(userPrompt);
                if (m1.find()) {
                    requestedDate = m1.group(1) + "-" + m1.group(2) + "-" + m1.group(3);
                } else {
                    java.util.regex.Pattern p2 = java.util.regex.Pattern.compile("(\\d{4})년\\s*(\\d{1,2})월\\s*(\\d{1,2})일");
                    java.util.regex.Matcher m2 = p2.matcher(userPrompt);
                    if (m2.find()) {
                        try {
                            String y = m2.group(1);
                            String m = String.format("%02d", Integer.parseInt(m2.group(2)));
                            String d = String.format("%02d", Integer.parseInt(m2.group(3)));
                            requestedDate = y + "-" + m + "-" + d;
                        } catch (Exception e) {
                            // ignore
                        }
                    }
                }
            }

            // 2. Extract actual date from response
            String actualDate = null;
            java.util.regex.Pattern datePattern = java.util.regex.Pattern.compile("데이터 기준 시각:\\s*(\\d{4})년\\s*(\\d{1,2})월\\s*(\\d{1,2})일");
            java.util.regex.Matcher matcher = datePattern.matcher(response);
            if (matcher.find()) {
                try {
                    String y = matcher.group(1);
                    String m = String.format("%02d", Integer.parseInt(matcher.group(2)));
                    String d = String.format("%02d", Integer.parseInt(matcher.group(3)));
                    actualDate = y + "-" + m + "-" + d;
                } catch (Exception e) {
                    // ignore
                }
            } else {
                java.util.regex.Pattern isoPattern = java.util.regex.Pattern.compile("데이터 기준 시각:\\s*(\\d{4})-(\\d{2})-(\\d{2})");
                java.util.regex.Matcher isoMatcher = isoPattern.matcher(response);
                if (isoMatcher.find()) {
                    actualDate = isoMatcher.group(1) + "-" + isoMatcher.group(2) + "-" + isoMatcher.group(3);
                }
            }

            // 3. Prepend appropriate warning
            boolean hasWarning = response.contains("데이터가 없어") || response.contains("최신 데이터를 기반으로");
            if (!hasWarning && actualDate != null) {
                if (requestedDate != null) {
                    if (!requestedDate.equals(actualDate)) {
                        response = "⚠️ 요청하신 날짜(" + requestedDate + ")의 데이터가 없습니다. 대신 저장소에 등록된 가장 최근 날짜(" + actualDate + ") 데이터를 기반으로 분석을 진행합니다.\n\n" + response.trim();
                    }
                } else {
                    // Check if actualDate is today (or system default today 2026-06-02)
                    java.time.LocalDate today = java.time.LocalDate.now();
                    String todayStr = today.toString();
                    boolean isToday = todayStr.equals(actualDate) || "2026-06-02".equals(actualDate);
                    if (!isToday) {
                        response = "⚠️ 금일 날짜 기준으로 저장된 데이터가 없어 저장소에 등록된 최신 데이터를 기반으로 출력하겠습니다.\n\n" + response.trim();
                    }
                }
            }
            return response.trim();
        }
        
        // Extract clean clarification questions if present (with fallback substring match to prevent thoughts leak)
        if (response.contains("조회를 원하는 공정 단계를 말씀해 주세요. (CLEANING, DEPOSITION, ETCH, PHOTO)")
            || (response.contains("CLEANING, DEPOSITION, ETCH, PHOTO") && response.contains("공정"))) {
            return "조회를 원하는 공정 단계를 말씀해 주세요. (CLEANING, DEPOSITION, ETCH, PHOTO)";
        }
        if (response.contains("어떤 로그를 보고 싶으신가요? (이상 로그, 센서 데이터, 요약 데이터 중 선택)")
            || (response.contains("이상 로그, 센서 데이터, 요약 데이터") && response.contains("어떤"))) {
            return "어떤 로그를 보고 싶으신가요? (이상 로그, 센서 데이터, 요약 데이터 중 선택)";
        }
        if (response.contains("어떤 설비 번호의 조회를 원하시나요?")
            || response.contains("설비 번호의 조회를 원하시나요")
            || (response.contains("설비 번호") && response.contains("원하시나요"))) {
            return "어떤 설비 번호의 조회를 원하시나요?";
        }
        if (response.contains("조회를 원하는 특정 날짜가 있으신가요?")
            || response.contains("특정 날짜가 있으신가요")
            || (response.contains("특정 날짜") && response.contains("있으신가요"))) {
            return "조회를 원하는 특정 날짜가 있으신가요?";
        }
        if (response.contains("스마트 팩토리 공정 진단 외의 질문에는 답변할 수 없습니다.")
            || response.contains("공정 진단 외의 질문에는 답변할 수 없습니다")
            || (response.contains("공정 진단") && response.contains("답변할 수 없습니다"))) {
            return "스마트 팩토리 공정 진단 외의 질문에는 답변할 수 없습니다.";
        }
        if (response.contains("해당 설비는 시스템에 존재하지 않는 설비입니다.")
            || response.contains("해당 설비는 시스템에 존재하지 않는 설비")
            || (response.contains("존재하지 않는 설비") && response.contains("시스템"))) {
            return "해당 설비는 시스템에 존재하지 않는 설비입니다.";
        }
        if (response.contains("어떤 분석을 원하시나요")) {
            int start = response.indexOf("[");
            if (start != -1 && start < response.indexOf("어떤 분석을 원하시나요")) {
                return response.substring(start).trim();
            }
            start = response.indexOf("데이터 조회를 요청하셨습니다");
            if (start != -1) {
                return response.substring(start).trim();
            }
        }
        
        return response.trim();
    }
}