package com.waqiti.support.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageRequest {
    @NotBlank(message = "Message is required")
    private String message;
    
    @NotBlank(message = "User ID is required")
    private String userId;
    
    private String sessionId;
    private Map<String, Object> context;
    private String messageType;
}