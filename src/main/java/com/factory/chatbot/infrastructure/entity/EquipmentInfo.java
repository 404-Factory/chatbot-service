package com.factory.chatbot.infrastructure.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "equipments")
@Getter
@NoArgsConstructor
public class EquipmentInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "process_id")
    private Long processId;

    @Column(name = "name")
    private String name;
}