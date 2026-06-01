package com.factory.chatbot_service.service;

import com.factory.chatbot_service.dto.LlmRecommendedParameter;
import com.factory.chatbot_service.dto.LlmRecipeRecommendation;

import com.factory.chatbot_service.dto.ExpectedEffect;
import com.factory.chatbot_service.dto.RecipeParameter;
import com.factory.chatbot_service.dto.RecipeParameterValue;
import com.factory.chatbot_service.dto.RecipeRecommendResponse;
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
            RecipeRecommendResponse backendRecommendation,
            LlmRecipeRecommendation candidate
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

        RecipeRecommendResponse annotatedBackend = annotateBackendRecommendation(
                backendRecommendation,
                candidate,
                violations
        );
        return new SelectionResult(annotatedBackend, "BACKEND_ONLY_LLM_REJECTED", violations);
    }

    private RecipeRecommendResponse adoptCandidate(
            RecipeRecommendResponse backendRecommendation,
            LlmRecipeRecommendation candidate
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

    private RecipeRecommendResponse annotateBackendRecommendation(
            RecipeRecommendResponse backendRecommendation,
            LlmRecipeRecommendation candidate,
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
            List<LlmRecommendedParameter> candidateParameters
    ) {
        Map<String, LlmRecommendedParameter> candidateByName = safeList(candidateParameters).stream()
                .filter(parameter -> StringUtils.hasText(parameter.getName()))
                .collect(Collectors.toMap(
                        parameter -> normalize(parameter.getName()),
                        Function.identity(),
                        (first, second) -> first
                ));

        return safeList(backendParameters).stream()
                .map(parameter -> {
                    LlmRecommendedParameter candidate = candidateByName.get(normalize(parameter.getName()));
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

    private void appendCandidateReasons(List<String> evidence, LlmRecipeRecommendation candidate) {
        for (LlmRecommendedParameter parameter : safeList(candidate.getRecommendedParameters())) {
            if (parameter != null && StringUtils.hasText(parameter.getReason())) {
                evidence.add("LLM candidate reason for " + parameter.getName() + ": " + parameter.getReason());
            }
        }
    }

    private RecipeRecommendResponse copyResponse(
            RecipeRecommendResponse original,
            String status,
            String summary,
            RecipeParameter recommendedRecipe,
            List<RecipeParameterValue> recommendedParameters,
            ExpectedEffect expectedEffect,
            List<String> evidence,
            List<String> warnings,
            Double confidence
    ) {
        return RecipeRecommendResponse.builder()
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

        private final RecipeRecommendResponse recommendation;

        private final String source;

        private final List<String> violations;

        public SelectionResult(
                RecipeRecommendResponse recommendation,
                String source,
                List<String> violations
        ) {
            this.recommendation = recommendation;
            this.source = source;
            this.violations = violations;
        }
    }
}