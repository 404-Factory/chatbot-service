package com.factory.chatbot_service.service;

import com.factory.chatbot_service.dto.RecipeHistoryCase;

import java.util.List;

public interface RecipeHistoryProvider {

    List<RecipeHistoryCase> findRelevantHistories(
            String equipmentId,
            String processId,
            String productId,
            String defectType
    );
}