package com.waqiti.payment.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentDisputeProcessedEvent implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String eventId;
    
    private String disputeId;
    
    private String paymentId;
    
    private String customerId;
    
    private String merchantId;
    
    private BigDecimal amount;
    
    private String currency;
    
    private String type;
    
    private String response;
    
    private String decision;
    
    private List<String> actions;
    
    private String resolutionType;
    
    private BigDecimal refundAmount;
    
    private Boolean fundsHeld;
    
    private Boolean accountFrozen;
    
    private String disputeReason;
    
    private String evidence;
    
    private String investigationResult;
    
    private String approvedBy;
    
    private Instant approvalTimestamp;
    
    private Map<String, Object> metadata;
    
    private String correlationId;
    
    private Instant timestamp;
}