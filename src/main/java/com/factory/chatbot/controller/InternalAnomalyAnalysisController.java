package com.factory.chatbot.controller;

import com.factory.chatbot.dto.AnomalyAnalysisDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;

@RestController
@RequestMapping("/api/internal/anomaly-analysis")
public class InternalAnomalyAnalysisController {

    private final BedrockRuntimeClient bedrockRuntimeClient;
    private final ObjectMapper objectMapper;

    @Value("${chatbot.bedrock.model-id:anthropic.claude-3-haiku-20240307-v1:0}")
    private String modelId;

    public InternalAnomalyAnalysisController(
            BedrockRuntimeClient bedrockRuntimeClient,
            ObjectMapper objectMapper
    ) {
        this.bedrockRuntimeClient = bedrockRuntimeClient;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public AnomalyAnalysisDto.Response analyze(@RequestBody AnomalyAnalysisDto.Request request) {
        System.out.println("[DEBUG] InternalAnomalyAnalysisController.analyze called");
        try {
            String prompt = buildPrompt(request);
            System.out.println("[DEBUG] Generated prompt: " + prompt);

            Map<String, Object> body = Map.of(
                    "anthropic_version", "bedrock-2023-05-31",
                    "max_tokens", 2000,
                    "temperature", 0.2,
                    "messages", List.of(Map.of(
                            "role", "user",
                            "content", List.of(Map.of("type", "text", "text", prompt))
                    ))
            );

            String requestBodyJson = objectMapper.writeValueAsString(body);

            InvokeModelRequest invokeModelRequest = InvokeModelRequest.builder()
                    .modelId(modelId)
                    .contentType("application/json")
                    .accept("application/json")
                    .body(SdkBytes.fromUtf8String(requestBodyJson))
                    .build();

            System.out.println("[DEBUG] Invoking Bedrock model: " + modelId);
            String responseBody = bedrockRuntimeClient.invokeModel(invokeModelRequest)
                    .body()
                    .asUtf8String();

            String analysisResult = extractText(responseBody);
            System.out.println("[DEBUG] Extracted analysis: " + analysisResult);

            if (analysisResult == null || analysisResult.isBlank()) {
                analysisResult = "AI 분석 리포트를 생성할 수 없습니다. (응답 비어있음)";
            }

            return new AnomalyAnalysisDto.Response(analysisResult);

        } catch (Exception e) {
            e.printStackTrace();
            return new AnomalyAnalysisDto.Response("AI 분석 리포트 생성 중 예외가 발생했습니다: " + e.getMessage());
        }
    }

    private String buildPrompt(AnomalyAnalysisDto.Request request) {
        StringBuilder sb = new StringBuilder();
        sb.append("너는 반도체 제조 공정의 설비 이상 감지 로그와 불량 데이터를 비교 분석하여, 이상 징후의 원인과 영향을 규명하는 '이상분석 AI' 서비스이다.\n\n");
        
        sb.append("[센서-불량 규칙 매핑 (RAG 사전 지식)]\n");
        sb.append("아래 규칙은 각 센서 파라미터 및 탐지된 Nelson Rule에 따라 발생할 수 있는 주요 불량 유형과 그 확률적 가중치 정보이다. 분석 시 이 지식을 반드시 참조하라:\n");
        sb.append("1. Spin Speed + NELSON_RULE_1 -> PR_THICKNESS (80%), EDGE_BEAD_FAIL (20%)\n");
        sb.append("2. Spin Speed + BIAS_RATIO_RULE -> PR_THICKNESS (70%), THICKNESS_UNEVEN (30%)\n");
        sb.append("3. Spin Speed + NELSON_RULE_3 -> THICKNESS_UNEVEN (60%), PR_THICKNESS (40%)\n");
        sb.append("4. Soft Bake Temperature + NELSON_RULE_1 -> PINHOLE_FAIL (100%)\n");
        sb.append("5. Soft Bake Temperature + BIAS_RATIO_RULE -> RESIDUE_FAIL (100%)\n");
        sb.append("6. Soft Bake Temperature + NELSON_RULE_3 -> RESIDUE_FAIL (70%), PINHOLE_FAIL (30%)\n");
        sb.append("7. PEB + NELSON_RULE_1 -> CD_FAIL (100%)\n");
        sb.append("8. PEB + BIAS_RATIO_RULE -> CD_FAIL (70%), PATTERN_OPEN (30%)\n");
        sb.append("9. PEB + NELSON_RULE_3 -> CD_FAIL (100%)\n");
        sb.append("10. Exposure Dose + NELSON_RULE_1 -> CD_FAIL (100%)\n");
        sb.append("11. Exposure Dose + BIAS_RATIO_RULE -> PATTERN_OPEN (50%), BRIDGE (50%)\n");
        sb.append("12. Exposure Dose + NELSON_RULE_3 -> CD_FAIL (60%), PATTERN_OPEN (20%), BRIDGE (20%)\n");
        sb.append("13. Chamber Pressure + 모든 룰(NELSON_RULE_1, BIAS_RATIO_RULE, NELSON_RULE_3 등) -> ETCH_PROFILE_DEFECT (100%)\n");
        sb.append("14. Chuck Temperature + 모든 룰 -> THICKNESS_NON_UNIFORM (100%)\n");
        sb.append("15. Chemical Temperature + NELSON_RULE_1 또는 NELSON_RULE_3 -> RESIDUE (100%)\n");
        sb.append("16. Chemical Temperature + BIAS_RATIO_RULE -> RESIDUE (60%), BOTTOM_LAYER_DAMAGE (40%)\n");
        sb.append("17. Chemical Concentration + NELSON_RULE_1 -> RESIDUE (50%), BOTTOM_LAYER_DAMAGE (50%)\n");
        sb.append("18. Chemical Concentration + BIAS_RATIO_RULE -> RESIDUE (40%), BOTTOM_LAYER_DAMAGE (60%)\n");
        sb.append("19. Chemical Concentration + NELSON_RULE_3 -> RESIDUE (20%), BOTTOM_LAYER_DAMAGE (80%)\n\n");
        
        sb.append("[감지된 이상 로그 정보]\n");
        sb.append("- 설비명: ").append(request.getEquipmentName() != null ? request.getEquipmentName() : "N/A").append("\n");
        sb.append("- 이상 센서 파라미터: ").append(request.getRecipeParameter() != null ? request.getRecipeParameter() : "N/A").append("\n");
        sb.append("- 적용된 룰: ").append(request.getRuleName() != null ? request.getRuleName() : "N/A").append("\n");
        sb.append("- 이상 유형: ").append(request.getAnomalyType() != null ? request.getAnomalyType() : "N/A").append("\n");
        sb.append("- 상세 탐지 근거: ").append(request.getDetectionReason() != null ? request.getDetectionReason() : "N/A").append("\n");
        sb.append("- 발생 시각: ").append(request.getOccurredTime() != null ? request.getOccurredTime().toString() : "N/A").append("\n\n");
        
        sb.append("[연관 불량 현황]\n");
        if (request.getDefects() == null || request.getDefects().isEmpty()) {
            sb.append("-> 현재 이상 발생 시각 이후 30분 이내에 감지된 불량 내역이 존재하지 않습니다.\n\n");
            sb.append("[작성 지침 - 연관 불량이 없는 경우]\n");
            sb.append("이상 발생 시각 주변에 등록된 불량 데이터가 없으므로, 심각한 품질 결함으로 즉시 이어지지는 않은 단순 일시적 이상이거나, 센서의 일시적 노이즈/오동작일 가능성이 있습니다. 설비의 물리적인 상태 점검 및 해당 센서 파라미터의 기준값(Calibration) 조정을 엔지니어에게 권고하는 가벼운 내용의 점검 가이드를 한국어로 작성해주세요. 레시피 추천이 필요할 수 있음을 언급해도 좋습니다.\n");
        } else {
            sb.append("-> 이상 발생 시각 이후 30분 이내에 아래 불량 건이 감지되었습니다:\n");
            for (var d : request.getDefects()) {
                sb.append(String.format("  * Lot ID: %s | 불량 유형: %s | 불량 코드: %s | 불량 발생시각: %s | 검출시각: %s\n",
                    d.getLotId(), d.getDefectType(), d.getDefectCode(), d.getOccurredTime(), d.getDetectedTime()));
            }
            sb.append("\n[작성 지침 - 연관 불량이 존재하는 경우]\n");
            sb.append("이상 감지 이후 실제로 연관된 불량이 발생한 상황입니다. 위의 [센서-불량 규칙 매핑] 정보와 비교하여 실제로 감지된 불량 유형(예: PR_THICKNESS, CD_FAIL 등)이 현재 이상 발생 센서(예: Spin Speed, PEB 등) 및 룰과 인과 관계가 있는지 규명하십시오.\n");
            sb.append("엔지니어에게 다음과 같은 흐름으로 Insight를 한국어로 명확히 보고서 형태로 제시해주십시오:\n");
            sb.append("1. 이상 발생 센서와 연관되어 발생한 실제 불량 목록 및 연관도 분석 결과.\n");
            sb.append("2. 이상 로그와 실제 연관이 있는 불량들을 명확히 언급.\n");
            sb.append("3. 인과 관계 설명 (예: 'Spin Speed 이상으로 인해 감광액 도포 두께인 PR_THICKNESS 불량이 발생했을 가능성이 매우 높습니다').\n");
            sb.append("4. 엔지니어가 취해야 할 후속 조치 권고 (예: 레시피 파라미터 조정 등).\n");
        }
        
        sb.append("\n[출력 형식 제한]\n");
        sb.append("- 한국어로 명확하고 전문성 있는 엔지니어링 리포트 형식으로 작성해라.\n");
        sb.append("- 출력 결과에 '반도체 제조 공정 이상 분석 리포트', '설비명', '이상 센서 파라미터', '적용된 룰', '이상 유형', '상세 탐지 근거', '발생 시각', '분석 결과:' 등 메타데이터 헤더나 요약 목록을 절대 생성하지 마십시오. 이 정보들은 상세 화면에서 이미 보여주고 있으므로 중복 출력되면 안 됩니다. 오직 실제 상황 분석과 행동 권고 사항의 본문 내용만 반환하십시오.\n");
        sb.append("- 인사말, '네, 알겠습니다' 같은 AI 서론/결론 멘트, 마크다운 코드 블록 표시(```markdown ...)는 절대 포함하지 말고 리포트 본문 텍스트(Markdown 형식 자체는 허용)만 반환하라.");
        
        return sb.toString();
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
                if (text.length() > 0) {
                    text.append(System.lineSeparator());
                }
                text.append(item.path("text").asText());
            }
        }
        return StringUtils.hasText(text.toString()) ? text.toString().trim() : null;
    }
}
