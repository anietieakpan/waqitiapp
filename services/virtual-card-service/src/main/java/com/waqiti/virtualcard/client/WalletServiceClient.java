package com.waqiti.virtualcard.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Feign Client for Wallet Service
 *
 * Provides wallet operations with:
 * - Circuit breaker protection
 * - Automatic retries for transient failures
 * - Fallback methods for graceful degradation
 * - Request/response logging
 * - Timeout configuration
 */
@FeignClient(
    name = "wallet-service",
    url = "${services.wallet-service.url:http://wallet-service:8082}",
    fallback = WalletServiceClientFallback.class,
    configuration = FeignClientConfiguration.class
)
public interface WalletServiceClient {

    /**
     * Get wallet balance for a user in specific currency
     *
     * @param userId User identifier
     * @param currency Currency code (USD, EUR, etc.)
     * @return Current balance
     */
    @GetMapping("/api/v1/wallets/{userId}/balance")
    @CircuitBreaker(name = "wallet-service", fallbackMethod = "getBalanceFallback")
    @Retry(name = "wallet-service")
    BigDecimal getBalance(
        @PathVariable("userId") String userId,
        @RequestParam("currency") String currency
    );

    /**
     * Debit amount from user's wallet
     *
     * @param userId User identifier
     * @param request Debit request
     * @return Transaction result
     */
    @PostMapping("/api/v1/wallets/{userId}/debit")
    @CircuitBreaker(name = "wallet-service", fallbackMethod = "debitFallback")
    @Retry(name = "wallet-service")
    WalletTransactionResponse debit(
        @PathVariable("userId") String userId,
        @RequestBody WalletDebitRequest request
    );

    /**
     * Credit amount to user's wallet
     *
     * @param userId User identifier
     * @param request Credit request
     * @return Transaction result
     */
    @PostMapping("/api/v1/wallets/{userId}/credit")
    @CircuitBreaker(name = "wallet-service", fallbackMethod = "creditFallback")
    @Retry(name = "wallet-service")
    WalletTransactionResponse credit(
        @PathVariable("userId") String userId,
        @RequestBody WalletCreditRequest request
    );

    /**
     * Check if user has sufficient balance
     *
     * @param userId User identifier
     * @param amount Amount to check
     * @param currency Currency code
     * @return true if sufficient balance exists
     */
    @GetMapping("/api/v1/wallets/{userId}/check-balance")
    @CircuitBreaker(name = "wallet-service", fallbackMethod = "hasSufficientBalanceFallback")
    @Retry(name = "wallet-service")
    boolean hasSufficientBalance(
        @PathVariable("userId") String userId,
        @RequestParam("amount") BigDecimal amount,
        @RequestParam("currency") String currency
    );

    /**
     * Get wallet details
     *
     * @param userId User identifier
     * @return Wallet details
     */
    @GetMapping("/api/v1/wallets/{userId}")
    @CircuitBreaker(name = "wallet-service", fallbackMethod = "getWalletDetailsFallback")
    @Retry(name = "wallet-service")
    WalletDetails getWalletDetails(@PathVariable("userId") String userId);

    /**
     * Reserve funds for pending transaction
     *
     * @param userId User identifier
     * @param request Reserve request
     * @return Reservation result
     */
    @PostMapping("/api/v1/wallets/{userId}/reserve")
    @CircuitBreaker(name = "wallet-service", fallbackMethod = "reserveFundsFallback")
    @Retry(name = "wallet-service")
    FundReservationResponse reserveFunds(
        @PathVariable("userId") String userId,
        @RequestBody FundReservationRequest request
    );

    /**
     * Release previously reserved funds
     *
     * @param userId User identifier
     * @param reservationId Reservation identifier
     */
    @DeleteMapping("/api/v1/wallets/{userId}/reserve/{reservationId}")
    @CircuitBreaker(name = "wallet-service")
    void releaseReservedFunds(
        @PathVariable("userId") String userId,
        @PathVariable("reservationId") String reservationId
    );

    // DTOs for wallet operations

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    class WalletDebitRequest {
        private BigDecimal amount;
        private String currency;
        private String description;
        private Map<String, Object> metadata;
        private String idempotencyKey;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    class WalletCreditRequest {
        private BigDecimal amount;
        private String currency;
        private String description;
        private Map<String, Object> metadata;
        private String idempotencyKey;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    class WalletTransactionResponse {
        private String transactionId;
        private boolean success;
        private BigDecimal newBalance;
        private String message;
        private String errorCode;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    class WalletDetails {
        private String walletId;
        private String userId;
        private BigDecimal balance;
        private String currency;
        private String status;
        private java.time.Instant createdAt;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    class FundReservationRequest {
        private BigDecimal amount;
        private String currency;
        private String purpose;
        private Long expirySeconds;
        private Map<String, Object> metadata;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    class FundReservationResponse {
        private String reservationId;
        private boolean success;
        private BigDecimal reservedAmount;
        private java.time.Instant expiresAt;
        private String errorCode;
    }
}
