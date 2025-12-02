package com.waqiti.voice.client;

import com.waqiti.voice.client.dto.*;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Feign client for Payment Service integration
 *
 * Handles:
 * - Payment execution
 * - Payment status queries
 * - Payment cancellation
 * - Balance inquiries
 *
 * Resilience:
 * - Circuit breaker configured in FeignConfig
 * - Retry logic for transient failures
 * - Fallback to PaymentServiceFallback
 * - Request/response logging
 */
@FeignClient(
    name = "payment-service",
    url = "${services.payment-service.url:http://payment-service}",
    path = "/api/v1",
    configuration = FeignConfig.class,
    fallback = PaymentServiceFallback.class
)
public interface PaymentServiceClient {

    /**
     * Execute a payment
     *
     * @param request Payment request with amount, recipient, etc.
     * @return Payment result with transaction ID and status
     */
    @PostMapping("/payments")
    PaymentResult executePayment(@RequestBody PaymentRequest request);

    /**
     * Execute payment with idempotency key
     * CRITICAL: Use this for all voice-initiated payments
     *
     * @param idempotencyKey Unique key to prevent duplicate payments
     * @param request Payment request
     * @return Payment result
     */
    @PostMapping("/payments")
    PaymentResult executePaymentIdempotent(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody PaymentRequest request);

    /**
     * Get payment status
     *
     * @param paymentId Payment ID
     * @return Payment status details
     */
    @GetMapping("/payments/{paymentId}")
    PaymentStatus getPaymentStatus(@PathVariable String paymentId);

    /**
     * Cancel a pending payment
     *
     * @param paymentId Payment ID
     * @param reason Cancellation reason
     * @return Cancellation result
     */
    @PostMapping("/payments/{paymentId}/cancel")
    CancellationResult cancelPayment(
            @PathVariable String paymentId,
            @RequestBody CancellationRequest reason);

    /**
     * Get user's account balance
     *
     * @param userId User ID
     * @return Balance information
     */
    @GetMapping("/accounts/{userId}/balance")
    BalanceInfo getBalance(@PathVariable UUID userId);

    /**
     * Verify recipient exists and is valid
     *
     * @param recipientIdentifier Phone, email, username, or user ID
     * @return Recipient verification result
     */
    @PostMapping("/recipients/verify")
    RecipientVerification verifyRecipient(@RequestBody RecipientLookupRequest recipientIdentifier);

    /**
     * Get transaction history
     *
     * @param userId User ID
     * @param limit Number of transactions to retrieve
     * @return Transaction history
     */
    @GetMapping("/transactions")
    TransactionHistory getTransactionHistory(
            @RequestParam UUID userId,
            @RequestParam(defaultValue = "10") int limit);

    /**
     * Request payment from another user
     *
     * @param request Payment request details
     * @return Request result
     */
    @PostMapping("/payment-requests")
    PaymentRequestResult requestPayment(@RequestBody PaymentRequest request);

    /**
     * Transfer funds between accounts
     *
     * @param request Transfer request
     * @return Transfer result
     */
    @PostMapping("/transfers")
    TransferResult transferFunds(@RequestBody TransferRequest request);

    /**
     * Split bill among multiple participants
     *
     * @param request Split bill request
     * @return Split result with participant details
     */
    @PostMapping("/split-bills")
    SplitBillResult splitBill(@RequestBody SplitBillRequest request);

    /**
     * Pay bill (utilities, services, etc.)
     *
     * @param request Bill payment request
     * @return Bill payment result
     */
    @PostMapping("/bill-payments")
    BillPaymentResult payBill(@RequestBody BillPaymentRequest request);

    /**
     * Health check
     */
    @GetMapping("/health")
    String healthCheck();
}
