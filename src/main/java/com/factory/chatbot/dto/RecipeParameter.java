package com.factory.chatbot.dto;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RecipeParameter {
    private Double temperature;

    private Double pressure;

    private Double speed;

    private Double duration;
}