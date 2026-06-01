package com.factory.chatbot_service.service;
import com.factory.chatbot_service.dto.RecipeRecommendDto;
import com.factory.chatbot_service.dto.LlmRecommendationDto;


import com.factory.chatbot_service.dto.RecipeParameterValue;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class LlmCandidateValidationService {

    public List<String> validate(
            RecipeRecommendDto.Response backendRecommendation,
            LlmRecommendationDto.Recommendation candidate,
            double minConfidence
    ) {
        List<String> violations = new ArrayList<>();
        if (candidate == null) {
            violations.add("LLM candidate was not generated.");
            return violations;
        }
        if (!"SUCCESS".equals(candidate.getStatus())) {
            violations.add("LLM candidate status was not SUCCESS.");
        }
        if (candidate.getConfidence() == null || candidate.getConfidence() < minConfidence) {
            violations.add("LLM candidate confidence was lower than " + minConfidence + ".");
        }

        List<RecipeParameterValue> backendParameters = backendRecommendation == null
                ? List.of()
                : safeList(backendRecommendation.getRecommendedParameters());
        if (backendParameters.isEmpty()) {
            violations.add("Backend recommendation has no parameter ranges to validate against.");
            return violations;
        }

        Map<String, RecipeParameterValue> baseByName = backendParameters.stream()
                .filter(parameter -> StringUtils.hasText(parameter.getName()))
                .collect(Collectors.toMap(
                        parameter -> normalize(parameter.getName()),
                        Function.identity(),
                        (first, second) -> first
                ));

        List<LlmRecommendationDto.Parameter> candidateParameters = safeList(candidate.getRecommendedParameters());
        if (candidateParameters.isEmpty()) {
            violations.add("LLM candidate has no recommendedParameters.");
            return violations;
        }

        for (LlmRecommendationDto.Parameter candidateParameter : candidateParameters) {
            validateParameter(candidateParameter, baseByName, violations);
        }

        return violations;
    }

    private void validateParameter(
            LlmRecommendationDto.Parameter candidateParameter,
            Map<String, RecipeParameterValue> baseByName,
            List<String> violations
    ) {
        if (candidateParameter == null || !StringUtils.hasText(candidateParameter.getName())) {
            violations.add("LLM candidate contained a parameter without a name.");
            return;
        }

        RecipeParameterValue base = baseByName.get(normalize(candidateParameter.getName()));
        if (base == null) {
            violations.add("LLM candidate referenced unknown parameter: " + candidateParameter.getName());
            return;
        }

        Double min = base.getMin();
        Double max = base.getMax();
        Double recommendedMin = candidateParameter.getRecommendedMin();
        Double recommendedMax = candidateParameter.getRecommendedMax();
        Double recommendedValue = candidateParameter.getRecommendedValue();

        if (min == null || max == null) {
            violations.add("Backend safety range was missing for parameter: " + base.getName());
            return;
        }
        if (recommendedMin == null || recommendedMax == null || recommendedValue == null) {
            violations.add("LLM candidate omitted recommended values for parameter: " + base.getName());
            return;
        }

        double lower = Math.min(min, max);
        double upper = Math.max(min, max);
        if (recommendedMin < lower || recommendedMin > upper) {
            violations.add(base.getName() + " recommendedMin exceeded backend safety range.");
        }
        if (recommendedMax < lower || recommendedMax > upper) {
            violations.add(base.getName() + " recommendedMax exceeded backend safety range.");
        }
        if (recommendedMin > recommendedMax) {
            violations.add(base.getName() + " recommendedMin was greater than recommendedMax.");
        }
        if (recommendedValue < recommendedMin || recommendedValue > recommendedMax) {
            violations.add(base.getName() + " recommendedValue was outside the recommended range.");
        }
        if (!isStepAllowed(base, recommendedMin, recommendedMax)) {
            violations.add(base.getName() + " candidate changed the range too aggressively.");
        }
    }

    private boolean isStepAllowed(RecipeParameterValue base, double recommendedMin, double recommendedMax) {
        double lower = Math.min(base.getMin(), base.getMax());
        double upper = Math.max(base.getMin(), base.getMax());
        double range = upper - lower;
        if (range <= 0.0) {
            return true;
        }

        double maxStep = Math.max(range * 0.35, 0.1);
        return Math.abs(recommendedMin - lower) <= maxStep
                && Math.abs(recommendedMax - upper) <= maxStep;
    }

    private String normalize(String value) {
        return value == null ? "" : value.replaceAll("[^A-Za-z0-9가-힣]", "").toLowerCase();
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }
}