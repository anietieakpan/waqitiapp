package com.waqiti.payment.core.provider;

import com.waqiti.payment.core.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Plaid payment provider implementation for ACH transfers
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class PlaidPaymentProvider implements PaymentProvider {

    @Override
    public PaymentResult processPayment(PaymentRequest request) {
        log.info("Processing Plaid ACH payment: {}", request.getPaymentId());
        
        try {
            // Validate Plaid-specific requirements
            ValidationResult validation = validatePlaidPayment(request);
            if (!validation.isValid()) {
                return PaymentResult.error(validation.getErrorMessage());
            }
            
            // Calculate fees
            FeeCalculation fees = calculatePlaidFees(request);
            
            // Simulate Plaid API call
            String transactionId = simulatePlaidTransaction(request);
            
            log.info("Plaid ACH payment initiated: paymentId={}, transactionId={}", 
                    request.getPaymentId(), transactionId);
            
            return PaymentResult.builder()
                    .paymentId(request.getPaymentId())
                    .transactionId(transactionId)
                    .status(PaymentStatus.PENDING) // ACH transfers are typically pending
                    .amount(request.getAmount())
                    .fees(fees)
                    .providerResponse("Plaid ACH transfer initiated successfully")
                    .processedAt(LocalDateTime.now())
                    .build();
                    
        } catch (Exception e) {
            log.error("Plaid payment failed: ", e);
            return PaymentResult.builder()
                    .paymentId(request.getPaymentId())
                    .status(PaymentStatus.FAILED)
                    .amount(request.getAmount())
                    .providerResponse("Plaid payment failed: " + e.getMessage())
                    .processedAt(LocalDateTime.now())
                    .build();
        }
    }

    @Override
    public PaymentResult refundPayment(RefundRequest request) {
        log.info("Processing Plaid ACH refund: {}", request.getRefundId());
        
        try {
            // Simulate Plaid refund API call
            String refundId = "plaid_rfnd_" + UUID.randomUUID().toString().substring(0, 8);
            
            return PaymentResult.builder()
                    .paymentId(request.getRefundId())
                    .transactionId(refundId)
                    .status(PaymentStatus.PENDING) // ACH refunds are also pending
                    .amount(request.getAmount())
                    .fees(FeeCalculation.noFees())
                    .providerResponse("Plaid ACH refund initiated successfully")
                    .processedAt(LocalDateTime.now())
                    .build();
                    
        } catch (Exception e) {
            log.error("Plaid refund failed: ", e);
            return PaymentResult.error("Plaid refund failed: " + e.getMessage());
        }
    }

    @Override
    public ProviderType getProviderType() {
        return ProviderType.PLAID;
    }

    @Override
    public boolean isAvailable() {
        return true; // In production, check Plaid service status
    }
    
    private ValidationResult validatePlaidPayment(PaymentRequest request) {
        // Plaid-specific validation
        if (request.getAmount().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            return ValidationResult.invalid("Amount must be positive");
        }
        
        // ACH has daily limits
        java.math.BigDecimal dailyLimit = new java.math.BigDecimal("25000.00");
        if (request.getAmount().compareTo(dailyLimit) > 0) {
            return ValidationResult.invalid("Amount exceeds daily ACH limit");
        }
        
        if (request.getMetadata() == null || 
            !request.getMetadata().containsKey("bankAccountId")) {
            return ValidationResult.invalid("Bank account ID is required for ACH transfers");
        }
        
        return ValidationResult.valid();
    }
    
    private FeeCalculation calculatePlaidFees(PaymentRequest request) {
        // Plaid ACH fee structure: typically flat fee
        java.math.BigDecimal processingFee = new java.math.BigDecimal("0.25");
        java.math.BigDecimal networkFee = java.math.BigDecimal.ZERO;
        
        return FeeCalculation.builder()
                .processingFee(processingFee)
                .networkFee(networkFee)
                .totalFees(processingFee)
                .feeStructure("Plaid ACH: $0.25 flat fee")
                .currency("USD")
                .build();
    }
    
    private String simulatePlaidTransaction(PaymentRequest request) {
        // Simulate Plaid transaction ID
        return "plaid_ach_" + UUID.randomUUID().toString().substring(0, 8);
    }
}