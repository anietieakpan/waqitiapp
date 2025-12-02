package com.waqiti.arpayment.integration.client;

import com.waqiti.arpayment.integration.dto.PaymentRequest;
import com.waqiti.arpayment.integration.dto.PaymentResult;
import com.waqiti.arpayment.integration.dto.WalletData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.UUID;

/**
 * Fallback implementation for Payment Service client
 * Provides graceful degradation when payment service is unavailable
 */
@Slf4j
@Component
public class PaymentServiceFallback implements PaymentServiceClient {

    @Override
    public PaymentResult processPayment(PaymentRequest request) {
        log.error("Payment service unavailable - fallback activated for payment processing");
        return PaymentResult.builder()
                .success(false)
                .errorMessage("Payment service temporarily unavailable. Please try again later.")
                .fallbackMode(true)
                .build();
    }

    @Override
    public WalletData getWalletData(UUID userId) {
        log.error("Payment service unavailable - fallback activated for wallet data retrieval");
        return WalletData.builder()
                .userId(userId)
                .balance(BigDecimal.ZERO)
                .currency("USD")
                .recentTransactions(Collections.emptyList())
                .fallbackMode(true)
                .build();
    }

    @Override
    public boolean validatePaymentMethod(UUID userId, String paymentMethodId) {
        log.error("Payment service unavailable - fallback activated for payment method validation");
        return false; // Fail-safe: reject payment if validation service is down
    }

    @Override
    public PaymentResult reversePayment(String transactionId, String reason) {
        log.error("Payment service unavailable - fallback activated for payment reversal");
        return PaymentResult.builder()
                .success(false)
                .errorMessage("Payment reversal service temporarily unavailable. Please contact support.")
                .fallbackMode(true)
                .build();
    }
}
