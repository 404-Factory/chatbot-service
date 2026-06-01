package com.factory.chatbot_service.service;

import com.factory.chatbot_service.dto.SensorSnapshot;

import java.util.Optional;

public interface SensorContextProvider {

    Optional<SensorSnapshot> loadLatest(String equipmentId, String processId);
}