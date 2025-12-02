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
public class PaymentRefundUpdateEvent implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String eventId;
    
    private String refundId;
    
    private String paymentId;
    
    private String customerId;
    
    private String merchantId;
    
    private String status;
    
    private String previousStatus;
    
    private BigDecimal refundAmount;
    
    private String currency;
    
    private String refundReason;
    
    private String refundType;
    
    private String gatewayId;
    
    private String gatewayRefundId;
    
    private String processedBy;
    
    private Instant initiatedAt;
    
    private Instant processedAt;
    
    private Instant completedAt;
    
    private Instant updatedAt;
    
    private String failureReason;
    
    private String notes;
    
    private Map<String, Object> metadata;
    
    private String correlationId;
    
    private Instant timestamp;
}