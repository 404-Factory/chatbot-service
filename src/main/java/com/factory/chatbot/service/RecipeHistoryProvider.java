package com.factory.chatbot.service;

import com.factory.chatbot.dto.RecipeHistoryCase;
import com.factory.chatbot.dto.LocalApiResponse;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class RecipeHistoryProvider {

    private final String managementServiceUrl;
    private final RestTemplate restTemplate = new RestTemplate();

    public RecipeHistoryProvider(
            @Value("${chatbot.services.management-url:http://localhost:8080}") String managementServiceUrl
    ) {
        this.managementServiceUrl = managementServiceUrl;
    }

    public List<RecipeHistoryCase> findRelevantHistories(
            String equipmentId,
            String processId,
            String productId,
            String defectType
    ) {
        String url = managementServiceUrl + "/api/management/recipes/histories";
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url)
                .queryParam("equipmentId", equipmentId)
                .queryParam("processId", processId)
                .queryParam("productId", productId)
                .queryParam("defectType", defectType);

        ResponseEntity<LocalApiResponse<List<RecipeHistoryCase>>> response = restTemplate.exchange(
                builder.toUriString(),
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<LocalApiResponse<List<RecipeHistoryCase>>>() {}
        );
        return response.getBody() != null ? response.getBody().getData() : List.of();
    }
}