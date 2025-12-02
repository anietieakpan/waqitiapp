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
public class PaymentRoutingChangedEvent implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String eventId;
    
    private String paymentId;
    
    private String originalGateway;
    
    private String newGateway;
    
    private String originalGatewayName;
    
    private String newGatewayName;
    
    private String strategy;
    
    private String routingReason;
    
    private BigDecimal costSavings;
    
    private BigDecimal originalCost;
    
    private BigDecimal newCost;
    
    private Double originalSuccessRate;
    
    private Double newSuccessRate;
    
    private Long originalProcessingTime;
    
    private Long newProcessingTime;
    
    private String customerId;
    
    private BigDecimal paymentAmount;
    
    private String currency;
    
    private String region;
    
    private String paymentMethod;
    
    private String correlationId;
    
    private Instant timestamp;
    
    private String changeInitiatedBy;
    
    private String changeReason;
}