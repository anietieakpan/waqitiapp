package com.waqiti.messaging.dto;

import com.waqiti.messaging.service.MessageType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EncryptedMessage {
    
    @NotBlank
    private String encryptedContent;
    
    @NotBlank
    private String encryptedKey;
    
    private String ephemeralPublicKey;
    
    @NotBlank
    private String signature;
    
    @NotNull
    private MessageType messageType;
    
    @NotBlank
    private String sessionId;
    
    private String senderId;
    
    private Integer messageNumber;
    
    private LocalDateTime timestamp;
    
    private Boolean isEphemeral;
    
    private Integer ephemeralDuration;
    
    private String replyToMessageId;
}