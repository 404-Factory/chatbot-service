package com.factory.chatbot_service.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class ChatbotService {

    private final S3Service s3Service;
    private final BedrockRuntimeClient bedrockClient;

    @Value("${chatbot.bedrock.model-id}")
    private String modelId;

    @Value("${chatbot.s3.bucket-name}")
    private String bucketName;

    @Value("${chatbot.bedrock.max-tokens}")
    private int maxTokens;

    @Value("${chatbot.bedrock.temperature}")
    private double temperature;

    @Value("${chatbot.bedrock.top-p}")
    private double topP;

    public ChatbotService(S3Service s3Service, BedrockRuntimeClient bedrockClient) {
        this.s3Service = s3Service;
        this.bedrockClient = bedrockClient;
    }

    /**
     * 유저의 입력에 따라 적절한 S3 인프라 도구를 선택하고 분석하여 결과를 반환하는 에이전트 메소드
     */
    public String askInsightAI(String prompt) {
        String normalizedPrompt = StringUtils.hasText(prompt) ? prompt.trim() : "";
        System.out.println("[Agent] 유저 입력: " + normalizedPrompt);

        try {
            // 1. 대화 컨텍스트 초기화 및 유저 메시지 적재
            List<Message> messages = new ArrayList<>();
            messages.add(Message.builder()
                .role(ConversationRole.USER)
                .content(ContentBlock.fromText(normalizedPrompt))
                .build());

            // 2. 에이전트 툴 벨트 로드
            ToolConfiguration toolConfig = ToolConfiguration.builder()
                .tools(registerAgentTools())
                .build();

            // 3. 1차 호출 (유저 의도 파악 및 도구 사용 결정 단계)
            ConverseRequest firstRequest = ConverseRequest.builder()
                .modelId(modelId)
                .system(SystemContentBlock.fromText(buildSystemPrompt()))
                .messages(messages)
                .toolConfig(toolConfig)
                .inferenceConfig(InferenceConfiguration.builder()
                    .temperature((float) temperature)
                    .maxTokens(maxTokens)
                    .topP((float) topP)
                    .build())
                .build();

            ConverseResponse response = bedrockClient.converse(firstRequest);
            StopReason stopReason = response.stopReason();
            Message assistantMessage = response.output().message();

            // 대화 이력에 에이전트의 중간 판단(Thought) 기록 추가
            messages.add(assistantMessage);

            // 4. 도구 가동 조건 충족 시 로컬 비즈니스 로직(S3) 실행 (Action & Observation)
            if (stopReason == StopReason.TOOL_USE) {
                System.out.println("[Agent] LLM이 S3 인프라 도구 가동을 결정했습니다.");

                for (ContentBlock contentBlock : assistantMessage.content()) {
                    if (contentBlock.type() == ContentBlock.Type.TOOL_USE) {
                        ToolUseBlock toolUse = contentBlock.toolUse();
                        String toolName = toolUse.name();

                        // 파라미터 맵핑 추출
                        java.util.Map<String, Document> arguments = toolUse.input().asMap();
                        String processStage = arguments.get("processStage").asString();

                        System.out.println(" - 실행 도구: " + toolName + " | 대상 공정 세션: " + processStage);

                        // S3 원격 덤프 파일 파싱 실행
                        String s3DataResult = executeLocalTool(toolName, processStage);

                        // 관측된 실제 가동 데이터를 에이전트 컨텍스트 피드백으로 장착
                        messages.add(Message.builder()
                            .role(ConversationRole.USER)
                            .content(ContentBlock.fromToolResult(ToolResultBlock.builder()
                                .toolUseId(toolUse.toolUseId())
                                .status(ToolResultStatus.SUCCESS)
                                .content(ToolResultContentBlock.builder().text(s3DataResult).build())
                                .build()))
                            .build());
                    }
                }

                // 5. 2차 호출 (확보된 인프라 데이터를 기반으로 최종 결론 도출)
                ConverseRequest secondRequest = ConverseRequest.builder()
                    .modelId(modelId)
                    .system(SystemContentBlock.fromText(buildSystemPrompt()))
                    .messages(messages)
                    .toolConfig(toolConfig)
                    .inferenceConfig(InferenceConfiguration.builder()
                        .temperature((float) temperature)
                        .maxTokens(maxTokens)
                        .topP((float) topP)
                        .build())
                    .build();

                ConverseResponse finalResponse = bedrockClient.converse(secondRequest);
                return finalResponse.output().message().content().get(0).text();
            }

            // 도구 조회가 필요 없는 범용 대화인 경우 1차 답변 직접 반환
            return assistantMessage.content().get(0).text();

        } catch (Exception e) {
            e.printStackTrace();
            return "Error executing InsightAI Agent: " + e.getMessage();
        }
    }

    /**
     * 에이전트가 호출한 툴 명칭에 맞춰 실질적인 S3 파일 스트림을 트리거하는 라우터
     */
    private String executeLocalTool(String toolName, String processStage) {
        LocalDate now = LocalDate.now();

        if ("get_process_summary".equals(toolName)) {
            String summaryPrefix = String.format("summary-data/daily/year=%d/month=%d/factoryId=%s/processStage=%s/",
                now.getYear(), now.getMonthValue(), "FAB-SEMICONDUCTOR-002", processStage);
            return s3Service.readParquetAsJson(bucketName, summaryPrefix);
        }

        if ("get_realtime_raw_logs".equals(toolName)) {
            String rawPrefix = String.format("FAB-SEMICONDUCTOR-002/LINE-02/%d/%02d/%02d/",
                now.getYear(), now.getMonthValue(), now.getDayOfMonth());
            return s3Service.readRawJson(bucketName, rawPrefix);
        }

        return "Unknown tool definition";
    }

    /**
     * 에이전트에게 공급할 인프라 도구 리스트 명세 정의
     */
    private List<Tool> registerAgentTools() {
        List<Tool> tools = new ArrayList<>();

        // Tool 1: 장기 통계 및 일일 요약 파싱 툴
        ToolSpecification summaryTool = ToolSpecification.builder()
            .name("get_process_summary")
            .description("스마트 팩토리 공정의 요약(Summary) 통계 데이터를 조회합니다. 일일 평균치, 가동률, 종합 데이터 등 대시보드 외적인 장기 데이터를 분석할 때 사용합니다.")
            .inputSchema(ToolInputSchema.builder()
                .json(Document.mapBuilder()
                    .putString("type", "object")
                    .putDocument("properties", Document.mapBuilder()
                        .putDocument("processStage", Document.mapBuilder()
                            .putString("type", "string")
                            .putString("description", "분석할 공정 단계를 선택합니다. 반드시 다음 중 하나여야 합니다: CLEANING, DEPOSITION, ETCH, PHOTO")
                            .build())
                        .build())
                    .putDocument("required", Document.listBuilder().addString("processStage").build())
                    .build())
                .build())
            .build();

        // Tool 2: 실시간 분/초 단위 Raw 로그 수집 툴
        ToolSpecification rawLogTool = ToolSpecification.builder()
            .name("get_realtime_raw_logs")
            .description("스마트 팩토리 공정의 실시간 Raw 센서 로그 데이터를 직접 다운로드하여 조회합니다. 특정 분/초 단위의 실시간 이상 징후나 장비의 미세 수치 변화를 상세 분석할 때 사용합니다.")
            .inputSchema(ToolInputSchema.builder()
                .json(Document.mapBuilder()
                    .putString("type", "object")
                    .putDocument("properties", Document.mapBuilder()
                        .putDocument("processStage", Document.mapBuilder()
                            .putString("type", "string")
                            .putString("description", "실시간 로그를 추적할 공정 단계: CLEANING, DEPOSITION, ETCH, PHOTO")
                            .build())
                        .build())
                    .putDocument("required", Document.listBuilder().addString("processStage").build())
                    .build())
                .build())
            .build();

        tools.add(Tool.builder().toolSpec(summaryTool).build());
        tools.add(Tool.builder().toolSpec(rawLogTool).build());
        return tools;
    }

    /**
     * 스마트 팩토리 수석 에이전트 'DANAI'의 페르소나 및 행동 규칙 정의
     */
    private String buildSystemPrompt() {
        return "<role>\n"
            + "당신은 SIGMA 스마트 팩토리 플랫폼의 최고 에이전트 'DANAI'입니다.\n"
            + "제공된 전용 도구(Tools)들을 사용하여 S3 인프라에서 수집된 실제 공장 센서 데이터를 수집하고 분석할 수 있습니다.\n"
            + "</role>\n\n"
            + "<rules>\n"
            + "1. 추측성 답변은 절대 금지합니다. 오직 Tool을 통해 반환된 S3 내부의 실제 데이터(Observation)만을 신뢰하고 근거로 삼아 답변하십시오.\n"
            + "2. [필수] 분석 결과 리포트를 작성할 때, 데이터에 존재하는 장비 ID(Equipment ID)와 센서 종류, 그리고 구체적인 측정 수치(평균값, 최소/최대값, 레시피 범위 등)를 절대로 누락하지 말고 숫자로 명시하십시오.\n"
            + "3. '모든 수치가 정상 범위 내에 있습니다'와 같은 추상적이고 포괄적인 문장으로 핵심 데이터를 생략하는 것을 엄격히 금지합니다. 반드시 각 장비별 세부 지표를 쪼개서 보여주세요.\n"
            + "4. 데이터 조회 결과 에러태그나 부재 안내가 나오면, 가공하지 말고 데이터 수집 실패 상태임을 명확히 안내하세요.\n"
            + "5. 공장 분석과 무관한 일상 질문은 답변을 거절하십시오.\n"
            + "</rules>\n\n"
            + "<output_format>\n"
            + "- 답변 시작 시 데이터 분석에 기반하여 현재 공장 상태를 [정상], [주의], [위험] 중 하나로 명시할 것.\n"
            + "- 항목별 마크다운(bullet points)을 적용하되, 각 항목에는 반드시 구체적인 장비 코드와 해당 장비의 핵심 통계 수치(숫자)가 포함되어야 함.\n"
            + "</output_format>";
    }
}