package com.waqiti.virtualcard.service;

import com.waqiti.virtualcard.client.WalletServiceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Wallet Service Wrapper
 *
 * Wraps WalletServiceClient with business logic and error handling
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletServiceClient walletServiceClient;

    /**
     * Get wallet balance for user in specified currency
     */
    public BigDecimal getBalance(String userId, String currency) {
        try {
            log.debug("Fetching wallet balance for user {} in currency {}", userId, currency);
            BigDecimal balance = walletServiceClient.getBalance(userId, currency);
            log.debug("Wallet balance retrieved: {} {}", balance, currency);
            return balance;
        } catch (Exception e) {
            log.error("Failed to get wallet balance for user {}", userId, e);
            // Throw exception to prevent operations with unknown balance
            throw new WalletServiceException("Unable to retrieve wallet balance", e);
        }
    }

    /**
     * Debit amount from user's wallet
     */
    public void debit(String userId, BigDecimal amount, String currency, String description, Map<String, Object> metadata) {
        try {
            log.info("Debiting {} {} from user {} wallet. Description: {}", amount, currency, userId, description);

            WalletServiceClient.WalletDebitRequest request = WalletServiceClient.WalletDebitRequest.builder()
                .amount(amount)
                .currency(currency)
                .description(description)
                .metadata(metadata)
                .idempotencyKey(generateIdempotencyKey(userId, amount, currency))
                .build();

            WalletServiceClient.WalletTransactionResponse response = walletServiceClient.debit(userId, request);

            if (!response.isSuccess()) {
                log.error("Wallet debit failed for user {}: {} - {}", userId, response.getErrorCode(), response.getMessage());
                throw new WalletDebitFailedException(response.getMessage(), response.getErrorCode());
            }

            log.info("Wallet debit successful. TransactionId: {}, New balance: {}",
                response.getTransactionId(), response.getNewBalance());

        } catch (WalletDebitFailedException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to debit wallet for user {}", userId, e);
            throw new WalletServiceException("Unable to debit wallet", e);
        }
    }

    /**
     * Credit amount to user's wallet
     */
    public void credit(String userId, BigDecimal amount, String currency, String description, Map<String, Object> metadata) {
        try {
            log.info("Crediting {} {} to user {} wallet. Description: {}", amount, currency, userId, description);

            WalletServiceClient.WalletCreditRequest request = WalletServiceClient.WalletCreditRequest.builder()
                .amount(amount)
                .currency(currency)
                .description(description)
                .metadata(metadata)
                .idempotencyKey(generateIdempotencyKey(userId, amount, currency))
                .build();

            WalletServiceClient.WalletTransactionResponse response = walletServiceClient.credit(userId, request);

            if (!response.isSuccess()) {
                log.error("Wallet credit failed for user {}: {} - {}", userId, response.getErrorCode(), response.getMessage());
                throw new WalletCreditFailedException(response.getMessage(), response.getErrorCode());
            }

            log.info("Wallet credit successful. TransactionId: {}, New balance: {}",
                response.getTransactionId(), response.getNewBalance());

        } catch (WalletCreditFailedException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to credit wallet for user {}", userId, e);
            throw new WalletServiceException("Unable to credit wallet", e);
        }
    }

    /**
     * Check if user has sufficient balance
     */
    public boolean hasSufficientBalance(String userId, BigDecimal amount, String currency) {
        try {
            log.debug("Checking sufficient balance for user {}: {} {}", userId, amount, currency);
            return walletServiceClient.hasSufficientBalance(userId, amount, currency);
        } catch (Exception e) {
            log.error("Failed to check wallet balance for user {}", userId, e);
            // Fail-safe: return false if unable to verify balance
            return false;
        }
    }

    /**
     * Generate idempotency key for wallet operations
     */
    private String generateIdempotencyKey(String userId, BigDecimal amount, String currency) {
        return String.format("card-service:%s:%s:%s:%s",
            userId, amount.toPlainString(), currency, UUID.randomUUID());
    }

    // Custom exceptions
    public static class WalletServiceException extends RuntimeException {
        public WalletServiceException(String message) {
            super(message);
        }

        public WalletServiceException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class WalletDebitFailedException extends RuntimeException {
        private final String errorCode;

        public WalletDebitFailedException(String message, String errorCode) {
            super(message);
            this.errorCode = errorCode;
        }

        public String getErrorCode() {
            return errorCode;
        }
    }

    public static class WalletCreditFailedException extends RuntimeException {
        private final String errorCode;

        public WalletCreditFailedException(String message, String errorCode) {
            super(message);
            this.errorCode = errorCode;
        }

        public String getErrorCode() {
            return errorCode;
        }
    }
}
