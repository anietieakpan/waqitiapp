package com.waqiti.corebanking.service;

import com.waqiti.corebanking.config.MetricsConfiguration;
import com.waqiti.corebanking.domain.Account;
import com.waqiti.corebanking.domain.Transaction;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Metrics Service - Production Monitoring
 *
 * Provides centralized metrics recording for all core banking operations.
 * Exposes metrics via Prometheus for monitoring dashboards and alerting.
 *
 * Key Metrics Categories:
 * - Transaction processing (rates, latencies, failures)
 * - Account operations (balance updates, status changes)
 * - Fee calculations
 * - Compliance checks
 * - Fraud detection
 * - Fund reservations
 * - Interest calculations
 *
 * @author Principal Software Engineer
 * @since 1.0.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MetricsService {

    private final MeterRegistry meterRegistry;

    // ==================== Transaction Metrics ====================

    /**
     * Record transaction processing
     */
    public void recordTransaction(Transaction.TransactionType type, Transaction.TransactionStatus status, Duration duration) {
        // Counter for transaction totals
        Counter.builder(MetricsConfiguration.MetricNames.TRANSACTION_TOTAL)
            .tags(
                MetricsConfiguration.TagKeys.TRANSACTION_TYPE, type.name(),
                MetricsConfiguration.TagKeys.TRANSACTION_STATUS, status.name()
            )
            .description("Total number of transactions processed")
            .register(meterRegistry)
            .increment();

        // Timer for transaction processing duration
        Timer.builder(MetricsConfiguration.MetricNames.TRANSACTION_DURATION)
            .tags(
                MetricsConfiguration.TagKeys.TRANSACTION_TYPE, type.name(),
                MetricsConfiguration.TagKeys.TRANSACTION_STATUS, status.name()
            )
            .description("Transaction processing duration")
            .register(meterRegistry)
            .record(duration);

        // Track failed transactions separately
        if (status == Transaction.TransactionStatus.FAILED) {
            Counter.builder(MetricsConfiguration.MetricNames.TRANSACTION_FAILED)
                .tag(MetricsConfiguration.TagKeys.TRANSACTION_TYPE, type.name())
                .description("Total number of failed transactions")
                .register(meterRegistry)
                .increment();
        }
    }

    /**
     * Record transaction reversal
     */
    public void recordTransactionReversal(Transaction.TransactionType type, String reason) {
        Counter.builder(MetricsConfiguration.MetricNames.TRANSACTION_REVERSAL)
            .tags(
                MetricsConfiguration.TagKeys.TRANSACTION_TYPE, type.name(),
                "reversal_reason", reason != null ? reason : "unknown"
            )
            .description("Total number of transaction reversals")
            .register(meterRegistry)
            .increment();
    }

    /**
     * Record transaction retry attempt
     */
    public void recordTransactionRetry(Transaction.TransactionType type, int attemptNumber) {
        Counter.builder(MetricsConfiguration.MetricNames.TRANSACTION_RETRY)
            .tags(
                MetricsConfiguration.TagKeys.TRANSACTION_TYPE, type.name(),
                "attempt", String.valueOf(attemptNumber)
            )
            .description("Total number of transaction retry attempts")
            .register(meterRegistry)
            .increment();
    }

    // ==================== Account Metrics ====================

    /**
     * Record account balance update
     */
    public void recordAccountBalanceUpdate(Account.AccountType accountType, String currency, BigDecimal amount) {
        Counter.builder(MetricsConfiguration.MetricNames.ACCOUNT_BALANCE_UPDATE)
            .tags(
                MetricsConfiguration.TagKeys.ACCOUNT_TYPE, accountType.name(),
                MetricsConfiguration.TagKeys.CURRENCY, currency
            )
            .description("Total number of account balance updates")
            .register(meterRegistry)
            .increment();

        // Record current balance as gauge
        meterRegistry.gauge(
            MetricsConfiguration.MetricNames.ACCOUNT_BALANCE_CURRENT,
            MetricsConfiguration.tags(
                MetricsConfiguration.TagKeys.ACCOUNT_TYPE, accountType.name(),
                MetricsConfiguration.TagKeys.CURRENCY, currency
            ),
            amount
        );
    }

    /**
     * Record account creation
     */
    public void recordAccountCreation(Account.AccountType accountType, String currency) {
        Counter.builder(MetricsConfiguration.MetricNames.ACCOUNT_CREATION)
            .tags(
                MetricsConfiguration.TagKeys.ACCOUNT_TYPE, accountType.name(),
                MetricsConfiguration.TagKeys.CURRENCY, currency
            )
            .description("Total number of accounts created")
            .register(meterRegistry)
            .increment();
    }

    /**
     * Record account closure
     */
    public void recordAccountClosure(Account.AccountType accountType, String reason) {
        Counter.builder(MetricsConfiguration.MetricNames.ACCOUNT_CLOSURE)
            .tags(
                MetricsConfiguration.TagKeys.ACCOUNT_TYPE, accountType.name(),
                "closure_reason", reason != null ? reason : "unknown"
            )
            .description("Total number of accounts closed")
            .register(meterRegistry)
            .increment();
    }

    /**
     * Record account status change
     */
    public void recordAccountStatusChange(Account.AccountType accountType, Account.AccountStatus fromStatus, Account.AccountStatus toStatus) {
        Counter.builder(MetricsConfiguration.MetricNames.ACCOUNT_STATUS_CHANGE)
            .tags(
                MetricsConfiguration.TagKeys.ACCOUNT_TYPE, accountType.name(),
                "from_status", fromStatus.name(),
                "to_status", toStatus.name()
            )
            .description("Total number of account status changes")
            .register(meterRegistry)
            .increment();
    }

    // ==================== Fee Metrics ====================

    /**
     * Record fee calculation
     */
    public void recordFeeCalculation(String feeType, BigDecimal amount, Duration duration, boolean waived) {
        Counter.builder(MetricsConfiguration.MetricNames.FEE_CALCULATION)
            .tags(
                MetricsConfiguration.TagKeys.FEE_TYPE, feeType,
                "waived", String.valueOf(waived)
            )
            .description("Total number of fee calculations")
            .register(meterRegistry)
            .increment();

        Timer.builder(MetricsConfiguration.MetricNames.FEE_CALCULATION_DURATION)
            .tag(MetricsConfiguration.TagKeys.FEE_TYPE, feeType)
            .description("Fee calculation duration")
            .register(meterRegistry)
            .record(duration);

        // Track total fee amounts
        meterRegistry.counter(
            MetricsConfiguration.MetricNames.FEE_AMOUNT_TOTAL,
            MetricsConfiguration.TagKeys.FEE_TYPE, feeType
        ).increment(amount.doubleValue());

        if (waived) {
            meterRegistry.counter(
                MetricsConfiguration.MetricNames.FEE_WAIVED_TOTAL,
                MetricsConfiguration.TagKeys.FEE_TYPE, feeType
            ).increment(amount.doubleValue());
        }
    }

    // ==================== Compliance Metrics ====================

    /**
     * Record compliance check
     */
    public void recordComplianceCheck(String checkType, String result, Duration duration) {
        Counter.builder(MetricsConfiguration.MetricNames.COMPLIANCE_CHECK)
            .tags(
                MetricsConfiguration.TagKeys.COMPLIANCE_CHECK_TYPE, checkType,
                MetricsConfiguration.TagKeys.RESULT, result
            )
            .description("Total number of compliance checks")
            .register(meterRegistry)
            .increment();

        Timer.builder(MetricsConfiguration.MetricNames.COMPLIANCE_CHECK_DURATION)
            .tag(MetricsConfiguration.TagKeys.COMPLIANCE_CHECK_TYPE, checkType)
            .description("Compliance check duration")
            .register(meterRegistry)
            .record(duration);

        if ("FAILED".equals(result) || "REJECTED".equals(result)) {
            Counter.builder(MetricsConfiguration.MetricNames.COMPLIANCE_FAILURE)
                .tags(
                    MetricsConfiguration.TagKeys.COMPLIANCE_CHECK_TYPE, checkType,
                    MetricsConfiguration.TagKeys.RESULT, result
                )
                .description("Total number of compliance check failures")
                .register(meterRegistry)
                .increment();
        }
    }

    /**
     * Record compliance hold placement
     */
    public void recordComplianceHold(String checkType, String reason) {
        Counter.builder(MetricsConfiguration.MetricNames.COMPLIANCE_HOLD)
            .tags(
                MetricsConfiguration.TagKeys.COMPLIANCE_CHECK_TYPE, checkType,
                "reason", reason != null ? reason : "unknown"
            )
            .description("Total number of compliance holds placed")
            .register(meterRegistry)
            .increment();
    }

    /**
     * Record manual review requirement
     */
    public void recordComplianceManualReview(String checkType, String reason) {
        Counter.builder(MetricsConfiguration.MetricNames.COMPLIANCE_MANUAL_REVIEW)
            .tags(
                MetricsConfiguration.TagKeys.COMPLIANCE_CHECK_TYPE, checkType,
                "reason", reason != null ? reason : "unknown"
            )
            .description("Total number of manual review requirements")
            .register(meterRegistry)
            .increment();
    }

    // ==================== Fraud Detection Metrics ====================

    /**
     * Record fraud alert
     */
    public void recordFraudAlert(String reason, int riskScore, boolean blocked) {
        Counter.builder(MetricsConfiguration.MetricNames.FRAUD_ALERT)
            .tags(
                MetricsConfiguration.TagKeys.FRAUD_REASON, reason,
                "blocked", String.valueOf(blocked)
            )
            .description("Total number of fraud alerts")
            .register(meterRegistry)
            .increment();

        meterRegistry.gauge(
            MetricsConfiguration.MetricNames.FRAUD_RISK_SCORE,
            MetricsConfiguration.tags(MetricsConfiguration.TagKeys.FRAUD_REASON, reason),
            riskScore
        );

        if (riskScore >= 70) {
            Counter.builder(MetricsConfiguration.MetricNames.FRAUD_HIGH_RISK)
                .tag(MetricsConfiguration.TagKeys.FRAUD_REASON, reason)
                .description("Total number of high-risk fraud alerts")
                .register(meterRegistry)
                .increment();
        }
    }

    // ==================== Fund Reservation Metrics ====================

    /**
     * Record fund reservation created
     */
    public void recordFundReservationCreated(String reservationType, BigDecimal amount) {
        Counter.builder(MetricsConfiguration.MetricNames.FUND_RESERVATION_CREATED)
            .tag("reservation_type", reservationType)
            .description("Total number of fund reservations created")
            .register(meterRegistry)
            .increment();
    }

    /**
     * Record fund reservation released
     */
    public void recordFundReservationReleased(String reservationType, BigDecimal amount, boolean expired) {
        Counter.builder(MetricsConfiguration.MetricNames.FUND_RESERVATION_RELEASED)
            .tags(
                "reservation_type", reservationType,
                "expired", String.valueOf(expired)
            )
            .description("Total number of fund reservations released")
            .register(meterRegistry)
            .increment();

        if (expired) {
            Counter.builder(MetricsConfiguration.MetricNames.FUND_RESERVATION_EXPIRED)
                .tag("reservation_type", reservationType)
                .description("Total number of expired fund reservations")
                .register(meterRegistry)
                .increment();
        }
    }

    // ==================== Interest Metrics ====================

    /**
     * Record interest calculation
     */
    public void recordInterestCalculation(Account.AccountType accountType, BigDecimal amount) {
        Counter.builder(MetricsConfiguration.MetricNames.INTEREST_CALCULATION)
            .tag(MetricsConfiguration.TagKeys.ACCOUNT_TYPE, accountType.name())
            .description("Total number of interest calculations")
            .register(meterRegistry)
            .increment();

        Counter.builder(MetricsConfiguration.MetricNames.INTEREST_CREDITED)
            .tag(MetricsConfiguration.TagKeys.ACCOUNT_TYPE, accountType.name())
            .description("Total number of interest credits")
            .register(meterRegistry)
            .increment();

        meterRegistry.counter(
            MetricsConfiguration.MetricNames.INTEREST_AMOUNT,
            MetricsConfiguration.TagKeys.ACCOUNT_TYPE, accountType.name()
        ).increment(amount.doubleValue());
    }

    // ==================== Ledger Metrics ====================

    /**
     * Record ledger entry creation
     */
    public void recordLedgerEntry(String entryType) {
        Counter.builder(MetricsConfiguration.MetricNames.LEDGER_ENTRY_CREATED)
            .tag("entry_type", entryType)
            .description("Total number of ledger entries created")
            .register(meterRegistry)
            .increment();
    }

    /**
     * Record ledger balance verification
     */
    public void recordLedgerBalanceVerification(boolean balanced) {
        Counter.builder(MetricsConfiguration.MetricNames.LEDGER_BALANCE_VERIFICATION)
            .tags(
                MetricsConfiguration.TagKeys.RESULT, balanced ? "BALANCED" : "IMBALANCED"
            )
            .description("Total number of ledger balance verifications")
            .register(meterRegistry)
            .increment();

        if (!balanced) {
            Counter.builder(MetricsConfiguration.MetricNames.LEDGER_IMBALANCE_DETECTED)
                .description("Total number of ledger imbalances detected - CRITICAL")
                .register(meterRegistry)
                .increment();
        }
    }

    // ==================== Statement Metrics ====================

    /**
     * Record statement generation
     */
    public void recordStatementGeneration(String statementType, Duration duration, boolean success) {
        Counter.builder(MetricsConfiguration.MetricNames.STATEMENT_GENERATION)
            .tags(
                "statement_type", statementType,
                "success", String.valueOf(success)
            )
            .description("Total number of statements generated")
            .register(meterRegistry)
            .increment();

        Timer.builder(MetricsConfiguration.MetricNames.STATEMENT_GENERATION_DURATION)
            .tag("statement_type", statementType)
            .description("Statement generation duration")
            .register(meterRegistry)
            .record(duration);

        if (!success) {
            Counter.builder(MetricsConfiguration.MetricNames.STATEMENT_FAILURE)
                .tag("statement_type", statementType)
                .description("Total number of statement generation failures")
                .register(meterRegistry)
                .increment();
        }
    }

    // ==================== Exchange Rate Metrics ====================

    /**
     * Record exchange rate fetch
     */
    public void recordExchangeRateFetch(String currencyPair, boolean cacheHit) {
        Counter.builder(MetricsConfiguration.MetricNames.EXCHANGE_RATE_FETCH)
            .tags(
                "currency_pair", currencyPair,
                "cache_hit", String.valueOf(cacheHit)
            )
            .description("Total number of exchange rate fetches")
            .register(meterRegistry)
            .increment();

        if (cacheHit) {
            Counter.builder(MetricsConfiguration.MetricNames.EXCHANGE_RATE_CACHE_HIT)
                .tag("currency_pair", currencyPair)
                .description("Total number of exchange rate cache hits")
                .register(meterRegistry)
                .increment();
        } else {
            Counter.builder(MetricsConfiguration.MetricNames.EXCHANGE_RATE_CACHE_MISS)
                .tag("currency_pair", currencyPair)
                .description("Total number of exchange rate cache misses")
                .register(meterRegistry)
                .increment();
        }
    }

    // ==================== GDPR Metrics ====================

    /**
     * Record GDPR data erasure request
     */
    public void recordGdprErasure(Duration duration, boolean success, int accountsErased, int transactionsPseudonymized) {
        Counter.builder(MetricsConfiguration.MetricNames.GDPR_ERASURE_REQUEST)
            .tag("success", String.valueOf(success))
            .description("Total number of GDPR erasure requests")
            .register(meterRegistry)
            .increment();

        Timer.builder(MetricsConfiguration.MetricNames.GDPR_ERASURE_DURATION)
            .description("GDPR erasure processing duration")
            .register(meterRegistry)
            .record(duration);

        log.info("GDPR Erasure Metrics - Accounts Erased: {}, Transactions Pseudonymized: {}, Duration: {}ms",
            accountsErased, transactionsPseudonymized, duration.toMillis());
    }

    /**
     * Record GDPR data export request
     */
    public void recordGdprExport(boolean success) {
        Counter.builder(MetricsConfiguration.MetricNames.GDPR_EXPORT_REQUEST)
            .tag("success", String.valueOf(success))
            .description("Total number of GDPR export requests")
            .register(meterRegistry)
            .increment();
    }

    // ==================== Database Metrics ====================

    /**
     * Record optimistic lock failure
     */
    public void recordOptimisticLockFailure(String entityType) {
        Counter.builder(MetricsConfiguration.MetricNames.DB_OPTIMISTIC_LOCK_FAILURE)
            .tag("entity_type", entityType)
            .description("Total number of optimistic lock failures")
            .register(meterRegistry)
            .increment();
    }
}
