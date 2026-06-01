package com.factory.chatbot_service.repository;

import com.factory.chatbot_service.entity.DefectInfo;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DefectInfoRepository extends JpaRepository<DefectInfo, Long> {
    List<DefectInfo> findByCauseEquipmentIdOrderByOccurredTimeDesc(Long equipmentId);
    List<DefectInfo> findByCauseProcessNameOrderByOccurredTimeDesc(String processName);
}