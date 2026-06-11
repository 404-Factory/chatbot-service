package com.factory.chatbot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocalApiResponse<T> {
    private boolean success;
    private int status;
    private String message;
    private T data;
}
