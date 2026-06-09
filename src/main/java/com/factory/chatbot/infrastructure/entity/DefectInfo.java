package com.factory.chatbot.infrastructure.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "defects")
@Getter
@NoArgsConstructor
public class DefectInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "defect_type")
    private String defectType;

    @Column(name = "defect_code")
    private String defectCode;

    @Column(name = "occurred_time")
    private LocalDateTime occurredTime;

    @Column(name = "detected_time")
    private LocalDateTime detectedTime;

    @Column(name = "cause_equipment_id")
    private Long causeEquipmentId;

    @Column(name = "cause_equipment_name")
    private String causeEquipmentName;

    @Column(name = "cause_process_id")
    private Long causeProcessId;

    @Column(name = "cause_process_name")
    private String causeProcessName;

    @Column(name = "lot_id")
    private Long lotId;
}