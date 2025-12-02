package com.waqiti.arpayment.integration.client;

import com.waqiti.arpayment.integration.dto.PaymentRequest;
import com.waqiti.arpayment.integration.dto.PaymentResult;
import com.waqiti.arpayment.integration.dto.WalletData;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Feign client for Payment Service integration
 * Provides payment processing capabilities for AR payment transactions
 */
@FeignClient(
    name = "payment-service",
    url = "${feign.client.config.payment-service.url}",
    fallback = PaymentServiceFallback.class
)
public interface PaymentServiceClient {

    /**
     * Process a payment transaction
     * @param request Payment request details
     * @return Payment result with transaction ID and status
     */
    @PostMapping("/api/v1/payments/process")
    PaymentResult processPayment(@RequestBody PaymentRequest request);

    /**
     * Get wallet data for a user
     * @param userId User identifier
     * @return Wallet data including balance and recent transactions
     */
    @GetMapping("/api/v1/wallets/{userId}")
    WalletData getWalletData(@PathVariable("userId") UUID userId);

    /**
     * Validate payment method
     * @param userId User identifier
     * @param paymentMethodId Payment method ID
     * @return true if valid, false otherwise
     */
    @GetMapping("/api/v1/payments/validate")
    boolean validatePaymentMethod(
        @RequestParam("userId") UUID userId,
        @RequestParam("paymentMethodId") String paymentMethodId
    );

    /**
     * Reverse a payment transaction
     * @param transactionId Transaction ID to reverse
     * @param reason Reversal reason
     * @return Reversal result
     */
    @PostMapping("/api/v1/payments/{transactionId}/reverse")
    PaymentResult reversePayment(
        @PathVariable("transactionId") String transactionId,
        @RequestParam("reason") String reason
    );
}
