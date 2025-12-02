package com.waqiti.payment.batch;

import com.waqiti.payment.dto.BatchPaymentRequest;
import com.waqiti.payment.dto.PaymentRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Production-grade batch validation service with comprehensive checks
 * Validates batch integrity, payment limits, and business rules before processing
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BatchValidationService {

    @Value("${batch.payment.max-batch-size:1000}")
    private int maxBatchSize;

    @Value("${batch.payment.max-total-amount:1000000}")
    private BigDecimal maxTotalAmount;

    @Value("${batch.payment.max-individual-amount:100000}")
    private BigDecimal maxIndividualAmount;

    @Value("${batch.payment.allow-duplicates:false}")
    private boolean allowDuplicates;

    /**
     * CRITICAL: Comprehensive batch validation
     */
    public BatchValidationResult validateBatch(BatchPaymentRequest batchRequest) {
        log.debug("SECURITY: Validating batch: {}, size: {}", 
                batchRequest.getBatchId(), batchRequest.getPayments().size());

        try {
            // 1. Basic structure validation
            if (batchRequest.getBatchId() == null || batchRequest.getBatchId().trim().isEmpty()) {
                return createFailedResult("Batch ID is required");
            }

            if (batchRequest.getInitiatedBy() == null || batchRequest.getInitiatedBy().trim().isEmpty()) {
                return createFailedResult("Batch initiator is required");
            }

            List<PaymentRequest> payments = batchRequest.getPayments();
            if (payments == null || payments.isEmpty()) {
                return createFailedResult("Batch must contain at least one payment");
            }

            // 2. Size validation
            if (payments.size() > maxBatchSize) {
                return createFailedResult(String.format("Batch size %d exceeds maximum allowed size %d", 
                        payments.size(), maxBatchSize));
            }

            // 3. Individual payment validation
            BigDecimal totalAmount = BigDecimal.ZERO;
            Set<String> paymentIds = new HashSet<>();
            
            for (int i = 0; i < payments.size(); i++) {
                PaymentRequest payment = payments.get(i);
                
                // Validate individual payment
                BatchValidationResult paymentValidation = validateIndividualPayment(payment, i);
                if (!paymentValidation.isValid()) {
                    return paymentValidation;
                }

                // Check for duplicate payment IDs
                if (!allowDuplicates) {
                    if (paymentIds.contains(payment.getPaymentId())) {
                        return createFailedResult(String.format("Duplicate payment ID found: %s at position %d", 
                                payment.getPaymentId(), i));
                    }
                    paymentIds.add(payment.getPaymentId());
                }

                // Accumulate total amount
                totalAmount = totalAmount.add(payment.getAmount());
            }

            // 4. Total amount validation
            if (totalAmount.compareTo(maxTotalAmount) > 0) {
                return createFailedResult(String.format("Total batch amount %s exceeds maximum allowed %s", 
                        totalAmount, maxTotalAmount));
            }

            // 5. Business rule validation
            BatchValidationResult businessValidation = validateBusinessRules(batchRequest);
            if (!businessValidation.isValid()) {
                return businessValidation;
            }

            log.debug("SECURITY: Batch validation successful for batch: {}", batchRequest.getBatchId());
            return createSuccessResult(String.format("Batch validation successful - %d payments, total amount %s", 
                    payments.size(), totalAmount));

        } catch (Exception e) {
            log.error("CRITICAL: Batch validation failed for batch: {}", batchRequest.getBatchId(), e);
            return createFailedResult("Batch validation failed: " + e.getMessage());
        }
    }

    /**
     * Validate individual payment within batch
     */
    private BatchValidationResult validateIndividualPayment(PaymentRequest payment, int position) {
        // Payment ID validation
        if (payment.getPaymentId() == null || payment.getPaymentId().trim().isEmpty()) {
            return createFailedResult(String.format("Payment ID is required at position %d", position));
        }

        // Amount validation
        if (payment.getAmount() == null || payment.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return createFailedResult(String.format("Invalid payment amount at position %d: %s", 
                    position, payment.getAmount()));
        }

        if (payment.getAmount().compareTo(maxIndividualAmount) > 0) {
            return createFailedResult(String.format("Payment amount %s at position %d exceeds maximum %s", 
                    payment.getAmount(), position, maxIndividualAmount));
        }

        // Currency validation
        if (payment.getCurrency() == null || payment.getCurrency().trim().isEmpty()) {
            return createFailedResult(String.format("Currency is required at position %d", position));
        }

        if (!isValidCurrency(payment.getCurrency())) {
            return createFailedResult(String.format("Invalid currency %s at position %d", 
                    payment.getCurrency(), position));
        }

        // Recipient validation
        if (payment.getRecipientId() == null) {
            return createFailedResult(String.format("Recipient ID is required at position %d", position));
        }

        // Self-payment check
        if (payment.getPayerId() != null && payment.getPayerId().equals(payment.getRecipientId())) {
            return createFailedResult(String.format("Self-payment not allowed at position %d", position));
        }

        return createSuccessResult("Payment validation successful");
    }

    /**
     * Validate business rules for the batch
     */
    private BatchValidationResult validateBusinessRules(BatchPaymentRequest batchRequest) {
        // Check for time-based restrictions
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        java.time.LocalTime currentTime = now.toLocalTime();
        
        // Example: Block batch processing during maintenance window (2 AM - 4 AM)
        if (currentTime.isAfter(java.time.LocalTime.of(2, 0)) && 
            currentTime.isBefore(java.time.LocalTime.of(4, 0))) {
            return createFailedResult("Batch processing is not allowed during maintenance window (2 AM - 4 AM)");
        }

        // Validate batch processing permissions
        // This would typically check user roles and permissions
        
        // Check for suspicious patterns
        BatchValidationResult suspiciousPatternCheck = checkForSuspiciousPatterns(batchRequest);
        if (!suspiciousPatternCheck.isValid()) {
            return suspiciousPatternCheck;
        }

        return createSuccessResult("Business rule validation successful");
    }

    /**
     * Check for suspicious patterns in the batch
     */
    private BatchValidationResult checkForSuspiciousPatterns(BatchPaymentRequest batchRequest) {
        List<PaymentRequest> payments = batchRequest.getPayments();

        // Check for excessive payments to same recipient
        java.util.Map<String, Long> recipientCounts = payments.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        p -> p.getRecipientId().toString(), 
                        java.util.stream.Collectors.counting()));

        for (java.util.Map.Entry<String, Long> entry : recipientCounts.entrySet()) {
            if (entry.getValue() > 50) { // Configurable threshold
                log.warn("SECURITY: Suspicious pattern detected - {} payments to recipient {} in batch {}", 
                        entry.getValue(), entry.getKey(), batchRequest.getBatchId());
                return createFailedResult(String.format("Suspicious pattern: %d payments to same recipient %s", 
                        entry.getValue(), entry.getKey()));
            }
        }

        // Check for round number patterns (potential money laundering)
        long roundAmountCount = payments.stream()
                .mapToLong(p -> {
                    BigDecimal amount = p.getAmount();
                    return amount.remainder(BigDecimal.valueOf(100)).compareTo(BigDecimal.ZERO) == 0 ? 1 : 0;
                })
                .sum();

        if (roundAmountCount > payments.size() * 0.8) { // 80% round amounts
            log.warn("SECURITY: Suspicious pattern detected - {} round amounts out of {} payments in batch {}", 
                    roundAmountCount, payments.size(), batchRequest.getBatchId());
            return createFailedResult("Suspicious pattern: Too many round amount payments detected");
        }

        return createSuccessResult("No suspicious patterns detected");
    }

    /**
     * Validate currency code
     */
    private boolean isValidCurrency(String currency) {
        // List of supported currencies
        Set<String> supportedCurrencies = Set.of("USD", "EUR", "GBP", "NGN", "KES", "GHS", "ZAR");
        return supportedCurrencies.contains(currency.toUpperCase());
    }

    private BatchValidationResult createSuccessResult(String message) {
        return BatchValidationResult.builder()
                .valid(true)
                .message(message)
                .build();
    }

    private BatchValidationResult createFailedResult(String errorMessage) {
        return BatchValidationResult.builder()
                .valid(false)
                .errorMessage(errorMessage)
                .build();
    }

    @lombok.Builder
    @lombok.Data
    public static class BatchValidationResult {
        private boolean valid;
        private String message;
        private String errorMessage;
        private java.util.List<String> warnings;
    }
}