package com.waqiti.payment.service;

import com.waqiti.payment.dto.*;
import com.waqiti.payment.integration.eventsourcing.PaymentEventSourcingIntegration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * Extension service that adds event sourcing capabilities to payment processing
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentServiceEventSourcingExtension {
    
    private final PaymentEventSourcingIntegration eventSourcingIntegration;
    
    @Value("${payment.event-sourcing.enabled:false}")
    private boolean eventSourcingEnabled;
    
    @Value("${payment.event-sourcing.async:true}")
    private boolean asyncEventSourcing;
    
    /**
     * Process payment with event sourcing for audit and compliance
     */
    public CompletableFuture<PaymentResponse> processPaymentWithEventSourcing(CreatePaymentRequest request, String userId) {
        if (!eventSourcingEnabled) {
            log.debug("Event sourcing is disabled, skipping event sourcing for payment");
            return CompletableFuture.completedFuture(null);
        }
        
        log.info("Processing payment with event sourcing - userId: {}, amount: {}", userId, request.getAmount());
        
        if (asyncEventSourcing) {
            // Process asynchronously without blocking the main payment flow
            eventSourcingIntegration.processPaymentWithEventSourcing(request, userId)
                .thenAccept(response -> log.info("Payment event sourcing completed asynchronously: {}", response.getPaymentId()))
                .exceptionally(throwable -> {
                    log.error("Error in async event sourcing for payment", throwable);
                    return null;
                });
            
            return CompletableFuture.completedFuture(null);
        } else {
            // Process synchronously
            return eventSourcingIntegration.processPaymentWithEventSourcing(request, userId);
        }
    }
    
    /**
     * Get payment status from event store
     */
    public CompletableFuture<PaymentResponse> getPaymentFromEventStore(String paymentId, String userId) {
        if (!eventSourcingEnabled) {
            return CompletableFuture.completedFuture(null);
        }
        
        return eventSourcingIntegration.getPaymentStatus(paymentId, userId);
    }
    
    /**
     * Cancel payment with event sourcing
     */
    public CompletableFuture<Void> cancelPaymentWithEventSourcing(String paymentId, String reason, String userId, String correlationId) {
        if (!eventSourcingEnabled) {
            return CompletableFuture.completedFuture(null);
        }
        
        log.info("Cancelling payment with event sourcing - paymentId: {}, reason: {}", paymentId, reason);
        
        return eventSourcingIntegration.cancelPayment(paymentId, reason, userId, correlationId);
    }
    
    /**
     * Flag payment for review with event sourcing
     */
    public CompletableFuture<Void> flagPaymentForReview(String paymentId, String reason, String riskScore, String userId, String correlationId) {
        if (!eventSourcingEnabled) {
            return CompletableFuture.completedFuture(null);
        }
        
        log.info("Flagging payment for review with event sourcing - paymentId: {}, riskScore: {}", paymentId, riskScore);
        
        return eventSourcingIntegration.flagPaymentForReview(paymentId, reason, riskScore, userId, correlationId);
    }
    
    /**
     * Check if event sourcing is enabled
     */
    public boolean isEventSourcingEnabled() {
        return eventSourcingEnabled;
    }
}