package com.waqiti.payment.saga.model;

import com.waqiti.payment.core.model.UnifiedPaymentRequest;
import com.waqiti.payment.core.provider.PaymentProvider;
import com.waqiti.payment.core.strategy.PaymentStrategy;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * SAGA Transaction model representing a distributed payment transaction
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SagaTransaction {
    
    private String transactionId;
    private UnifiedPaymentRequest paymentRequest;
    private PaymentStrategy strategy;
    private PaymentProvider provider;
    private boolean compensationEnabled;
    private int maxRetries;
    private Map<String, Object> context;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    
    @Builder.Default
    private SagaType sagaType = SagaType.ORCHESTRATION;
    
    public enum SagaType {
        ORCHESTRATION,  // Centralized coordination
        CHOREOGRAPHY    // Event-driven coordination
    }
}