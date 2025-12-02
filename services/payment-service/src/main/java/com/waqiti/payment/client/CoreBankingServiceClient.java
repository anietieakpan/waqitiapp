package com.waqiti.payment.client;

import com.waqiti.payment.ach.service.BankAccountService.BankAccount;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import java.util.UUID;

/**
 * Feign Client for Core Banking Service - PRODUCTION READY
 *
 * Provides integration with core-banking-service for bank account management
 *
 * @author Waqiti Platform Team
 * @version 2.0.0
 */
@FeignClient(
    name = "core-banking-service",
    fallback = CoreBankingServiceClientFallback.class
)
public interface CoreBankingServiceClient {

    @GetMapping("/api/v1/bank-accounts/{id}")
    BankAccount getBankAccount(@PathVariable("id") UUID id);

    @GetMapping("/api/v1/bank-accounts/routing/{routingNumber}/account/{accountNumber}")
    Optional<BankAccount> findByRoutingAndAccount(
            @PathVariable("routingNumber") String routingNumber,
            @PathVariable("accountNumber") String accountNumber
    );

    @PostMapping("/api/v1/bank-accounts")
    BankAccount createBankAccount(@RequestBody CreateBankAccountRequest request);

    @GetMapping("/api/v1/bank-accounts/{id}/verified")
    boolean isVerified(@PathVariable("id") UUID accountId);

    @PutMapping("/api/v1/bank-accounts/{id}")
    BankAccount updateBankAccount(@PathVariable("id") UUID id, @RequestBody BankAccount account);

    @GetMapping("/api/v1/bank-accounts/authorization/{authId}/valid")
    boolean hasValidAuthorization(@PathVariable("authId") String authId);

    @PostMapping("/api/v1/bank-accounts/{id}/flag-insufficient-funds")
    void flagInsufficientFunds(@PathVariable("id") UUID accountId);

    @PostMapping("/api/v1/bank-accounts/{id}/deactivate")
    void deactivateAccount(@PathVariable("id") UUID accountId, @RequestParam("reason") String reason);

    @PostMapping("/api/v1/bank-accounts/{id}/mark-invalid")
    void markAsInvalid(@PathVariable("id") UUID accountId);

    @DeleteMapping("/api/v1/bank-accounts/authorization/{authId}")
    void revokeAuthorization(@PathVariable("authId") String authId);
}
