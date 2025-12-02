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
public class PaymentReconciliationUpdateEvent implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String eventId;
    
    private String reconciliationId;
    
    private String settlementId;
    
    private String paymentId;
    
    private String status;
    
    private String previousStatus;
    
    private String reconciliationType;
    
    private BigDecimal reconciliationAmount;
    
    private String currency;
    
    private Integer matchedTransactions;
    
    private Integer unmatchedTransactions;
    
    private BigDecimal discrepancyAmount;
    
    private String gatewayId;
    
    private String merchantId;
    
    private Instant periodStartDate;
    
    private Instant periodEndDate;
    
    private Instant initiatedAt;
    
    private Instant completedAt;
    
    private Instant updatedAt;
    
    private String initiatedBy;
    
    private String approvedBy;
    
    private String reconciliationNotes;
    
    private String discrepancyReason;
    
    private Map<String, Object> metadata;
    
    private String correlationId;
    
    private Instant timestamp;
}