package com.factory.chatbot.service;
import com.factory.chatbot.dto.RecipeRecommendDto;
import com.factory.chatbot.dto.LlmRecommendationDto;
import com.factory.chatbot.dto.ChatDto;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.factory.chatbot.service.BedrockRecipeAnswerService;
import com.factory.chatbot.service.BedrockRecipeCandidateService;
import com.factory.chatbot.dto.ExpectedEffect;
import com.factory.chatbot.dto.RecipeParameterValue;
import com.factory.chatbot.service.RecommendationSelectionService;
import com.factory.chatbot.service.RecipeRecommendationService;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class RecipeChatServiceImpl implements RecipeChatService {

    private static final Logger log = LoggerFactory.getLogger(RecipeChatService.class);

    private static final Pattern JSON_OBJECT_PATTERN = Pattern.compile("\\{.*}", Pattern.DOTALL);
    private static final Pattern EQUIPMENT_ID_PATTERN = Pattern.compile(
            "(?i)(?:equipment\\s*id|equipmentId|eqp|설비\\s*(?:id)?|장비\\s*(?:id)?)[^0-9]{0,10}(\\d+)|\\b(\\d{1,3})\\s*번\\s*(?:설비|장비)"
    );
    private static final Pattern DEFECT_TYPE_AFTER_PATTERN = Pattern.compile(
            "(?i)(?:defect\\s*type|defectType|불량\\s*(?:유형|타입))[\\s:=：-]*([A-Za-z0-9_가-힣-]+)"
    );
    private static final Pattern DEFECT_TYPE_BEFORE_PATTERN = Pattern.compile(
            "\\b([A-Za-z][A-Za-z0-9_-]*)\\s*불량"
    );

    private static final Pattern RECIPE_RECOMMENDATION_INTENT_PATTERN = Pattern.compile(
            "(?i)(추천|레시피|파라미터|불량|줄|개선|조정|최적|recommend|recipe|parameter|defect|reduce|improve|optimi[sz]e|adjust)"
    );

    private final RecipeRecommendationService recipeRecommendationService;
    private final BedrockRecipeAnswerService bedrockRecipeAnswerService;
    private final BedrockRecipeCandidateService bedrockRecipeCandidateService;
    private final RecommendationSelectionService recommendationSelectionService;
    private final ObjectMapper objectMapper;

    public RecipeChatServiceImpl(
            RecipeRecommendationService recipeRecommendationService,
            BedrockRecipeAnswerService bedrockRecipeAnswerService,
            BedrockRecipeCandidateService bedrockRecipeCandidateService,
            RecommendationSelectionService recommendationSelectionService,
            ObjectMapper objectMapper
    ) {
        this.recipeRecommendationService = recipeRecommendationService;
        this.bedrockRecipeAnswerService = bedrockRecipeAnswerService;
        this.bedrockRecipeCandidateService = bedrockRecipeCandidateService;
        this.recommendationSelectionService = recommendationSelectionService;
        this.objectMapper = objectMapper;
    }

    @Override
    public ChatDto.Response chat(ChatDto.Request request) {
        if (request == null || !StringUtils.hasText(request.getMessage())) {
            return ChatDto.Response.builder()
                    .status("BAD_REQUEST")
                    .answer("질문 내용을 입력해 주세요.")
                    .sessionId(null)
                    .rawResponse(null)
                    .build();
        }

        String sessionId = resolveSessionId(request.getSessionId());
        RecipeRecommendDto.Request recommendRequest = toRecommendRequest(request.getMessage());
        if (!StringUtils.hasText(recommendRequest.getEquipmentId())) {
            return ChatDto.Response.builder()
                    .status("INSUFFICIENT_CONTEXT")
                    .answer("추천할 설비 ID를 찾지 못했습니다. 예: \"설비 1번의 PATTERN 불량을 줄일 레시피 파라미터를 추천해줘\"")
                    .sessionId(sessionId)
                    .rawResponse(null)
                    .build();
        }

        // 백엔드 추천 전에 자연어 요청이 레시피 추천 의도인지 먼저 확인한다.
        if (!isRecipeRecommendationIntent(request.getMessage(), recommendRequest)) {
            return ChatDto.Response.builder()
                    .status("UNSUPPORTED_INTENT")
                    .answer("레시피 추천을 안전하게 수행하려면 설비 ID와 함께 불량 유형, 개선 목표, 또는 추천 요청이 필요합니다. 예: \"설비 1번의 PATTERN 불량을 줄일 레시피 파라미터를 추천해줘\"")
                    .sessionId(sessionId)
                    .rawResponse(null)
                    .build();
        }

        // 백엔드 추천을 기준선으로 만들고, LLM 후보를 별도로 생성/검증한다.
        RecipeRecommendDto.Response backendRecommendation = recipeRecommendationService.recommendLocally(recommendRequest);

        boolean hasAnomaly = false;
        if (backendRecommendation != null && "SUCCESS".equals(backendRecommendation.getStatus())) {
            List<RecipeParameterValue> params = backendRecommendation.getRecommendedParameters();
            if (params != null && !params.isEmpty()) {
                for (RecipeParameterValue p : params) {
                    if (p.getCurrentValue() != null && p.getMin() != null && p.getMax() != null) {
                        if (p.getCurrentValue() < p.getMin() || p.getCurrentValue() > p.getMax()) {
                            hasAnomaly = true;
                            break;
                        }
                    }
                }
            } else if (backendRecommendation.getRecommendedRecipe() != null) {
                hasAnomaly = true;
            }
        }

        if (!hasAnomaly && backendRecommendation != null && "SUCCESS".equals(backendRecommendation.getStatus())) {
            String normalAnswer = "현재 분석 대상 설비의 모든 센서 가동 측정값(평균치)이 표준 사양 범위 내에서 **정상 가동 중**으로 감지되었습니다.\n"
                    + "따라서 추가적인 레시피 변동이나 보정치 조치(Adjust)는 불필요한 상태입니다.\n\n"
                    + "만약 설비의 가동 상태 트렌드 조회, RDBMS 이상 로그 분석, 또는 장기 이상 추이 분석을 추가로 희망하시는 경우 **[Insight AI로 상세 분석하기]** 기능으로 이동하여 편리하게 대화해 보세요.";
            
            return ChatDto.Response.builder()
                    .status("NORMAL_STATUS")
                    .answer(normalAnswer)
                    .sessionId(sessionId)
                    .rawResponse(toJson(backendRecommendation))
                    .build();
        }

        LlmRecommendationDto.Recommendation candidate = generateLlmCandidateIfEnabled(recommendRequest, backendRecommendation);
        RecommendationSelectionService.SelectionResult selectionResult =
                recommendationSelectionService.select(backendRecommendation, candidate);
        RecipeRecommendDto.Response recommendation = selectionResult.getRecommendation();
        log.info("Recipe recommendation selected: source={}, violations={}",
                selectionResult.getSource(), selectionResult.getViolations());

        String rawResponse = toJson(recommendation);
        String modelAnswer = bedrockRecipeAnswerService.explain(recommendRequest, recommendation);
        String answer = StringUtils.hasText(modelAnswer)
                ? modelAnswer
                : toFallbackAnswer(recommendation);

        return ChatDto.Response.builder()
                .status(recommendation.getStatus())
                .answer(answer)
                .sessionId(sessionId)
                .rawResponse(rawResponse)
                .build();
    }

    private LlmRecommendationDto.Recommendation generateLlmCandidateIfEnabled(
            RecipeRecommendDto.Request recommendRequest,
            RecipeRecommendDto.Response backendRecommendation
    ) {
        if (backendRecommendation == null
                || !"SUCCESS".equals(backendRecommendation.getStatus())) {
            return null;
        }

        long startedAt = System.currentTimeMillis();
        LlmRecommendationDto.Recommendation candidate = bedrockRecipeCandidateService.recommendCandidate(
                recommendRequest,
                backendRecommendation
        );
        log.info("LLM recipe candidate generation completed: generated={}, elapsedMs={}",
                candidate != null, System.currentTimeMillis() - startedAt);
        return candidate;
    }

    private String resolveSessionId(String requestedSessionId) {
        if (StringUtils.hasText(requestedSessionId)) {
            return requestedSessionId;
        }
        return "chat-" + UUID.randomUUID();
    }

    private String resolveEquipmentIdFromMessage(String message) {
        if (!StringUtils.hasText(message)) {
            return null;
        }
        String upper = message.toUpperCase();
        
        // Direct EQP mappings
        if (upper.contains("EQP-DEPOSITION-001") || upper.contains("DEPOSITION-001")) return "1";
        if (upper.contains("EQP-DEPOSITION-002") || upper.contains("DEPOSITION-002")) return "2";
        if (upper.contains("EQP-PHOTO-001") || upper.contains("PHOTO-001")) return "3";
        if (upper.contains("EQP-PHOTO-002") || upper.contains("PHOTO-002")) return "4";
        if (upper.contains("EQP-PHOTO-003") || upper.contains("PHOTO-003") || upper.contains("SCN-03")) return "5";
        if (upper.contains("EQP-PHOTO-004") || upper.contains("PHOTO-004")) return "6";
        if (upper.contains("EQP-ETCH-001") || upper.contains("ETCH-001")) return "7";
        if (upper.contains("EQP-ETCH-002") || upper.contains("ETCH-002")) return "8";
        if (upper.contains("EQP-CLEANING-001") || upper.contains("CLEANING-001")) return "9";
        if (upper.contains("EQP-CLEANING-002") || upper.contains("CLEANING-002")) return "10";

        // Regex pattern search
        String matchedId = extractFirstMatch(message, EQUIPMENT_ID_PATTERN);
        if (StringUtils.hasText(matchedId)) {
            try {
                int val = Integer.parseInt(matchedId);
                return String.valueOf(val);
            } catch (NumberFormatException e) {
                return matchedId;
            }
        }

        // Relative number detection based on stage context
        Pattern numPattern = Pattern.compile("(\\d+)");
        Matcher numMatcher = numPattern.matcher(message);
        if (numMatcher.find()) {
            int relativeNum = Integer.parseInt(numMatcher.group(1));
            if (upper.contains("포토") || upper.contains("PHOTO")) {
                if (relativeNum == 1) return "3";
                if (relativeNum == 2) return "4";
                if (relativeNum == 3) return "5";
                if (relativeNum == 4) return "6";
            }
            if (upper.contains("데포") || upper.contains("증착") || upper.contains("DEPOSITION") || upper.contains("도포")) {
                if (relativeNum == 1) return "1";
                if (relativeNum == 2) return "2";
            }
            if (upper.contains("식각") || upper.contains("에칭") || upper.contains("ETCH")) {
                if (relativeNum == 1) return "7";
                if (relativeNum == 2) return "8";
            }
            if (upper.contains("세정") || upper.contains("CLEANING") || upper.contains("클리어")) {
                if (relativeNum == 1) return "9";
                if (relativeNum == 2) return "10";
            }
        }

        return null;
    }

    private RecipeRecommendDto.Request toRecommendRequest(String message) {
        // Postman 테스트용 JSON과 자연어 질문을 모두 받을 수 있게 처리한다.
        RecipeRecommendDto.Request parsedJson = parseJsonRequest(message);
        if (parsedJson != null) {
            if (!StringUtils.hasText(parsedJson.getOperatorQuestion())) {
                parsedJson.setOperatorQuestion(message);
            }
            return parsedJson;
        }

        RecipeRecommendDto.Request recommendRequest = new RecipeRecommendDto.Request();
        recommendRequest.setOperatorQuestion(message);
        recommendRequest.setEquipmentId(resolveEquipmentIdFromMessage(message));
        
        String extractedDefect = firstText(
                extractFirstMatch(message, DEFECT_TYPE_AFTER_PATTERN),
                extractFirstMatch(message, DEFECT_TYPE_BEFORE_PATTERN)
        );

        if (!StringUtils.hasText(extractedDefect) && StringUtils.hasText(message)) {
            String lower = message.toLowerCase();
            if (lower.contains("두께") || lower.contains("thickness")) {
                extractedDefect = "Thickness";
            } else if (lower.contains("패턴") || lower.contains("pattern")) {
                extractedDefect = "PATTERN";
            } else if (lower.contains("스크래치") || lower.contains("scratch")) {
                extractedDefect = "Scratch";
            } else if (lower.contains("파티클") || lower.contains("particle")) {
                extractedDefect = "Particle";
            } else if (lower.contains("잔사") || lower.contains("residue")) {
                extractedDefect = "Residue";
            } else if (lower.contains("기포") || lower.contains("bubble")) {
                extractedDefect = "Bubble";
            } else if (lower.contains("오버레이") || lower.contains("overlay")) {
                extractedDefect = "Overlay";
            } else if (lower.contains("cd")) {
                extractedDefect = "CD";
            }
        }

        recommendRequest.setDefectType(extractedDefect);
        return recommendRequest;
    }

    private boolean isRecipeRecommendationIntent(String message, RecipeRecommendDto.Request recommendRequest) {
        if (recommendRequest != null && StringUtils.hasText(recommendRequest.getDefectType())) {
            return true;
        }
        if (recommendRequest != null
                && StringUtils.hasText(recommendRequest.getOperatorQuestion())
                && RECIPE_RECOMMENDATION_INTENT_PATTERN.matcher(recommendRequest.getOperatorQuestion()).find()) {
            return true;
        }
        return StringUtils.hasText(message)
                && RECIPE_RECOMMENDATION_INTENT_PATTERN.matcher(message).find();
    }

    private RecipeRecommendDto.Request parseJsonRequest(String message) {
        Matcher matcher = JSON_OBJECT_PATTERN.matcher(message);
        if (!matcher.find()) {
            return null;
        }

        try {
            return objectMapper.readValue(matcher.group(), RecipeRecommendDto.Request.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private String extractFirstMatch(String message, Pattern pattern) {
        Matcher matcher = pattern.matcher(message);
        if (!matcher.find()) {
            return null;
        }

        for (int index = 1; index <= matcher.groupCount(); index++) {
            String value = matcher.group(index);
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String firstText(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }

    private String toJson(RecipeRecommendDto.Response recommendation) {
        try {
            return objectMapper.writeValueAsString(recommendation);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize recommendation response.", e);
        }
    }

    private String toFallbackAnswer(RecipeRecommendDto.Response response) {
        if (response == null) {
            return "추천 결과가 비어 있습니다.";
        }
        if (!"SUCCESS".equals(response.getStatus())) {
            return buildFailureAnswer(response);
        }

        StringBuilder answer = new StringBuilder();
        answer.append("레시피 파라미터 추천이 완료되었습니다.");
        if (StringUtils.hasText(response.getSummary())) {
            answer.append(System.lineSeparator())
                    .append("요약: ")
                    .append(response.getSummary());
        }

        List<RecipeParameterValue> parameters = response.getRecommendedParameters();
        if (parameters != null && !parameters.isEmpty()) {
            answer.append(System.lineSeparator())
                    .append(System.lineSeparator())
                    .append("권장 센서 측정 범위:");
            for (RecipeParameterValue parameter : parameters) {
                answer.append(System.lineSeparator())
                        .append("- ")
                        .append(parameter.getName())
                        .append(": 기준 ")
                        .append(format(parameter.getMin()))
                        .append(" ~ ")
                        .append(format(parameter.getMax()))
                        .append(" -> 권장 ")
                        .append(format(parameter.getRecommendedMin()))
                        .append(" ~ ")
                        .append(format(parameter.getRecommendedMax()))
                        .append(" / 중심값 ")
                        .append(format(parameter.getRecommendedValue()));
                if (StringUtils.hasText(parameter.getUnit())) {
                    answer.append(" ").append(parameter.getUnit());
                }
            }
        }

        ExpectedEffect expectedEffect = response.getExpectedEffect();
        if (expectedEffect != null && StringUtils.hasText(expectedEffect.getDescription())) {
            answer.append(System.lineSeparator())
                    .append(System.lineSeparator())
                    .append("기대 효과: ")
                    .append(expectedEffect.getDescription());
        }

        appendSection(answer, "주요 근거", response.getEvidence(), 5);
        appendSection(answer, "유의점", response.getWarnings(), 5);
        if (response.getConfidence() != null) {
            answer.append(System.lineSeparator())
                    .append(System.lineSeparator())
                    .append("신뢰도: ")
                    .append(Math.round(response.getConfidence() * 100))
                    .append("%");
        }
        answer.append(System.lineSeparator())
                .append("이 추천은 자동 제어 명령이 아니라 작업자 검토용 제안입니다.");
        return answer.toString();
    }

    private String buildFailureAnswer(RecipeRecommendDto.Response response) {
        StringBuilder answer = new StringBuilder();
        answer.append("레시피 추천을 완료하지 못했습니다. 상태: ")
                .append(response.getStatus());
        if (StringUtils.hasText(response.getSummary())) {
            answer.append(System.lineSeparator())
                    .append("사유: ")
                    .append(response.getSummary());
        }
        appendSection(answer, "확인 사항", response.getWarnings(), 5);
        return answer.toString();
    }

    private void appendSection(StringBuilder answer, String title, List<String> values, int limit) {
        if (values == null || values.isEmpty()) {
            return;
        }

        answer.append(System.lineSeparator())
                .append(System.lineSeparator())
                .append(title)
                .append(":");

        int count = 0;
        for (String value : values) {
            if (!StringUtils.hasText(value)) {
                continue;
            }
            if (count >= limit) {
                break;
            }
            answer.append(System.lineSeparator())
                    .append("- ")
                    .append(value);
            count++;
        }
    }

    private String format(Double value) {
        if (value == null) {
            return "-";
        }
        if (value == Math.rint(value)) {
            return String.valueOf(value.longValue());
        }
        return String.valueOf(value);
    }
}