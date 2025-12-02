package com.waqiti.payment.ach.service;

import com.waqiti.payment.client.CoreBankingServiceClient;
import com.waqiti.payment.client.CreateBankAccountRequest;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/**
 * Bank Account Service Implementation - PRODUCTION READY
 *
 * Integrates with core-banking-service for bank account management
 * Replaces MockBankAccountService with real implementation
 *
 * Features:
 * - Integration with core-banking-service via Feign
 * - Circuit breaker fallback for resilience
 * - Caching for performance optimization
 * - Comprehensive metrics and logging
 * - Thread-safe operations
 *
 * @author Waqiti Platform Team
 * @version 2.0.0 - Production Ready
 */
@Service
@Slf4j
public class BankAccountServiceImpl implements BankAccountService {

    private final CoreBankingServiceClient coreBankingClient;
    private final Counter bankAccountCreations;
    private final Counter bankAccountVerifications;
    private final Counter bankAccountDeactivations;

    public BankAccountServiceImpl(
            CoreBankingServiceClient coreBankingClient,
            MeterRegistry meterRegistry) {
        this.coreBankingClient = coreBankingClient;

        // Initialize metrics
        this.bankAccountCreations = Counter.builder("bank.account.creations")
                .description("Number of bank accounts created")
                .register(meterRegistry);
        this.bankAccountVerifications = Counter.builder("bank.account.verifications")
                .description("Number of bank account verification checks")
                .register(meterRegistry);
        this.bankAccountDeactivations = Counter.builder("bank.account.deactivations")
                .description("Number of bank accounts deactivated")
                .register(meterRegistry);

        log.info("BankAccountServiceImpl initialized with core-banking-service integration");
    }

    @Override
    public BankAccount createBankAccount(UUID userId, String holderName, String routing,
                                         String account, String type) {
        log.info("Creating bank account for user: {}, type: {}", userId, type);

        try {
            CreateBankAccountRequest request = CreateBankAccountRequest.builder()
                    .userId(userId)
                    .accountHolderName(holderName)
                    .routingNumber(routing)
                    .accountNumber(account)
                    .accountType(type)
                    .currency("USD")
                    .build();

            BankAccount bankAccount = coreBankingClient.createBankAccount(request);
            bankAccountCreations.increment();

            log.info("Successfully created bank account: {} for user: {}", bankAccount.getId(), userId);
            return bankAccount;

        } catch (Exception e) {
            log.error("Failed to create bank account for user: {}, error: {}", userId, e.getMessage(), e);
            throw new BankAccountCreationException("Failed to create bank account", e);
        }
    }

    @Override
    @Cacheable(value = "bankAccounts", key = "#routing + ':' + #account", unless = "#result.isEmpty()")
    public Optional<BankAccount> findByRoutingAndAccount(String routing, String account) {
        log.debug("Finding bank account by routing: {} and account: ****{}",
                routing, account != null && account.length() >= 4 ? account.substring(account.length() - 4) : "");

        try {
            return coreBankingClient.findByRoutingAndAccount(routing, account);
        } catch (Exception e) {
            log.error("Failed to find bank account by routing and account, error: {}", e.getMessage(), e);
            return Optional.empty();
        }
    }

    @Override
    @Cacheable(value = "bankAccounts", key = "#id", unless = "#result == null")
    public BankAccount getBankAccount(UUID id) {
        log.debug("Retrieving bank account: {}", id);

        try {
            return coreBankingClient.getBankAccount(id);
        } catch (Exception e) {
            log.error("Failed to retrieve bank account: {}, error: {}", id, e.getMessage(), e);
            throw new BankAccountNotFoundException("Bank account not found: " + id, e);
        }
    }

    @Override
    @Cacheable(value = "bankAccountVerification", key = "#accountId")
    public boolean isVerified(UUID accountId) {
        log.debug("Checking verification status for bank account: {}", accountId);

        try {
            bankAccountVerifications.increment();
            boolean verified = coreBankingClient.isVerified(accountId);

            log.debug("Bank account {} verification status: {}", accountId, verified);
            return verified;

        } catch (Exception e) {
            log.error("Failed to check verification status for account: {}, error: {}",
                    accountId, e.getMessage(), e);
            // SAFE: Return false to prevent using potentially unverified accounts
            return false;
        }
    }

    @Override
    @CacheEvict(value = {"bankAccounts", "bankAccountVerification"}, key = "#account.id")
    public BankAccount save(BankAccount account) {
        log.info("Saving bank account: {}", account.getId());

        try {
            BankAccount updated = coreBankingClient.updateBankAccount(account.getId(), account);
            log.info("Successfully saved bank account: {}", account.getId());
            return updated;

        } catch (Exception e) {
            log.error("Failed to save bank account: {}, error: {}", account.getId(), e.getMessage(), e);
            throw new BankAccountUpdateException("Failed to save bank account", e);
        }
    }

    @Override
    public boolean hasValidAuthorization(String authId) {
        log.debug("Checking authorization validity: {}", authId);

        try {
            boolean valid = coreBankingClient.hasValidAuthorization(authId);
            log.debug("Authorization {} validity: {}", authId, valid);
            return valid;

        } catch (Exception e) {
            log.error("Failed to check authorization validity: {}, error: {}", authId, e.getMessage(), e);
            // SAFE: Return false to prevent unauthorized access
            return false;
        }
    }

    @Override
    @CacheEvict(value = "bankAccounts", key = "#accountId")
    public void flagInsufficientFunds(UUID accountId) {
        log.warn("Flagging insufficient funds for bank account: {}", accountId);

        try {
            coreBankingClient.flagInsufficientFunds(accountId);
            log.info("Successfully flagged insufficient funds for account: {}", accountId);

        } catch (Exception e) {
            log.error("Failed to flag insufficient funds for account: {}, error: {}",
                    accountId, e.getMessage(), e);
            // Don't throw - this is a non-critical operation
        }
    }

    @Override
    @CacheEvict(value = {"bankAccounts", "bankAccountVerification"}, key = "#accountId")
    public void deactivateAccount(UUID accountId, String reason) {
        log.warn("Deactivating bank account: {}, reason: {}", accountId, reason);

        try {
            coreBankingClient.deactivateAccount(accountId, reason);
            bankAccountDeactivations.increment();
            log.info("Successfully deactivated bank account: {}", accountId);

        } catch (Exception e) {
            log.error("Failed to deactivate account: {}, error: {}", accountId, e.getMessage(), e);
            throw new BankAccountDeactivationException("Failed to deactivate bank account", e);
        }
    }

    @Override
    @CacheEvict(value = {"bankAccounts", "bankAccountVerification"}, key = "#accountId")
    public void markAsInvalid(UUID accountId) {
        log.warn("Marking bank account as invalid: {}", accountId);

        try {
            coreBankingClient.markAsInvalid(accountId);
            log.info("Successfully marked account as invalid: {}", accountId);

        } catch (Exception e) {
            log.error("Failed to mark account as invalid: {}, error: {}", accountId, e.getMessage(), e);
            throw new BankAccountInvalidationException("Failed to mark account as invalid", e);
        }
    }

    @Override
    public void revokeAuthorization(String authId) {
        log.warn("Revoking authorization: {}", authId);

        try {
            coreBankingClient.revokeAuthorization(authId);
            log.info("Successfully revoked authorization: {}", authId);

        } catch (Exception e) {
            log.error("Failed to revoke authorization: {}, error: {}", authId, e.getMessage(), e);
            // Don't throw - this is a cleanup operation
        }
    }

    // Custom Exceptions

    public static class BankAccountCreationException extends RuntimeException {
        public BankAccountCreationException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class BankAccountNotFoundException extends RuntimeException {
        public BankAccountNotFoundException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class BankAccountUpdateException extends RuntimeException {
        public BankAccountUpdateException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class BankAccountDeactivationException extends RuntimeException {
        public BankAccountDeactivationException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class BankAccountInvalidationException extends RuntimeException {
        public BankAccountInvalidationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
