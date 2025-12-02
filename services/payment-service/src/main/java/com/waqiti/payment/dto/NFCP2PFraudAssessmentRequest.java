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
 * DTO for NFC P2P fraud assessment requests
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NFCP2PFraudAssessmentRequest {
    
    @NotBlank
    private String senderId;
    
    @NotBlank
    private String receiverId;
    
    @NotBlank
    private String transactionId;
    
    @NotNull
    private BigDecimal amount;
    
    @NotBlank
    private String currency;
    
    // NFC specific data
    @NotBlank
    private String senderDeviceId;
    
    @NotBlank
    private String receiverDeviceId;
    
    private String nfcSessionId;
    private String proximityData;
    private String signalStrength;
    private Integer connectionDuration;
    
    // Device verification
    private String senderDeviceFingerprint;
    private String receiverDeviceFingerprint;
    private boolean senderDeviceTrusted;
    private boolean receiverDeviceTrusted;
    private Instant senderLastDeviceActivity;
    private Instant receiverLastDeviceActivity;
    
    // Location and proximity
    private String location;
    private String venue;
    private boolean locationVerified;
    private boolean proximityVerified;
    private String gpsCoordinates;
    
    // Transaction context
    private Instant transactionTime;
    private String transactionType;
    private String message;
    private boolean initiated; // true if sender initiated, false if receiver requested
    
    // Relationship analysis
    private String relationshipType;
    private Integer previousNFCP2PCount;
    private BigDecimal previousNFCP2PTotal;
    private Instant lastNFCP2PDate;
    private boolean frequentContact;
    
    // Risk factors
    private boolean velocityExceeded;
    private boolean unusualAmount;
    private boolean newDevice;
    private boolean deviceMismatch;
    private boolean locationAnomaly;
    private boolean timeAnomaly;
    
    // Behavioral patterns
    private BigDecimal senderAverageNFCP2PAmount;
    private Integer senderNFCP2PFrequency;
    private String senderUsualNFCP2PTimes;
    private String senderUsualNFCP2PLocations;
    
    private BigDecimal receiverAverageNFCP2PAmount;
    private Integer receiverNFCP2PFrequency;
    
    // Security validation
    private boolean cryptogramValidated;
    private String securityLevel;
    private boolean tamperDetected;
    
    private Map<String, Object> additionalData;
}