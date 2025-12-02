package com.waqiti.payment.client;

import com.waqiti.payment.client.dto.*;
import com.waqiti.payment.client.fallback.WalletServiceFallback;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Unified Wallet Service Client
 * 
 * Enterprise-grade Feign client for wallet service operations with comprehensive
 * resilience patterns. This replaces all duplicate WalletServiceClient implementations.
 * 
 * @version 3.0.0
 * @since 2025-01-15
 */
@FeignClient(
    name = "wallet-service",
    path = "/api/v1/wallets",
    fallback = WalletServiceFallback.class,
    configuration = WalletServiceClientConfiguration.class
)
public interface UnifiedWalletServiceClient {
    
    // =====================================================
    // CORE WALLET OPERATIONS
    // =====================================================
    
    @GetMapping("/{walletId}")
    @CircuitBreaker(name = "wallet-service")
    @Retry(name = "wallet-service")
    ResponseEntity<WalletResponse> getWallet(@PathVariable("walletId") UUID walletId);
    
    @GetMapping("/user/{userId}")
    @CircuitBreaker(name = "wallet-service")
    @Retry(name = "wallet-service")
    ResponseEntity<WalletResponse> getWalletByUserId(@PathVariable("userId") UUID userId);
    
    @GetMapping("/{walletId}/balance")
    @CircuitBreaker(name = "wallet-service")
    @Retry(name = "wallet-service")
    ResponseEntity<BalanceResponse> getBalance(@PathVariable("walletId") UUID walletId);
    
    @PostMapping("/{walletId}/debit")
    @CircuitBreaker(name = "wallet-service")
    @Retry(name = "wallet-service")
    @RateLimiter(name = "wallet-transaction")
    ResponseEntity<TransactionResponse> debitWallet(
        @PathVariable("walletId") UUID walletId,
        @Valid @RequestBody DebitRequest request
    );
    
    @PostMapping("/{walletId}/credit")
    @CircuitBreaker(name = "wallet-service")
    @Retry(name = "wallet-service")
    @RateLimiter(name = "wallet-transaction")
    ResponseEntity<TransactionResponse> creditWallet(
        @PathVariable("walletId") UUID walletId,
        @Valid @RequestBody CreditRequest request
    );
    
    @PostMapping("/transfer")
    @CircuitBreaker(name = "wallet-service")
    @Retry(name = "wallet-service")
    @RateLimiter(name = "wallet-transfer")
    ResponseEntity<TransferResponse> transfer(@Valid @RequestBody TransferRequest request);
    
    @PostMapping("/{walletId}/hold")
    @CircuitBreaker(name = "wallet-service")
    @Retry(name = "wallet-service")
    ResponseEntity<HoldResponse> placeHold(
        @PathVariable("walletId") UUID walletId,
        @Valid @RequestBody HoldRequest request
    );
    
    @DeleteMapping("/{walletId}/hold/{holdId}")
    @CircuitBreaker(name = "wallet-service")
    @Retry(name = "wallet-service")
    ResponseEntity<Void> releaseHold(
        @PathVariable("walletId") UUID walletId,
        @PathVariable("holdId") String holdId
    );
    
    @GetMapping("/{walletId}/validate/balance")
    @CircuitBreaker(name = "wallet-service")
    @Retry(name = "wallet-service")
    ResponseEntity<ValidationResponse> validateBalance(
        @PathVariable("walletId") UUID walletId,
        @RequestParam("amount") BigDecimal amount,
        @RequestParam("currency") String currency
    );
    
    @GetMapping("/{walletId}/transactions")
    @CircuitBreaker(name = "wallet-service")
    @Retry(name = "wallet-service")
    ResponseEntity<List<TransactionResponse>> getTransactionHistory(
        @PathVariable("walletId") UUID walletId,
        @RequestParam(value = "page", defaultValue = "0") int page,
        @RequestParam(value = "size", defaultValue = "20") int size
    );
}