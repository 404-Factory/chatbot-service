package com.factory.chatbot.infrastructure.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "anomalies") // 실제 DB 테이블명 매핑
@Getter
@NoArgsConstructor
public class AnomalyLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "log_id")
    private Long logId;

    @Column(name = "equipment_id")
    private Long equipmentId;

    @Column(name = "recipe_parameter")
    private String recipeParameter;

    @Column(name = "rule_name")
    private String ruleName;

    private String severity;

    @Column(name = "occurred_time")
    private LocalDateTime occurredTime;

    @Column(name = "detection_reason")
    private String detectionReason;

    @Column(name = "anomaly_type")
    private String anomalyType;
}