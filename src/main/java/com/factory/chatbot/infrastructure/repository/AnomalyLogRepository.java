package com.factory.chatbot.infrastructure.repository;

import com.factory.chatbot.infrastructure.entity.AnomalyLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AnomalyLogRepository extends JpaRepository<AnomalyLog, Long> {

    List<AnomalyLog> findTop5ByEquipmentIdOrderByOccurredTimeDesc(Long equipmentId);

    List<AnomalyLog> findByEquipmentIdOrderByOccurredTimeDesc(Long equipmentId);
}