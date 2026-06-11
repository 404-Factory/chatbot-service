package com.factory.chatbot.service;

import com.factory.chatbot.dto.RecipeRecommendDto;
import com.factory.chatbot.dto.RecipeRecommendationContext;
import com.factory.chatbot.dto.LocalApiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class RecipeContextResolver {

    private final String managementServiceUrl;
    private final RestTemplate restTemplate = new RestTemplate();

    public RecipeContextResolver(
            @Value("${chatbot.services.management-url:http://localhost:8080}") String managementServiceUrl
    ) {
        this.managementServiceUrl = managementServiceUrl;
    }

    public RecipeRecommendationContext resolve(RecipeRecommendDto.Request request) {
        String url = managementServiceUrl + "/api/management/recipes/recommendation-context";
        LocalApiResponse<RecipeRecommendationContext> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                new HttpEntity<>(request),
                new ParameterizedTypeReference<LocalApiResponse<RecipeRecommendationContext>>() {}
        ).getBody();
        return response != null ? response.getData() : null;
    }
}