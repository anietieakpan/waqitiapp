package com.waqiti.websocket.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TypingIndicator {
    @NotBlank
    private String recipientId;
    
    @NotBlank
    private String conversationId;
    
    private boolean isTyping;
}