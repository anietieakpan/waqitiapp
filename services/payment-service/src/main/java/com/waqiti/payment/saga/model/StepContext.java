package com.waqiti.payment.saga.model;

import com.waqiti.payment.core.model.PaymentRequest;
import com.waqiti.payment.core.provider.PaymentProvider;
import com.waqiti.payment.core.strategy.PaymentStrategy;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * Context passed to SAGA step executors containing all necessary execution data
 */
@Data
@Builder
public class StepContext {
    
    private String sagaId;
    
    private String stepName;
    
    private PaymentRequest paymentRequest;
    
    private PaymentProvider provider;
    
    private PaymentStrategy strategy;
    
    private Map<String, Object> previousResults;
    
    private Map<String, Object> metadata;
    
    @Builder.Default
    private long startTime = System.currentTimeMillis();
}