package com.waqiti.payment.integration.eventsourcing;

import com.waqiti.eventsourcing.payment.service.PaymentEventSourcingService;
import com.waqiti.eventsourcing.payment.aggregate.PaymentAggregate;
import com.waqiti.payment.dto.CreatePaymentRequest;
import com.waqiti.payment.dto.PaymentResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentEventSourcingIntegration {
    
    private final PaymentEventSourcingService eventSourcingService;
    
    public CompletableFuture<PaymentResponse> processPaymentWithEventSourcing(CreatePaymentRequest request, String userId) {
        log.info("Processing payment with event sourcing for user: {}", userId);
        
        return eventSourcingService.createPayment(
            request.getFromWalletId(),
            request.getToWalletId(),
            request.getAmount(),
            request.getCurrency(),
            request.getDescription(),
            request.getType(),
            userId,
            request.getCorrelationId()
        ).thenCompose(paymentId -> {
            // Process the payment through the state machine
            return eventSourcingService.processPayment(paymentId, userId, request.getCorrelationId())
                .thenCompose(v -> eventSourcingService.authorizePayment(
                    paymentId, 
                    "AUTH-" + paymentId.substring(0, 8), 
                    request.getAmount(), 
                    calculateFee(request.getAmount()),
                    userId, 
                    request.getCorrelationId()
                ))
                .thenCompose(v -> eventSourcingService.completePayment(paymentId, userId, request.getCorrelationId()))
                .thenCompose(v -> eventSourcingService.getPayment(paymentId, userId))
                .thenApply(this::mapToPaymentResponse);
        }).exceptionally(throwable -> {
            log.error("Error processing payment with event sourcing", throwable);
            throw new RuntimeException("Payment processing failed", throwable);
        });
    }
    
    public CompletableFuture<PaymentResponse> getPaymentStatus(String paymentId, String userId) {
        return eventSourcingService.getPayment(paymentId, userId)
            .thenApply(this::mapToPaymentResponse);
    }
    
    public CompletableFuture<Void> cancelPayment(String paymentId, String reason, String userId, String correlationId) {
        return eventSourcingService.cancelPayment(paymentId, reason, userId, correlationId);
    }
    
    public CompletableFuture<Void> flagPaymentForReview(String paymentId, String reason, String riskScore, String userId, String correlationId) {
        return eventSourcingService.flagPaymentForReview(paymentId, reason, riskScore, userId, correlationId);
    }
    
    private BigDecimal calculateFee(BigDecimal amount) {
        // Simple fee calculation - 1% of amount, minimum $0.50, maximum $10.00
        BigDecimal feePercentage = new BigDecimal("0.01");
        BigDecimal fee = amount.multiply(feePercentage);
        
        BigDecimal minFee = new BigDecimal("0.50");
        BigDecimal maxFee = new BigDecimal("10.00");
        
        if (fee.compareTo(minFee) < 0) {
            return minFee;
        }
        if (fee.compareTo(maxFee) > 0) {
            return maxFee;
        }
        
        return fee.setScale(2, RoundingMode.HALF_UP);
    }
    
    private PaymentResponse mapToPaymentResponse(PaymentAggregate aggregate) {
        if (aggregate == null) {
            log.warn("CRITICAL: PaymentAggregate is null - Cannot map to PaymentResponse");
            throw new IllegalArgumentException("PaymentAggregate cannot be null for response mapping");
        }
        
        return PaymentResponse.builder()
            .paymentId(aggregate.getPaymentId())
            .fromWalletId(aggregate.getFromWalletId())
            .toWalletId(aggregate.getToWalletId())
            .amount(aggregate.getAmount())
            .currency(aggregate.getCurrency())
            .description(aggregate.getDescription())
            .status(aggregate.getStatus().name())
            .type(aggregate.getType().name())
            .createdAt(aggregate.getCreatedAt())
            .completedAt(aggregate.getCompletedAt())
            .feeAmount(aggregate.getFeeAmount())
            .failureReason(aggregate.getFailureReason())
            .requiresManualReview(aggregate.isRequiresManualReview())
            .riskScore(aggregate.getRiskScore())
            .build();
    }
}