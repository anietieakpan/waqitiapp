package com.waqiti.wallet.client;

import com.waqiti.wallet.dto.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Fallback implementation for BankIntegrationServiceClient following
 * circuit breaker pattern for graceful degradation when bank-integration-service is unavailable.
 * 
 * @author Principal Software Engineer
 * @since 1.0.0
 */
@Component
@Slf4j
public class BankIntegrationServiceClientFallback implements BankIntegrationServiceClient {
    
    private static final String SERVICE_UNAVAILABLE_ERROR = "Bank integration service temporarily unavailable";
    
    @Override
    public ResponseEntity<Map<String, Object>> linkBankAccount(LinkBankAccountRequest request) {
        log.error("Bank integration service unavailable - falling back for linkBankAccount");
        return ResponseEntity.ok(Map.of(
            "success", false,
            "error", SERVICE_UNAVAILABLE_ERROR,
            "errorCode", "SERVICE_UNAVAILABLE",
            "fallback", true
        ));
    }
    
    @Override
    public ResponseEntity<Map<String, Object>> verifyBankAccount(VerifyBankAccountRequest request) {
        log.error("Bank integration service unavailable - falling back for verifyBankAccount");
        return ResponseEntity.ok(Map.of(
            "success", false,
            "error", SERVICE_UNAVAILABLE_ERROR,
            "errorCode", "SERVICE_UNAVAILABLE",
            "fallback", true
        ));
    }
    
    @Override
    public ResponseEntity<List<Map<String, Object>>> getUserBankAccounts(String userId) {
        log.error("Bank integration service unavailable - falling back for getUserBankAccounts");
        return ResponseEntity.ok(List.of());
    }
    
    @Override
    public ResponseEntity<Map<String, Object>> getAccountBalance(String accountId) {
        log.error("Bank integration service unavailable - falling back for getAccountBalance");
        return ResponseEntity.ok(Map.of(
            "error", SERVICE_UNAVAILABLE_ERROR,
            "errorCode", "SERVICE_UNAVAILABLE",
            "fallback", true
        ));
    }
    
    @Override
    public ResponseEntity<List<Map<String, Object>>> getAccountTransactions(String accountId, int days, int page, int size) {
        log.error("Bank integration service unavailable - falling back for getAccountTransactions");
        return ResponseEntity.ok(List.of());
    }
    
    @Override
    public ResponseEntity<Map<String, Object>> initiateTransfer(String accountId, BankTransferRequest request) {
        log.error("Bank integration service unavailable - falling back for initiateTransfer");
        return ResponseEntity.ok(Map.of(
            "success", false,
            "error", SERVICE_UNAVAILABLE_ERROR,
            "errorCode", "SERVICE_UNAVAILABLE",
            "fallback", true
        ));
    }
    
    @Override
    public ResponseEntity<Map<String, Object>> setPrimaryAccount(String accountId, SetPrimaryAccountRequest request) {
        log.error("Bank integration service unavailable - falling back for setPrimaryAccount");
        return ResponseEntity.ok(Map.of(
            "success", false,
            "error", SERVICE_UNAVAILABLE_ERROR,
            "errorCode", "SERVICE_UNAVAILABLE",
            "fallback", true
        ));
    }
    
    @Override
    public ResponseEntity<Map<String, Object>> unlinkBankAccount(String accountId, UnlinkBankAccountRequest request) {
        log.error("Bank integration service unavailable - falling back for unlinkBankAccount");
        return ResponseEntity.ok(Map.of(
            "success", false,
            "error", SERVICE_UNAVAILABLE_ERROR,
            "errorCode", "SERVICE_UNAVAILABLE", 
            "fallback", true
        ));
    }
    
    @Override
    public ResponseEntity<List<Map<String, Object>>> getSupportedBanks(String country) {
        log.error("Bank integration service unavailable - falling back for getSupportedBanks");
        return ResponseEntity.ok(List.of());
    }
    
    @Override
    public ResponseEntity<Map<String, Object>> startInstantVerification(InstantVerificationRequest request) {
        log.error("Bank integration service unavailable - falling back for startInstantVerification");
        return ResponseEntity.ok(Map.of(
            "success", false,
            "error", SERVICE_UNAVAILABLE_ERROR,
            "errorCode", "SERVICE_UNAVAILABLE",
            "fallback", true
        ));
    }
    
    @Override
    public ResponseEntity<Map<String, Object>> getVerificationStatus(String verificationId) {
        log.error("Bank integration service unavailable - falling back for getVerificationStatus");
        return ResponseEntity.ok(Map.of(
            "error", SERVICE_UNAVAILABLE_ERROR,
            "errorCode", "SERVICE_UNAVAILABLE", 
            "fallback", true
        ));
    }
}