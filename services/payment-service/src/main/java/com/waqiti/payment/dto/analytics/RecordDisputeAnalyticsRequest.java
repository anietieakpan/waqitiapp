package com.waqiti.payment.dto.analytics;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecordDisputeAnalyticsRequest {
    
    @NotBlank
    private String disputeId;
    
    @NotBlank
    private String paymentId;
    
    @NotBlank
    private String customerId;
    
    @NotBlank
    private String merchantId;
    
    @NotBlank
    private String decision;
    
    @NotBlank
    private String resolutionType;
    
    @NotNull
    @Positive
    private BigDecimal amount;
    
    @NotNull
    private BigDecimal refundAmount;
    
    @NotBlank
    private String currency;
    
    @NotBlank
    private String type;
    
    @NotBlank
    private String disputeReason;
    
    private String investigationResult;
    
    private Boolean fundsHeld;
    
    private Boolean accountFrozen;
    
    @NotNull
    private Instant resolutionTimestamp;
    
    @NotBlank
    private String correlationId;
}