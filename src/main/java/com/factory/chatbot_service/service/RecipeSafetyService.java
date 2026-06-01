package com.factory.chatbot_service.service;

import com.factory.chatbot_service.dto.RecipeParameter;
import com.factory.chatbot_service.dto.RecipeParameterValue;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class RecipeSafetyService {

    public List<String> validate(RecipeParameter recipe) {
        List<String> violations = new ArrayList<>();

        if (recipe == null) {
            violations.add("recipe must not be null");
            return violations;
        }

        if (recipe.getTemperature() == null || recipe.getTemperature() < 160 || recipe.getTemperature() > 190) {
            violations.add("temperature must be between 160 and 190");
        }

        if (recipe.getPressure() == null || recipe.getPressure() < 2.0 || recipe.getPressure() > 3.0) {
            violations.add("pressure must be between 2.0 and 3.0");
        }

        if (recipe.getSpeed() == null || recipe.getSpeed() < 90 || recipe.getSpeed() > 140) {
            violations.add("speed must be between 90 and 140");
        }

        if (recipe.getDuration() == null || recipe.getDuration() < 30 || recipe.getDuration() > 120) {
            violations.add("duration must be between 30 and 120");
        }

        return violations;
    }

    public List<String> validateParameters(List<RecipeParameterValue> parameters) {
        List<String> violations = new ArrayList<>();

        if (parameters == null || parameters.isEmpty()) {
            violations.add("recommendedParameters must not be empty");
            return violations;
        }

        for (RecipeParameterValue parameter : parameters) {
            boolean hasRecommendedRange = parameter.getRecommendedMin() != null || parameter.getRecommendedMax() != null;

            if (hasRecommendedRange) {
                if (parameter.getRecommendedMin() == null) {
                    violations.add(parameter.getName() + " recommendedMin must not be null");
                    continue;
                }

                if (parameter.getRecommendedMax() == null) {
                    violations.add(parameter.getName() + " recommendedMax must not be null");
                    continue;
                }

                if (parameter.getRecommendedMin() > parameter.getRecommendedMax()) {
                    violations.add(parameter.getName() + " recommendedMin must be less than or equal to recommendedMax");
                }

                if (parameter.getMin() != null && parameter.getRecommendedMin() < parameter.getMin()) {
                    violations.add(parameter.getName() + " recommendedMin must be greater than or equal to " + parameter.getMin());
                }

                if (parameter.getMax() != null && parameter.getRecommendedMax() > parameter.getMax()) {
                    violations.add(parameter.getName() + " recommendedMax must be less than or equal to " + parameter.getMax());
                }

                continue;
            }

            if (parameter.getRecommendedValue() == null) {
                violations.add(parameter.getName() + " recommendedValue or recommended range must not be null");
                continue;
            }

            if (parameter.getMin() != null && parameter.getRecommendedValue() < parameter.getMin()) {
                violations.add(parameter.getName() + " must be greater than or equal to " + parameter.getMin());
            }

            if (parameter.getMax() != null && parameter.getRecommendedValue() > parameter.getMax()) {
                violations.add(parameter.getName() + " must be less than or equal to " + parameter.getMax());
            }
        }

        return violations;
    }
}