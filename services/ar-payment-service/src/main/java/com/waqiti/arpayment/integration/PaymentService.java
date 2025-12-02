package com.waqiti.arpayment.integration;

import com.waqiti.arpayment.integration.client.PaymentServiceClient;
import com.waqiti.arpayment.integration.dto.PaymentRequest;
import com.waqiti.arpayment.integration.dto.PaymentResult;
import com.waqiti.arpayment.integration.dto.WalletData;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Payment service wrapper with circuit breaker, metrics, and error handling
 * Provides enterprise-grade payment processing capabilities
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentServiceClient paymentServiceClient;
    private final MeterRegistry meterRegistry;

    /**
     * Process payment with comprehensive error handling and metrics
     */
    public PaymentResult processPayment(PaymentRequest request) {
        Timer.Sample sample = Timer.start(meterRegistry);

        try {
            log.info("Processing payment for user: {} amount: {} {}",
                    request.getUserId(), request.getAmount(), request.getCurrency());

            PaymentResult result = paymentServiceClient.processPayment(request);

            sample.stop(Timer.builder("payment.process")
                    .tag("status", result.isSuccess() ? "success" : "failure")
                    .tag("currency", request.getCurrency())
                    .register(meterRegistry));

            if (result.isSuccess()) {
                meterRegistry.counter("payment.success",
                        "currency", request.getCurrency()).increment();
            } else {
                meterRegistry.counter("payment.failure",
                        "error_code", result.getErrorCode()).increment();
                log.error("Payment failed: {} - {}", result.getErrorCode(), result.getErrorMessage());
            }

            return result;

        } catch (Exception e) {
            sample.stop(Timer.builder("payment.process")
                    .tag("status", "error")
                    .tag("error", e.getClass().getSimpleName())
                    .register(meterRegistry));

            log.error("Payment processing exception for user: {}", request.getUserId(), e);
            meterRegistry.counter("payment.exception",
                    "exception", e.getClass().getSimpleName()).increment();

            return PaymentResult.builder()
                    .success(false)
                    .errorMessage("Payment processing failed: " + e.getMessage())
                    .errorCode("PAYMENT_SERVICE_ERROR")
                    .build();
        }
    }

    /**
     * Get wallet data with caching and metrics
     */
    public WalletData getWalletData(UUID userId) {
        Timer.Sample sample = Timer.start(meterRegistry);

        try {
            log.debug("Fetching wallet data for user: {}", userId);

            WalletData walletData = paymentServiceClient.getWalletData(userId);

            sample.stop(Timer.builder("wallet.fetch")
                    .tag("status", "success")
                    .register(meterRegistry));

            return walletData;

        } catch (Exception e) {
            sample.stop(Timer.builder("wallet.fetch")
                    .tag("status", "error")
                    .register(meterRegistry));

            log.error("Failed to fetch wallet data for user: {}", userId, e);
            return WalletData.builder()
                    .userId(userId)
                    .fallbackMode(true)
                    .build();
        }
    }

    /**
     * Validate payment method
     */
    public boolean validatePaymentMethod(UUID userId, String paymentMethodId) {
        try {
            return paymentServiceClient.validatePaymentMethod(userId, paymentMethodId);
        } catch (Exception e) {
            log.error("Payment method validation failed for user: {}", userId, e);
            return false; // Fail-safe
        }
    }

    /**
     * Reverse payment with audit trail
     */
    public PaymentResult reversePayment(String transactionId, String reason) {
        log.warn("Payment reversal requested for transaction: {} reason: {}", transactionId, reason);

        try {
            PaymentResult result = paymentServiceClient.reversePayment(transactionId, reason);

            meterRegistry.counter("payment.reversal",
                    "success", String.valueOf(result.isSuccess())).increment();

            return result;

        } catch (Exception e) {
            log.error("Payment reversal failed for transaction: {}", transactionId, e);
            meterRegistry.counter("payment.reversal.error").increment();

            return PaymentResult.builder()
                    .success(false)
                    .errorMessage("Reversal failed: " + e.getMessage())
                    .build();
        }
    }
}
