package com.waqiti.wallet.client;

import com.waqiti.wallet.dto.*;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Feign client for integrating with the core-banking-service.
 * 
 * This client provides access to core banking operations including:
 * - Financial account management
 * - Bank account operations 
 * - Balance and transaction management
 * - Chart of accounts integration
 * 
 * @author Principal Software Engineer
 * @since 1.0.0
 */
@FeignClient(
    name = "core-banking-service",
    path = "/api/v1",
    fallback = CoreBankingServiceClientFallback.class
)
public interface CoreBankingServiceClient {
    
    // Core Account Operations
    
    /**
     * Create a new financial account
     */
    @PostMapping("/accounts")
    @CircuitBreaker(name = "core-banking-service", fallbackMethod = "createAccountFallback")
    @Retry(name = "core-banking-service")
    @TimeLimiter(name = "core-banking-service")
    ResponseEntity<Map<String, Object>> createAccount(
        @RequestHeader("Authorization") String authorization,
        @Valid @RequestBody CoreBankingAccountRequest request
    );
    
    /**
     * Get account details
     */
    @GetMapping("/accounts/{accountId}")
    @CircuitBreaker(name = "core-banking-service", fallbackMethod = "getAccountFallback")
    @Retry(name = "core-banking-service")
    @TimeLimiter(name = "core-banking-service")
    ResponseEntity<Map<String, Object>> getAccount(
        @PathVariable String accountId,
        @RequestHeader("Authorization") String authorization
    );
    
    /**
     * Get account balance
     */
    @GetMapping("/accounts/{accountId}/balance")
    @CircuitBreaker(name = "core-banking-service", fallbackMethod = "getAccountBalanceFallback")
    @Retry(name = "core-banking-service")
    @TimeLimiter(name = "core-banking-service")
    ResponseEntity<Map<String, Object>> getAccountBalance(
        @PathVariable String accountId,
        @RequestHeader("Authorization") String authorization
    );
    
    /**
     * Update account balance
     */
    @PutMapping("/accounts/{accountId}/balance")
    @CircuitBreaker(name = "core-banking-service", fallbackMethod = "updateAccountBalanceFallback")
    @Retry(name = "core-banking-service")
    @TimeLimiter(name = "core-banking-service")
    ResponseEntity<Map<String, Object>> updateAccountBalance(
        @PathVariable String accountId,
        @RequestHeader("Authorization") String authorization,
        @Valid @RequestBody AccountBalanceUpdateRequest request
    );
    
    /**
     * Get user's accounts
     */
    @GetMapping("/accounts/user/{userId}")
    ResponseEntity<List<Map<String, Object>>> getUserAccounts(
        @PathVariable UUID userId,
        @RequestHeader("Authorization") String authorization
    );
    
    // Bank Account Operations
    
    /**
     * Create a new bank account
     */
    @PostMapping("/bank-accounts")
    ResponseEntity<Map<String, Object>> createBankAccount(
        @RequestHeader("Authorization") String authorization,
        @Valid @RequestBody CreateBankAccountRequest request
    );
    
    /**
     * Get bank account details
     */
    @GetMapping("/bank-accounts/{bankAccountId}")
    ResponseEntity<Map<String, Object>> getBankAccount(
        @PathVariable UUID bankAccountId,
        @RequestHeader("Authorization") String authorization
    );
    
    /**
     * Verify bank account with micro-deposits
     */
    @PostMapping("/bank-accounts/{bankAccountId}/verify")
    ResponseEntity<Map<String, Object>> verifyBankAccount(
        @PathVariable UUID bankAccountId,
        @RequestHeader("Authorization") String authorization,
        @Valid @RequestBody BankAccountVerificationRequest request
    );
    
    /**
     * Get user's bank accounts
     */
    @GetMapping("/bank-accounts/user/{userId}")
    ResponseEntity<List<Map<String, Object>>> getUserBankAccounts(
        @PathVariable UUID userId,
        @RequestHeader("Authorization") String authorization
    );
    
    /**
     * Update bank account status
     */
    @PutMapping("/bank-accounts/{bankAccountId}/status")
    ResponseEntity<Map<String, Object>> updateBankAccountStatus(
        @PathVariable UUID bankAccountId,
        @RequestHeader("Authorization") String authorization,
        @Valid @RequestBody BankAccountStatusUpdateRequest request
    );
    
    // Transaction Operations
    
    /**
     * Create transaction between accounts
     */
    @PostMapping("/transactions")
    ResponseEntity<Map<String, Object>> createTransaction(
        @RequestHeader("Authorization") String authorization,
        @Valid @RequestBody CreateTransactionRequest request
    );
    
    /**
     * Get account transactions
     */
    @GetMapping("/accounts/{accountId}/transactions")
    ResponseEntity<List<Map<String, Object>>> getAccountTransactions(
        @PathVariable String accountId,
        @RequestHeader("Authorization") String authorization,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "50") int size
    );
    
    /**
     * Get transaction details
     */
    @GetMapping("/transactions/{transactionId}")
    ResponseEntity<Map<String, Object>> getTransaction(
        @PathVariable String transactionId,
        @RequestHeader("Authorization") String authorization
    );
}