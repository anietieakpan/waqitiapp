package com.waqiti.payment.core.strategy;

import com.waqiti.payment.core.model.*;
import com.waqiti.payment.core.provider.PaymentProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Strategy for handling split payments
 * Consolidates logic from SplitPaymentService.java (1,171 LOC)
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SplitPaymentStrategy implements PaymentStrategy {

    private final Map<ProviderType, PaymentProvider> paymentProviders;
    private final SplitPaymentCalculator splitCalculator;
    private final SplitPaymentValidator splitValidator;

    @Override
    public PaymentResult executePayment(PaymentRequest request) {
        SplitPaymentRequest splitRequest = (SplitPaymentRequest) request;
        
        log.info("Executing split payment: totalAmount={}, splits={}", 
                splitRequest.getTotalAmount(), splitRequest.getSplits().size());
        
        try {
            // 1. Validate split configuration
            validateSplitPayment(splitRequest);
            
            // 2. Calculate individual amounts
            List<SplitCalculation> calculations = splitCalculator.calculateSplits(splitRequest);
            
            // 3. Process each split
            List<PaymentResult> splitResults = processSplits(calculations, splitRequest);
            
            // 4. Handle partial failures
            return handleSplitResults(splitResults, splitRequest);
            
        } catch (Exception e) {
            log.error("Split payment failed: ", e);
            return PaymentResult.error("Split payment processing failed: " + e.getMessage());
        }
    }

    private void validateSplitPayment(SplitPaymentRequest request) {
        ValidationResult result = splitValidator.validateSplitRequest(request);
        if (!result.isValid()) {
            throw new PaymentValidationException(result.getErrorMessage());
        }
    }

    private List<PaymentResult> processSplits(List<SplitCalculation> calculations, 
                                            SplitPaymentRequest originalRequest) {
        return calculations.stream()
                .map(calc -> processSingleSplit(calc, originalRequest))
                .toList();
    }

    private PaymentResult processSingleSplit(SplitCalculation calculation, 
                                           SplitPaymentRequest originalRequest) {
        try {
            PaymentProvider provider = paymentProviders.get(originalRequest.getProviderType());
            
            PaymentRequest splitRequest = PaymentRequest.builder()
                    .type(PaymentType.P2P)
                    .amount(calculation.getAmount())
                    .fromUserId(calculation.getFromUserId())
                    .toUserId(calculation.getToUserId())
                    .description("Split payment: " + originalRequest.getDescription())
                    .metadata(Map.of(
                        "originalPaymentId", originalRequest.getPaymentId(),
                        "splitIndex", calculation.getIndex(),
                        "splitType", calculation.getType().name()
                    ))
                    .build();
            
            return provider.processPayment(splitRequest);
            
        } catch (Exception e) {
            log.error("Individual split failed for calculation: {}", calculation, e);
            return PaymentResult.error("Split failed: " + e.getMessage());
        }
    }

    private PaymentResult handleSplitResults(List<PaymentResult> splitResults, 
                                           SplitPaymentRequest originalRequest) {
        long successCount = splitResults.stream()
                .mapToLong(result -> result.isSuccess() ? 1 : 0)
                .sum();
        
        long totalSplits = splitResults.size();
        
        if (successCount == totalSplits) {
            // All splits successful
            return PaymentResult.success(
                originalRequest.getPaymentId(),
                originalRequest.getTotalAmount(),
                "All " + totalSplits + " splits processed successfully"
            );
        } else if (successCount == 0) {
            // All splits failed
            return PaymentResult.error("All " + totalSplits + " splits failed");
        } else {
            // Partial success - need to handle rollbacks
            handlePartialSplitFailure(splitResults, originalRequest);
            return PaymentResult.partialSuccess(
                originalRequest.getPaymentId(),
                originalRequest.getTotalAmount(),
                successCount + " of " + totalSplits + " splits succeeded. Rollback initiated."
            );
        }
    }

    private void handlePartialSplitFailure(List<PaymentResult> splitResults, 
                                         SplitPaymentRequest originalRequest) {
        log.warn("Partial split failure detected. Initiating rollback for successful splits.");
        
        // Reverse successful splits
        splitResults.stream()
                .filter(PaymentResult::isSuccess)
                .forEach(result -> {
                    try {
                        // Create refund for successful split
                        RefundRequest refundRequest = RefundRequest.builder()
                                .originalPaymentId(result.getPaymentId())
                                .amount(result.getAmount())
                                .reason("Split payment rollback")
                                .providerType(originalRequest.getProviderType())
                                .build();
                        
                        PaymentProvider provider = paymentProviders.get(originalRequest.getProviderType());
                        provider.processRefund(refundRequest);
                        
                    } catch (Exception e) {
                        log.error("Failed to rollback split payment: {}", result.getPaymentId(), e);
                    }
                });
    }

    @Override
    public PaymentType getPaymentType() {
        return PaymentType.SPLIT;
    }

    @Override
    public boolean canHandle(PaymentRequest request) {
        return request instanceof SplitPaymentRequest && 
               request.getType() == PaymentType.SPLIT;
    }

    @Override
    public int getPriority() {
        return 5; // Medium priority
    }
}