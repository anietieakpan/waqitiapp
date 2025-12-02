package com.waqiti.wallet.client;

import com.waqiti.wallet.dto.*;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;

/**
 * Feign client for integrating with the existing bank-integration-service.
 * 
 * This client follows the exact API contract defined in BankAccountController
 * of the bank-integration-service, ensuring proper microservice integration.
 * 
 * @author Principal Software Engineer
 * @since 1.0.0
 */
@FeignClient(
    name = "bank-integration-service", 
    path = "/api/v1/banking/accounts",
    fallback = BankIntegrationServiceClientFallback.class
)
public interface BankIntegrationServiceClient {
    
    /**
     * Link a new bank account via bank-integration-service
     */
    @PostMapping("/link")
    ResponseEntity<Map<String, Object>> linkBankAccount(@RequestBody @Valid LinkBankAccountRequest request);
    
    /**
     * Verify bank account using micro-deposits
     */
    @PostMapping("/verify") 
    ResponseEntity<Map<String, Object>> verifyBankAccount(@RequestBody @Valid VerifyBankAccountRequest request);
    
    /**
     * Get user's linked bank accounts
     */
    @GetMapping("/{userId}")
    ResponseEntity<List<Map<String, Object>>> getUserBankAccounts(@PathVariable String userId);
    
    /**
     * Get account balance from external bank
     */
    @GetMapping("/{accountId}/balance")
    ResponseEntity<Map<String, Object>> getAccountBalance(@PathVariable String accountId);
    
    /**
     * Get account transactions
     */
    @GetMapping("/{accountId}/transactions")
    ResponseEntity<List<Map<String, Object>>> getAccountTransactions(
        @PathVariable String accountId,
        @RequestParam(defaultValue = "30") int days,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "50") int size
    );
    
    /**
     * Initiate bank transfer
     */
    @PostMapping("/{accountId}/transfer")
    ResponseEntity<Map<String, Object>> initiateTransfer(
        @PathVariable String accountId,
        @RequestBody @Valid BankTransferRequest request
    );
    
    /**
     * Set primary bank account
     */
    @PostMapping("/{accountId}/set-primary")
    ResponseEntity<Map<String, Object>> setPrimaryAccount(
        @PathVariable String accountId,
        @RequestBody @Valid SetPrimaryAccountRequest request
    );
    
    /**
     * Unlink bank account
     */
    @DeleteMapping("/{accountId}")
    ResponseEntity<Map<String, Object>> unlinkBankAccount(
        @PathVariable String accountId,
        @RequestBody @Valid UnlinkBankAccountRequest request
    );
    
    /**
     * Get supported banks
     */
    @GetMapping("/supported-banks")
    ResponseEntity<List<Map<String, Object>>> getSupportedBanks(@RequestParam(required = false) String country);
    
    /**
     * Start instant verification
     */
    @PostMapping("/instant-verification")
    ResponseEntity<Map<String, Object>> startInstantVerification(@RequestBody @Valid InstantVerificationRequest request);
    
    /**
     * Check verification status
     */
    @GetMapping("/verification/{verificationId}/status")
    ResponseEntity<Map<String, Object>> getVerificationStatus(@PathVariable String verificationId);
}