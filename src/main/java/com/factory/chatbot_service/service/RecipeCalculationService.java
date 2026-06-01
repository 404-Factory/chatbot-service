package com.factory.chatbot_service.service;

import com.factory.chatbot_service.dto.RecipeParameter;
import com.factory.chatbot_service.dto.SimilarRecipeCase;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class RecipeCalculationService {

    public RecipeParameter calculateRecommendedRecipe(List<SimilarRecipeCase> similarCases) {
        double avgTemperature = similarCases.stream()
                .mapToDouble(h -> h.getRecipe().getTemperature())
                .average()
                .orElseThrow();

        double avgPressure = similarCases.stream()
                .mapToDouble(h -> h.getRecipe().getPressure())
                .average()
                .orElseThrow();

        double avgSpeed = similarCases.stream()
                .mapToDouble(h -> h.getRecipe().getSpeed())
                .average()
                .orElseThrow();

        double avgDuration = similarCases.stream()
                .mapToDouble(h -> h.getRecipe().getDuration())
                .average()
                .orElseThrow();

        return new RecipeParameter(
                round(avgTemperature),
                round(avgPressure),
                round(avgSpeed),
                round(avgDuration)
        );
    }

    public double calculateConfidence(List<SimilarRecipeCase> similarCases) {
        if (similarCases.size() >= 3) {
            return 0.72;
        }

        if (similarCases.size() == 2) {
            return 0.55;
        }

        return 0.35;
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}