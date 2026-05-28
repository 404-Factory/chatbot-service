package com.factory.chatbot_service.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "equipment_info")
@Getter
@NoArgsConstructor
public class EquipmentInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "equipment_id")
    private Long equipmentId;

    @Column(name = "process_id")
    private Long processId;

    @Column(name = "equipment_name")
    private String equipmentName;
}
