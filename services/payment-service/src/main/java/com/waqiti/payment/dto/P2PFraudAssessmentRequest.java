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
 * DTO for P2P fraud assessment requests
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class P2PFraudAssessmentRequest {
    
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
    
    // Sender information
    private String senderIpAddress;
    private String senderDeviceId;
    private String senderLocation;
    private String senderSessionId;
    
    // Receiver information
    private String receiverDeviceId;
    private String receiverLocation;
    private boolean receiverKnownContact;
    private Instant lastContactInteraction;
    
    // Transaction context
    private Instant transactionTime;
    private String transactionType;
    private String message;
    private boolean isRecurring;
    
    // Relationship analysis
    private String relationshipType; // UNKNOWN, FRIEND, FAMILY, MERCHANT
    private Integer previousTransactionCount;
    private BigDecimal previousTransactionTotal;
    private Instant firstTransactionDate;
    private Instant lastTransactionDate;
    
    // Risk factors
    private boolean velocityExceeded;
    private boolean unusualAmount;
    private boolean newReceiver;
    private boolean crossBorderTransfer;
    private boolean highRiskReceiver;
    
    // Behavioral data
    private BigDecimal senderAverageP2PAmount;
    private Integer senderP2PFrequency;
    private String senderUsualTransactionTimes;
    private BigDecimal receiverAverageReceiveAmount;
    private Integer receiverReceiveFrequency;
    
    private Map<String, Object> additionalData;
}