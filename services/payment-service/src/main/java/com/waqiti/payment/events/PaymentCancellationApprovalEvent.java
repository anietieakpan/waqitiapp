package com.waqiti.payment.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentCancellationApprovalEvent implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String eventId;
    
    private String paymentId;
    
    private String cancellationReason;
    
    private BigDecimal refundAmount;
    
    private BigDecimal cancellationFee;
    
    private String customerId;
    
    private String merchantId;
    
    private BigDecimal originalAmount;
    
    private String currency;
    
    private String approvalStatus;
    
    private String approvedBy;
    
    private Instant approvalTimestamp;
    
    private String approvalComments;
    
    private String rejectionReason;
    
    private String requestedBy;
    
    private String requestReason;
    
    private Instant requestTimestamp;
    
    private String correlationId;
    
    private Instant timestamp;
}