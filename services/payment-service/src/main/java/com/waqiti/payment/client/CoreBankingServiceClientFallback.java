package com.waqiti.payment.client;

import com.waqiti.payment.ach.service.BankAccountService.BankAccount;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Fallback implementation for Core Banking Service Client - PRODUCTION READY
 *
 * Provides circuit breaker fallback behavior when core-banking-service is unavailable
 *
 * @author Waqiti Platform Team
 * @version 2.0.0
 */
@Component
@Slf4j
public class CoreBankingServiceClientFallback implements CoreBankingServiceClient {

    @Override
    public BankAccount getBankAccount(UUID id) {
        log.error("FALLBACK: Core banking service unavailable - cannot retrieve bank account: {}", id);
        throw new ServiceUnavailableException("Core banking service is currently unavailable");
    }

    @Override
    public Optional<BankAccount> findByRoutingAndAccount(String routingNumber, String accountNumber) {
        log.error("FALLBACK: Core banking service unavailable - cannot find bank account");
        return Optional.empty();
    }

    @Override
    public BankAccount createBankAccount(CreateBankAccountRequest request) {
        log.error("FALLBACK: Core banking service unavailable - cannot create bank account for user: {}",
                request != null ? request.getUserId() : "unknown");
        throw new ServiceUnavailableException("Core banking service is currently unavailable");
    }

    @Override
    public boolean isVerified(UUID accountId) {
        log.error("FALLBACK: Core banking service unavailable - cannot verify account status: {}", accountId);
        // SAFE: Return false to prevent unverified account usage
        return false;
    }

    @Override
    public BankAccount updateBankAccount(UUID id, BankAccount account) {
        log.error("FALLBACK: Core banking service unavailable - cannot update bank account: {}", id);
        throw new ServiceUnavailableException("Core banking service is currently unavailable");
    }

    @Override
    public boolean hasValidAuthorization(String authId) {
        log.error("FALLBACK: Core banking service unavailable - cannot check authorization: {}", authId);
        // SAFE: Return false to prevent unauthorized access
        return false;
    }

    @Override
    public void flagInsufficientFunds(UUID accountId) {
        log.error("FALLBACK: Core banking service unavailable - cannot flag insufficient funds: {}", accountId);
        // Log for manual review but don't throw - this is a non-critical operation
    }

    @Override
    public void deactivateAccount(UUID accountId, String reason) {
        log.error("FALLBACK: Core banking service unavailable - cannot deactivate account: {}, reason: {}",
                accountId, reason);
        throw new ServiceUnavailableException("Core banking service is currently unavailable");
    }

    @Override
    public void markAsInvalid(UUID accountId) {
        log.error("FALLBACK: Core banking service unavailable - cannot mark account as invalid: {}", accountId);
        throw new ServiceUnavailableException("Core banking service is currently unavailable");
    }

    @Override
    public void revokeAuthorization(String authId) {
        log.error("FALLBACK: Core banking service unavailable - cannot revoke authorization: {}", authId);
        // Log for manual review but don't throw - this is a cleanup operation
    }

    /**
     * Service Unavailable Exception
     */
    public static class ServiceUnavailableException extends RuntimeException {
        public ServiceUnavailableException(String message) {
            super(message);
        }
    }
}
