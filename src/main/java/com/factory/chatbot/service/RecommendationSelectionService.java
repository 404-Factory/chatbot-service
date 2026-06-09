package com.factory.chatbot.service;
import com.factory.chatbot.dto.RecipeRecommendDto;
import com.factory.chatbot.dto.LlmRecommendationDto;


import com.factory.chatbot.dto.ExpectedEffect;
import com.factory.chatbot.dto.RecipeParameter;
import com.factory.chatbot.dto.RecipeParameterValue;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.Getter;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class RecommendationSelectionService {

    private static final double LLM_CANDIDATE_MIN_CONFIDENCE = 0.7;

    private final LlmCandidateValidationService validationService;

    public RecommendationSelectionService(LlmCandidateValidationService validationService) {
        this.validationService = validationService;
    }

    public SelectionResult select(
            RecipeRecommendDto.Response backendRecommendation,
            LlmRecommendationDto.Recommendation candidate
    ) {
        List<String> violations = validationService.validate(
                backendRecommendation,
                candidate,
                LLM_CANDIDATE_MIN_CONFIDENCE
        );

        if (violations.isEmpty()) {
            return new SelectionResult(
                    adoptCandidate(backendRecommendation, candidate),
                    "LLM_VALIDATED",
                    List.of()
            );
        }

        RecipeRecommendDto.Response annotatedBackend = annotateBackendRecommendation(
                backendRecommendation,
                candidate,
                violations
        );
        return new SelectionResult(annotatedBackend, "BACKEND_ONLY_LLM_REJECTED", violations);
    }

    private RecipeRecommendDto.Response adoptCandidate(
            RecipeRecommendDto.Response backendRecommendation,
            LlmRecommendationDto.Recommendation candidate
    ) {
        List<RecipeParameterValue> adoptedParameters = mergeCandidateParameters(
                backendRecommendation.getRecommendedParameters(),
                candidate.getRecommendedParameters()
        );

        List<String> evidence = new ArrayList<>(safeList(backendRecommendation.getEvidence()));
        evidence.add("LLM candidate passed backend safety validation and was adopted.");
        evidence.addAll(safeList(candidate.getEvidence()));
        appendCandidateReasons(evidence, candidate);

        List<String> warnings = new ArrayList<>(safeList(backendRecommendation.getWarnings()));
        warnings.addAll(safeList(candidate.getWarnings()));

        return copyResponse(
                backendRecommendation,
                "SUCCESS",
                firstText(candidate.getSummary(), backendRecommendation.getSummary()),
                backendRecommendation.getRecommendedRecipe(),
                adoptedParameters,
                backendRecommendation.getExpectedEffect(),
                evidence,
                warnings,
                firstNonNull(candidate.getConfidence(), backendRecommendation.getConfidence())
        );
    }

    private RecipeRecommendDto.Response annotateBackendRecommendation(
            RecipeRecommendDto.Response backendRecommendation,
            LlmRecommendationDto.Recommendation candidate,
            List<String> violations
    ) {
        List<String> evidence = new ArrayList<>(safeList(backendRecommendation.getEvidence()));
        List<String> warnings = new ArrayList<>(safeList(backendRecommendation.getWarnings()));

        if (candidate == null) {
            warnings.add("LLM candidate was not generated, so backend-only recommendation was used.");
        } else {
            warnings.add("LLM candidate was rejected by backend safety validation.");
            warnings.addAll(prefix("LLM rejection reason: ", violations));
        }

        return copyResponse(
                backendRecommendation,
                backendRecommendation.getStatus(),
                backendRecommendation.getSummary(),
                backendRecommendation.getRecommendedRecipe(),
                backendRecommendation.getRecommendedParameters(),
                backendRecommendation.getExpectedEffect(),
                evidence,
                warnings,
                backendRecommendation.getConfidence()
        );
    }

    private List<RecipeParameterValue> mergeCandidateParameters(
            List<RecipeParameterValue> backendParameters,
            List<LlmRecommendationDto.Parameter> candidateParameters
    ) {
        Map<String, LlmRecommendationDto.Parameter> candidateByName = safeList(candidateParameters).stream()
                .filter(parameter -> StringUtils.hasText(parameter.getName()))
                .collect(Collectors.toMap(
                        parameter -> normalize(parameter.getName()),
                        Function.identity(),
                        (first, second) -> first
                ));

        return safeList(backendParameters).stream()
                .map(parameter -> {
                    LlmRecommendationDto.Parameter candidate = candidateByName.get(normalize(parameter.getName()));
                    if (candidate == null) {
                        return parameter;
                    }
                    return RecipeParameterValue.builder()
                            .name(parameter.getName())
                            .min(parameter.getMin())
                            .max(parameter.getMax())
                            .currentValue(parameter.getCurrentValue())
                            .recommendedMin(candidate.getRecommendedMin())
                            .recommendedMax(candidate.getRecommendedMax())
                            .recommendedValue(candidate.getRecommendedValue())
                            .unit(parameter.getUnit())
                            .build();
                })
                .toList();
    }

    private void appendCandidateReasons(List<String> evidence, LlmRecommendationDto.Recommendation candidate) {
        for (LlmRecommendationDto.Parameter parameter : safeList(candidate.getRecommendedParameters())) {
            if (parameter != null && StringUtils.hasText(parameter.getReason())) {
                evidence.add("LLM candidate reason for " + parameter.getName() + ": " + parameter.getReason());
            }
        }
    }

    private RecipeRecommendDto.Response copyResponse(
            RecipeRecommendDto.Response original,
            String status,
            String summary,
            RecipeParameter recommendedRecipe,
            List<RecipeParameterValue> recommendedParameters,
            ExpectedEffect expectedEffect,
            List<String> evidence,
            List<String> warnings,
            Double confidence
    ) {
        return RecipeRecommendDto.Response.builder()
                .status(status)
                .summary(summary)
                .recommendedRecipe(recommendedRecipe)
                .recommendedParameters(recommendedParameters)
                .expectedEffect(expectedEffect)
                .evidence(evidence)
                .warnings(warnings)
                .confidence(confidence)
                .build();
    }

    private List<String> prefix(String prefix, List<String> values) {
        return safeList(values).stream()
                .map(value -> prefix + value)
                .toList();
    }

    private String normalize(String value) {
        return value == null ? "" : value.replaceAll("[^A-Za-z0-9가-힣]", "").toLowerCase();
    }

    private String firstText(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }

    private Double firstNonNull(Double first, Double second) {
        return first != null ? first : second;
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    @Getter
    public static class SelectionResult {

        private final RecipeRecommendDto.Response recommendation;

        private final String source;

        private final List<String> violations;

        public SelectionResult(
                RecipeRecommendDto.Response recommendation,
                String source,
                List<String> violations
        ) {
            this.recommendation = recommendation;
            this.source = source;
            this.violations = violations;
        }
    }
}