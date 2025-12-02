package com.waqiti.corebanking.test.fixtures;

import com.waqiti.corebanking.domain.Transaction;
import com.waqiti.corebanking.domain.Transaction.TransactionStatus;
import com.waqiti.corebanking.domain.Transaction.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Test fixtures for Transaction entities
 *
 * Provides builder pattern for creating test transactions with sensible defaults.
 * All financial amounts use proper BigDecimal precision.
 */
public class TransactionFixtures {

    /**
     * Creates a standard completed P2P transfer
     */
    public static Transaction createCompletedTransfer() {
        return Transaction.builder()
                .id(UUID.randomUUID())
                .transactionNumber(generateTransactionNumber())
                .idempotencyKey(UUID.randomUUID().toString())
                .transactionType(TransactionType.P2P_TRANSFER)
                .status(TransactionStatus.COMPLETED)
                .sourceAccountId(UUID.randomUUID())
                .targetAccountId(UUID.randomUUID())
                .amount(new BigDecimal("100.0000"))
                .currency("USD")
                .feeAmount(new BigDecimal("1.0000"))
                .description("Test P2P transfer")
                .channel("WEB")
                .riskScore(10)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .completedAt(LocalDateTime.now())
                .settlementDate(LocalDate.now())
                .valueDate(LocalDate.now())
                .retryCount(0)
                .maxRetryAttempts(3)
                .version(0)
                .build();
    }

    /**
     * Creates a pending transaction
     */
    public static Transaction createPendingTransaction() {
        return Transaction.builder()
                .id(UUID.randomUUID())
                .transactionNumber(generateTransactionNumber())
                .idempotencyKey(UUID.randomUUID().toString())
                .transactionType(TransactionType.P2P_TRANSFER)
                .status(TransactionStatus.PENDING)
                .sourceAccountId(UUID.randomUUID())
                .targetAccountId(UUID.randomUUID())
                .amount(new BigDecimal("250.0000"))
                .currency("USD")
                .feeAmount(new BigDecimal("2.5000"))
                .description("Pending transfer")
                .channel("MOBILE")
                .riskScore(15)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .retryCount(0)
                .maxRetryAttempts(3)
                .version(0)
                .build();
    }

    /**
     * Creates a failed transaction
     */
    public static Transaction createFailedTransaction() {
        return Transaction.builder()
                .id(UUID.randomUUID())
                .transactionNumber(generateTransactionNumber())
                .idempotencyKey(UUID.randomUUID().toString())
                .transactionType(TransactionType.P2P_TRANSFER)
                .status(TransactionStatus.FAILED)
                .sourceAccountId(UUID.randomUUID())
                .targetAccountId(UUID.randomUUID())
                .amount(new BigDecimal("500.0000"))
                .currency("USD")
                .feeAmount(new BigDecimal("5.0000"))
                .description("Failed transfer")
                .failureReason("Insufficient funds")
                .channel("API")
                .riskScore(25)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .failedAt(LocalDateTime.now())
                .retryCount(3)
                .maxRetryAttempts(3)
                .version(0)
                .build();
    }

    /**
     * Creates a reversed transaction
     */
    public static Transaction createReversedTransaction() {
        return Transaction.builder()
                .id(UUID.randomUUID())
                .transactionNumber(generateTransactionNumber())
                .idempotencyKey(UUID.randomUUID().toString())
                .transactionType(TransactionType.P2P_TRANSFER)
                .status(TransactionStatus.REVERSED)
                .sourceAccountId(UUID.randomUUID())
                .targetAccountId(UUID.randomUUID())
                .amount(new BigDecimal("150.0000"))
                .currency("USD")
                .feeAmount(new BigDecimal("1.5000"))
                .description("Reversed transfer")
                .reversalTransactionId(UUID.randomUUID())
                .channel("WEB")
                .riskScore(5)
                .createdAt(LocalDateTime.now().minusDays(5))
                .updatedAt(LocalDateTime.now())
                .completedAt(LocalDateTime.now().minusDays(5))
                .settlementDate(LocalDate.now().minusDays(5))
                .valueDate(LocalDate.now().minusDays(5))
                .retryCount(0)
                .maxRetryAttempts(3)
                .version(0)
                .build();
    }

    /**
     * Creates a transaction requiring approval
     */
    public static Transaction createTransactionRequiringApproval() {
        return Transaction.builder()
                .id(UUID.randomUUID())
                .transactionNumber(generateTransactionNumber())
                .idempotencyKey(UUID.randomUUID().toString())
                .transactionType(TransactionType.WIRE_TRANSFER)
                .status(TransactionStatus.REQUIRES_APPROVAL)
                .sourceAccountId(UUID.randomUUID())
                .targetAccountId(UUID.randomUUID())
                .amount(new BigDecimal("50000.0000")) // High value
                .currency("USD")
                .feeAmount(new BigDecimal("25.0000"))
                .description("High value transfer requiring approval")
                .channel("WEB")
                .riskScore(60)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .retryCount(0)
                .maxRetryAttempts(3)
                .version(0)
                .build();
    }

    /**
     * Creates a transaction on compliance hold
     */
    public static Transaction createComplianceHoldTransaction() {
        return Transaction.builder()
                .id(UUID.randomUUID())
                .transactionNumber(generateTransactionNumber())
                .idempotencyKey(UUID.randomUUID().toString())
                .transactionType(TransactionType.INTERNATIONAL_TRANSFER)
                .status(TransactionStatus.COMPLIANCE_HOLD)
                .sourceAccountId(UUID.randomUUID())
                .targetAccountId(UUID.randomUUID())
                .amount(new BigDecimal("15000.0000"))
                .currency("USD")
                .feeAmount(new BigDecimal("150.0000"))
                .description("International transfer on compliance hold")
                .complianceCheckId(UUID.randomUUID())
                .channel("WEB")
                .riskScore(70)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .retryCount(0)
                .maxRetryAttempts(3)
                .version(0)
                .build();
    }

    /**
     * Creates a fee transaction
     */
    public static Transaction createFeeTransaction() {
        return Transaction.builder()
                .id(UUID.randomUUID())
                .transactionNumber(generateTransactionNumber())
                .idempotencyKey(UUID.randomUUID().toString())
                .transactionType(TransactionType.FEE)
                .status(TransactionStatus.COMPLETED)
                .sourceAccountId(UUID.randomUUID())
                .targetAccountId(UUID.randomUUID()) // Fee collection account
                .amount(new BigDecimal("5.0000"))
                .currency("USD")
                .feeAmount(BigDecimal.ZERO)
                .description("Transaction processing fee")
                .channel("SYSTEM")
                .riskScore(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .completedAt(LocalDateTime.now())
                .settlementDate(LocalDate.now())
                .valueDate(LocalDate.now())
                .retryCount(0)
                .maxRetryAttempts(3)
                .version(0)
                .build();
    }

    /**
     * Creates an interest credit transaction
     */
    public static Transaction createInterestTransaction() {
        return Transaction.builder()
                .id(UUID.randomUUID())
                .transactionNumber(generateTransactionNumber())
                .idempotencyKey(UUID.randomUUID().toString())
                .transactionType(TransactionType.INTEREST_CREDIT)
                .status(TransactionStatus.COMPLETED)
                .sourceAccountId(UUID.randomUUID()) // System account
                .targetAccountId(UUID.randomUUID()) // User savings account
                .amount(new BigDecimal("12.5000"))
                .currency("USD")
                .feeAmount(BigDecimal.ZERO)
                .description("Monthly interest credit")
                .channel("SYSTEM")
                .riskScore(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .completedAt(LocalDateTime.now())
                .settlementDate(LocalDate.now())
                .valueDate(LocalDate.now())
                .retryCount(0)
                .maxRetryAttempts(3)
                .version(0)
                .build();
    }

    /**
     * Creates a multi-currency transaction
     */
    public static Transaction createMultiCurrencyTransaction() {
        return Transaction.builder()
                .id(UUID.randomUUID())
                .transactionNumber(generateTransactionNumber())
                .idempotencyKey(UUID.randomUUID().toString())
                .transactionType(TransactionType.CURRENCY_EXCHANGE)
                .status(TransactionStatus.COMPLETED)
                .sourceAccountId(UUID.randomUUID())
                .targetAccountId(UUID.randomUUID())
                .amount(new BigDecimal("1000.0000"))
                .currency("USD")
                .exchangeRate(new BigDecimal("0.85000000")) // USD to EUR
                .convertedAmount(new BigDecimal("850.0000"))
                .convertedCurrency("EUR")
                .feeAmount(new BigDecimal("5.0000"))
                .description("Currency exchange USD to EUR")
                .channel("WEB")
                .riskScore(10)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .completedAt(LocalDateTime.now())
                .settlementDate(LocalDate.now())
                .valueDate(LocalDate.now())
                .retryCount(0)
                .maxRetryAttempts(3)
                .version(0)
                .build();
    }

    /**
     * Builder class for custom transaction creation
     */
    public static class TransactionBuilder {
        private Transaction transaction;

        private TransactionBuilder() {
            transaction = createCompletedTransfer();
        }

        public static TransactionBuilder builder() {
            return new TransactionBuilder();
        }

        public TransactionBuilder withId(UUID id) {
            transaction.setId(id);
            return this;
        }

        public TransactionBuilder withTransactionNumber(String number) {
            transaction.setTransactionNumber(number);
            return this;
        }

        public TransactionBuilder withIdempotencyKey(String key) {
            transaction.setIdempotencyKey(key);
            return this;
        }

        public TransactionBuilder withType(TransactionType type) {
            transaction.setTransactionType(type);
            return this;
        }

        public TransactionBuilder withStatus(TransactionStatus status) {
            transaction.setStatus(status);
            return this;
        }

        public TransactionBuilder withSourceAccount(UUID accountId) {
            transaction.setSourceAccountId(accountId);
            return this;
        }

        public TransactionBuilder withTargetAccount(UUID accountId) {
            transaction.setTargetAccountId(accountId);
            return this;
        }

        public TransactionBuilder withAmount(BigDecimal amount) {
            transaction.setAmount(amount);
            return this;
        }

        public TransactionBuilder withCurrency(String currency) {
            transaction.setCurrency(currency);
            return this;
        }

        public TransactionBuilder withFee(BigDecimal fee) {
            transaction.setFeeAmount(fee);
            return this;
        }

        public TransactionBuilder withDescription(String description) {
            transaction.setDescription(description);
            return this;
        }

        public TransactionBuilder withRiskScore(Integer riskScore) {
            transaction.setRiskScore(riskScore);
            return this;
        }

        public TransactionBuilder withOriginalTransaction(UUID originalTxnId) {
            transaction.setOriginalTransactionId(originalTxnId);
            return this;
        }

        public TransactionBuilder withReversalTransaction(UUID reversalTxnId) {
            transaction.setReversalTransactionId(reversalTxnId);
            return this;
        }

        public Transaction build() {
            return transaction;
        }
    }

    /**
     * Generates a random transaction number (12 alphanumeric characters)
     */
    private static String generateTransactionNumber() {
        return "TXN" + UUID.randomUUID().toString().substring(0, 9).toUpperCase();
    }
}
