package com.waqiti.websocket.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Map;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {
    private String id;
    
    @NotBlank
    private String senderId;
    
    @NotBlank
    private String recipientId;
    
    @NotBlank
    @Size(max = 1000)
    private String content;
    
    private String messageType;
    private String conversationId;
    private Instant timestamp;
    private String status;
    private Map<String, Object> metadata;
    private String replyToMessageId;
    
    public enum MessageType {
        TEXT,
        IMAGE,
        FILE,
        PAYMENT_REQUEST,
        LOCATION,
        EMOJI
    }
    
    public enum Status {
        SENT,
        DELIVERED,
        READ,
        FAILED
    }
}