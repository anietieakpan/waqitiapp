package com.waqiti.frauddetection.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FraudDetectionRequest {
    
    @NotBlank
    private String transactionId;
    
    @NotBlank
    private String userId;
    
    @NotNull
    @Positive
    private BigDecimal amount;
    
    private String recipientId;
    private String deviceFingerprint;
    private String ipAddress;
    private String userAgent;
    private String country;
    private String city;
    
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
    
    private Map<String, Object> additionalData;
}