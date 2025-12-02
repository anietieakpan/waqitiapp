package com.waqiti.wallet.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Fallback implementation for CoreBankingServiceClient following
 * circuit breaker pattern for graceful degradation when core-banking-service is unavailable.
 * 
 * @author Principal Software Engineer
 * @since 1.0.0
 */
@Component
@Slf4j
public class CoreBankingServiceClientFallback implements CoreBankingServiceClient {
    
    private static final String SERVICE_UNAVAILABLE_ERROR = "Core banking service temporarily unavailable";
    private static final String FALLBACK_RESPONSE_KEY = "fallback";
    
    @Override
    public ResponseEntity<Map<String, Object>> createAccount(String authorization, CoreBankingAccountRequest request) {
        log.error("Core banking service unavailable - falling back for createAccount");
        return ResponseEntity.ok(Map.of(
            "success", false,
            "error", SERVICE_UNAVAILABLE_ERROR,
            "errorCode", "CORE_BANKING_UNAVAILABLE",
            FALLBACK_RESPONSE_KEY, true
        ));
    }
    
    @Override
    public ResponseEntity<Map<String, Object>> getAccount(String accountId, String authorization) {
        log.error("Core banking service unavailable - falling back for getAccount: {}", accountId);
        return ResponseEntity.ok(Map.of(
            "error", SERVICE_UNAVAILABLE_ERROR,
            "errorCode", "CORE_BANKING_UNAVAILABLE",
            FALLBACK_RESPONSE_KEY, true
        ));
    }
    
    @Override
    public ResponseEntity<Map<String, Object>> getAccountBalance(String accountId, String authorization) {
        log.error("Core banking service unavailable - falling back for getAccountBalance: {}", accountId);
        return ResponseEntity.ok(Map.of(
            "error", SERVICE_UNAVAILABLE_ERROR,
            "errorCode", "CORE_BANKING_UNAVAILABLE",
            "balance", "0.00",
            FALLBACK_RESPONSE_KEY, true
        ));
    }
    
    @Override
    public ResponseEntity<Map<String, Object>> updateAccountBalance(String accountId, String authorization, 
                                                                   AccountBalanceUpdateRequest request) {
        log.error("Core banking service unavailable - falling back for updateAccountBalance: {}", accountId);
        return ResponseEntity.ok(Map.of(
            "success", false,
            "error", SERVICE_UNAVAILABLE_ERROR,
            "errorCode", "CORE_BANKING_UNAVAILABLE",
            FALLBACK_RESPONSE_KEY, true
        ));
    }
    
    @Override
    public ResponseEntity<List<Map<String, Object>>> getUserAccounts(UUID userId, String authorization) {
        log.error("Core banking service unavailable - falling back for getUserAccounts: {}", userId);
        return ResponseEntity.ok(List.of());
    }
    
    @Override
    public ResponseEntity<Map<String, Object>> createBankAccount(String authorization, CreateBankAccountRequest request) {
        log.error("Core banking service unavailable - falling back for createBankAccount");
        return ResponseEntity.ok(Map.of(
            "success", false,
            "error", SERVICE_UNAVAILABLE_ERROR,
            "errorCode", "CORE_BANKING_UNAVAILABLE",
            FALLBACK_RESPONSE_KEY, true
        ));
    }
    
    @Override
    public ResponseEntity<Map<String, Object>> getBankAccount(UUID bankAccountId, String authorization) {
        log.error("Core banking service unavailable - falling back for getBankAccount: {}", bankAccountId);
        return ResponseEntity.ok(Map.of(
            "error", SERVICE_UNAVAILABLE_ERROR,
            "errorCode", "CORE_BANKING_UNAVAILABLE",
            FALLBACK_RESPONSE_KEY, true
        ));
    }
    
    @Override
    public ResponseEntity<Map<String, Object>> verifyBankAccount(UUID bankAccountId, String authorization, 
                                                                BankAccountVerificationRequest request) {
        log.error("Core banking service unavailable - falling back for verifyBankAccount: {}", bankAccountId);
        return ResponseEntity.ok(Map.of(
            "success", false,
            "error", SERVICE_UNAVAILABLE_ERROR,
            "errorCode", "CORE_BANKING_UNAVAILABLE",
            FALLBACK_RESPONSE_KEY, true
        ));
    }
    
    @Override
    public ResponseEntity<List<Map<String, Object>>> getUserBankAccounts(UUID userId, String authorization) {
        log.error("Core banking service unavailable - falling back for getUserBankAccounts: {}", userId);
        return ResponseEntity.ok(List.of());
    }
    
    @Override
    public ResponseEntity<Map<String, Object>> updateBankAccountStatus(UUID bankAccountId, String authorization, 
                                                                      BankAccountStatusUpdateRequest request) {
        log.error("Core banking service unavailable - falling back for updateBankAccountStatus: {}", bankAccountId);
        return ResponseEntity.ok(Map.of(
            "success", false,
            "error", SERVICE_UNAVAILABLE_ERROR,
            "errorCode", "CORE_BANKING_UNAVAILABLE",
            FALLBACK_RESPONSE_KEY, true
        ));
    }
    
    @Override
    public ResponseEntity<Map<String, Object>> createTransaction(String authorization, CreateTransactionRequest request) {
        log.error("Core banking service unavailable - falling back for createTransaction");
        return ResponseEntity.ok(Map.of(
            "success", false,
            "error", SERVICE_UNAVAILABLE_ERROR,
            "errorCode", "CORE_BANKING_UNAVAILABLE",
            FALLBACK_RESPONSE_KEY, true
        ));
    }
    
    @Override
    public ResponseEntity<List<Map<String, Object>>> getAccountTransactions(String accountId, String authorization, 
                                                                           int page, int size) {
        log.error("Core banking service unavailable - falling back for getAccountTransactions: {}", accountId);
        return ResponseEntity.ok(List.of());
    }
    
    @Override
    public ResponseEntity<Map<String, Object>> getTransaction(String transactionId, String authorization) {
        log.error("Core banking service unavailable - falling back for getTransaction: {}", transactionId);
        return ResponseEntity.ok(Map.of(
            "error", SERVICE_UNAVAILABLE_ERROR,
            "errorCode", "CORE_BANKING_UNAVAILABLE",
            FALLBACK_RESPONSE_KEY, true
        ));
    }
}