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
public class FraudContainmentExecutedEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    private String eventId;
    
    private String alertId;
    
    private String userId;
    
    private String transactionId;
    
    private String fraudType;
    
    private Double fraudScore;
    
    private String riskLevel;
    
    private String severity;
    
    private List<String> containmentActions;
    
    private Integer containmentActionsCount;
    
    private BigDecimal transactionAmount;
    
    private String currency;
    
    private String accountStatus;
    
    private Boolean accountSuspended;
    
    private Boolean cardsBlocked;
    
    private Boolean transactionBlocked;
    
    private Boolean enhancedMonitoringEnabled;
    
    private String containmentReason;
    
    private List<String> affectedAccounts;
    
    private List<String> affectedCards;
    
    private List<String> affectedTransactions;
    
    private Map<String, Object> fraudIndicators;
    
    private Map<String, Object> containmentDetails;
    
    private String executedBy;
    
    private String executionSource;
    
    private Instant executedAt;
    
    private Instant detectedAt;
    
    private Long responseTimeMs;
    
    private String correlationId;
    
    private String version;
    
    private Instant timestamp;
}