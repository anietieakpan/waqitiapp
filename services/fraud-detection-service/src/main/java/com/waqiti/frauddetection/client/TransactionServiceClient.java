package com.waqiti.frauddetection.client;

import com.waqiti.frauddetection.client.dto.TransactionBlockRequest;
import com.waqiti.frauddetection.client.dto.TransactionBlockResponse;
import com.waqiti.frauddetection.client.dto.TransactionUnblockRequest;
import com.waqiti.frauddetection.client.dto.TransactionStatusResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

/**
 * Feign Client for Transaction Service
 *
 * CRITICAL INTEGRATION - Used for blocking fraudulent transactions in real-time.
 * This client was MISSING, causing high-risk transactions to NOT be blocked.
 *
 * Security Features:
 * - Circuit breaker to prevent cascade failures
 * - Retry with exponential backoff
 * - Bulkhead pattern for resource isolation
 * - Request/response validation
 * - Comprehensive error handling
 *
 * @author Waqiti Security Team
 * @version 1.0.0
 */
@FeignClient(
    name = "transaction-service",
    url = "${services.transaction-service.url:http://transaction-service:8086}",
    fallback = TransactionServiceClientFallback.class
)
public interface TransactionServiceClient {

    /**
     * Block a transaction due to fraud detection
     *
     * CRITICAL OPERATION - Must complete successfully to prevent fraud
     *
     * @param request Transaction block request with fraud details
     * @return Transaction block response with status
     */
    @PostMapping("/api/v1/transactions/block")
    @CircuitBreaker(name = "transactionService", fallbackMethod = "blockTransactionFallback")
    @Retry(name = "transactionService")
    @Bulkhead(name = "transactionService", type = Bulkhead.Type.THREADPOOL)
    TransactionBlockResponse blockTransaction(@Valid @RequestBody TransactionBlockRequest request);

    /**
     * Unblock a transaction (false positive fraud detection)
     *
     * @param request Transaction unblock request
     * @return Status of unblock operation
     */
    @PostMapping("/api/v1/transactions/unblock")
    @CircuitBreaker(name = "transactionService")
    @Retry(name = "transactionService")
    TransactionBlockResponse unblockTransaction(@Valid @RequestBody TransactionUnblockRequest request);

    /**
     * Get transaction status
     *
     * @param transactionId Transaction identifier
     * @return Current transaction status
     */
    @GetMapping("/api/v1/transactions/{transactionId}/status")
    @CircuitBreaker(name = "transactionService")
    TransactionStatusResponse getTransactionStatus(@PathVariable("transactionId") String transactionId);

    /**
     * Reverse a completed transaction due to fraud
     *
     * FINANCIAL OPERATION - Must be atomic and audited
     *
     * @param transactionId Transaction to reverse
     * @param reason Fraud-related reversal reason
     * @return Reversal status
     */
    @PostMapping("/api/v1/transactions/{transactionId}/reverse")
    @CircuitBreaker(name = "transactionService")
    @Retry(name = "transactionService")
    TransactionBlockResponse reverseTransaction(
        @PathVariable("transactionId") String transactionId,
        @RequestParam("reason") String reason
    );

    /**
     * Freeze transaction for manual review
     *
     * @param transactionId Transaction to freeze
     * @param reviewReason Reason requiring manual review
     * @return Freeze status
     */
    @PostMapping("/api/v1/transactions/{transactionId}/freeze")
    @CircuitBreaker(name = "transactionService")
    TransactionBlockResponse freezeForReview(
        @PathVariable("transactionId") String transactionId,
        @RequestParam("reviewReason") String reviewReason
    );
}
