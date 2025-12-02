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
public class RecordRefundAnalyticsRequest {
    
    @NotBlank
    private String refundId;
    
    @NotBlank
    private String paymentId;
    
    @NotBlank
    private String customerId;
    
    @NotBlank
    private String merchantId;
    
    @NotBlank
    private String status;
    
    private String previousStatus;
    
    @NotNull
    @Positive
    private BigDecimal refundAmount;
    
    @NotBlank
    private String currency;
    
    private String refundReason;
    
    private String refundType;
    
    private String gatewayId;
    
    private String processedBy;
    
    private Instant initiatedAt;
    
    private Instant processedAt;
    
    private Instant completedAt;
    
    private String failureReason;
    
    @NotBlank
    private String correlationId;
}