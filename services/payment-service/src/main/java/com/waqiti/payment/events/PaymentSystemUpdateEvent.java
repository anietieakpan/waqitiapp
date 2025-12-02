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
public class PaymentSystemUpdateEvent implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String eventId;
    
    private String eventType;
    
    private Instant timestamp;
    
    private String correlationId;
    
    private String customerId;
    
    private String creditLineId;
    
    private BigDecimal newCreditLimit;
    
    private BigDecimal previousCreditLimit;
    
    private String updateReason;
    
    private String initiatedBy;
    
    private String approvedBy;
    
    private Instant approvalTimestamp;
    
    private String underwritingResultId;
    
    private String riskLevel;
    
    private String currency;
    
    private Boolean requiresPaymentSystemUpdate;
    
    private Boolean requiresRiskSystemUpdate;
    
    private Map<String, Object> metadata;
    
    private String sourceService;
}