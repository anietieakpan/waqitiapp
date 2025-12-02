package com.waqiti.payment.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Feign Client for Wallet Service
 * Handles wallet-related operations
 */
@FeignClient(
    name = "wallet-service",
    path = "/api/v1/wallet",
    fallback = WalletServiceClientFallback.class
)
public interface WalletServiceClient {
    
    @PostMapping("/reserve")
    @CircuitBreaker(name = "wallet-service")
    @Retry(name = "wallet-service")
    Map<String, Object> reserveFunds(@RequestParam("walletId") String walletId, 
                                     @RequestParam("amount") BigDecimal amount,
                                     @RequestParam("currency") String currency,
                                     @RequestParam("referenceId") String referenceId);
    
    @PostMapping("/release")
    @CircuitBreaker(name = "wallet-service")
    @Retry(name = "wallet-service")
    Map<String, Object> releaseFunds(@RequestParam("walletId") String walletId,
                                      @RequestParam("reservationId") String reservationId);
    
    @PostMapping("/transfer")
    @CircuitBreaker(name = "wallet-service")
    @Retry(name = "wallet-service")
    Map<String, Object> transfer(@RequestParam("fromWalletId") String fromWalletId,
                                  @RequestParam("toWalletId") String toWalletId,
                                  @RequestParam("amount") BigDecimal amount,
                                  @RequestParam("currency") String currency,
                                  @RequestParam("transactionId") String transactionId);
    
    @GetMapping("/balance/{walletId}")
    @CircuitBreaker(name = "wallet-service")
    Map<String, Object> getBalance(@PathVariable("walletId") String walletId);
    
    @PostMapping("/debit")
    @CircuitBreaker(name = "wallet-service")
    @Retry(name = "wallet-service")
    Map<String, Object> debit(@RequestParam("walletId") String walletId,
                               @RequestParam("amount") BigDecimal amount,
                               @RequestParam("currency") String currency,
                               @RequestParam("transactionId") String transactionId);
    
    @PostMapping("/credit")
    @CircuitBreaker(name = "wallet-service")
    @Retry(name = "wallet-service")
    Map<String, Object> credit(@RequestParam("walletId") String walletId,
                                @RequestParam("amount") BigDecimal amount,
                                @RequestParam("currency") String currency,
                                @RequestParam("transactionId") String transactionId);
}