package com.factory.chatbot.infrastructure.repository;

import com.factory.chatbot.infrastructure.entity.EquipmentInfo;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EquipmentInfoRepository extends JpaRepository<EquipmentInfo, Long> {
    Optional<EquipmentInfo> findByEquipmentName(String equipmentName);
}