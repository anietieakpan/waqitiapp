package com.waqiti.payment.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentReversalFailedEvent implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String eventId;
    
    private String eventType;
    
    private String paymentId;
    
    private String reversalId;
    
    private String customerId;
    
    private String merchantId;
    
    private BigDecimal originalAmount;
    
    private BigDecimal reversalAmount;
    
    private String currency;
    
    private String reversalReason;
    
    private String reversalType;
    
    private String errorCode;
    
    private String errorMessage;
    
    private String errorCategory;
    
    private String failureReason;
    
    private Boolean isRetryable;
    
    private Integer retryAttempt;
    
    private String initiatedBy;
    
    private String gatewayId;
    
    private String gatewayErrorCode;
    
    private String gatewayErrorMessage;
    
    private Map<String, Object> metadata;
    
    private String correlationId;
    
    private Instant timestamp;
    
    private Instant failedAt;
}