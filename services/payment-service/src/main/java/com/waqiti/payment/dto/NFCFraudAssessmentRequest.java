package com.waqiti.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * DTO for NFC fraud assessment requests
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NFCFraudAssessmentRequest {
    
    @NotBlank
    private String userId;
    
    @NotBlank
    private String transactionId;
    
    @NotBlank
    private String deviceId;
    
    @NotNull
    private BigDecimal amount;
    
    @NotBlank
    private String currency;
    
    private String merchantId;
    private String terminalId;
    private String location;
    private String ipAddress;
    
    // NFC specific data
    private String nfcDeviceType;
    private String emvData;
    private String cryptogram;
    private String applicationTransactionCounter;
    
    // Device characteristics
    private String deviceFingerprint;
    private String deviceTrustScore;
    private boolean deviceRegistered;
    private Instant lastDeviceActivity;
    
    // Transaction context
    private Instant transactionTime;
    private String transactionType;
    private boolean isRecurring;
    private String paymentMethod;
    
    // Behavioral data
    private BigDecimal averageTransactionAmount;
    private Integer transactionFrequency;
    private String usualTransactionTimes;
    private String usualLocations;
    
    // Risk factors
    private boolean velocityExceeded;
    private boolean unusualLocation;
    private boolean unusualAmount;
    private boolean deviceMismatch;
    
    private Map<String, Object> additionalData;
}