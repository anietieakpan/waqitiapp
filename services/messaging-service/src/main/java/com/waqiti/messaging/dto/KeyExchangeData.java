package com.waqiti.messaging.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KeyExchangeData {
    
    @NotBlank
    private String sessionId;
    
    @NotBlank
    private String ephemeralPublicKey;
    
    @NotNull
    private Integer preKeyId;
    
    private String senderId;
    
    private String recipientId;
    
    private String deviceId;
    
    private Integer registrationId;
}