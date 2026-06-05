package com.factory.chatbot_service.repository;

import com.factory.chatbot_service.entity.AnomalyLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AnomalyLogRepository extends JpaRepository<AnomalyLog, Long> {

    /**
     * 설비 ID로 조회하여 발생 시각(OccurredTime) 기준 내림차순으로 최근 5개만 가져오는 매직 메서드
     */
    List<AnomalyLog> findTop5ByEquipmentIdOrderByOccurredTimeDesc(Integer equipmentId);

    List<AnomalyLog> findByEquipmentIdOrderByOccurredTimeDesc(Integer equipmentId);
}