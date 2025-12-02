package com.waqiti.corebanking.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Collection;

/**
 * Metrics Configuration for Core Banking Service
 *
 * Configures comprehensive production metrics for monitoring:
 * - Transaction processing rates and latencies
 * - Account balance operations
 * - Fee calculations
 * - Compliance checks
 * - Fraud detection
 * - Fund reservations
 * - Interest calculations
 *
 * Metrics are exposed via /actuator/prometheus for Prometheus scraping
 *
 * @author Principal Software Engineer
 * @since 1.0.0
 */
@Configuration
public class MetricsConfiguration {

    /**
     * Add common tags to all metrics
     * Tags help filter and aggregate metrics in monitoring dashboards
     */
    @Bean
    public MeterRegistryCustomizer<MeterRegistry> metricsCommonTags() {
        return registry -> registry.config()
            .commonTags(
                "application", "core-banking-service",
                "service", "core-banking",
                "tier", "tier1-critical"
            );
    }

    /**
     * Metric name constants for consistency across the application
     */
    public static class MetricNames {

        // Transaction Metrics
        public static final String TRANSACTION_TOTAL = "core_banking.transaction.total";
        public static final String TRANSACTION_DURATION = "core_banking.transaction.duration";
        public static final String TRANSACTION_REVERSAL = "core_banking.transaction.reversal.total";
        public static final String TRANSACTION_FAILED = "core_banking.transaction.failed.total";
        public static final String TRANSACTION_PROCESSING_ACTIVE = "core_banking.transaction.processing.active";
        public static final String TRANSACTION_RETRY = "core_banking.transaction.retry.total";

        // Account Metrics
        public static final String ACCOUNT_BALANCE_UPDATE = "core_banking.account.balance.update.total";
        public static final String ACCOUNT_CREATION = "core_banking.account.creation.total";
        public static final String ACCOUNT_CLOSURE = "core_banking.account.closure.total";
        public static final String ACCOUNT_STATUS_CHANGE = "core_banking.account.status.change.total";
        public static final String ACCOUNT_BALANCE_CURRENT = "core_banking.account.balance.current";

        // Fee Metrics
        public static final String FEE_CALCULATION = "core_banking.fee.calculation.total";
        public static final String FEE_CALCULATION_DURATION = "core_banking.fee.calculation.duration";
        public static final String FEE_AMOUNT_TOTAL = "core_banking.fee.amount.total";
        public static final String FEE_WAIVED_TOTAL = "core_banking.fee.waived.total";

        // Compliance Metrics
        public static final String COMPLIANCE_CHECK = "core_banking.compliance.check.total";
        public static final String COMPLIANCE_CHECK_DURATION = "core_banking.compliance.check.duration";
        public static final String COMPLIANCE_FAILURE = "core_banking.compliance.failure.total";
        public static final String COMPLIANCE_HOLD = "core_banking.compliance.hold.total";
        public static final String COMPLIANCE_MANUAL_REVIEW = "core_banking.compliance.manual_review.total";

        // Fraud Detection Metrics
        public static final String FRAUD_ALERT = "core_banking.fraud.alert.total";
        public static final String FRAUD_RISK_SCORE = "core_banking.fraud.risk_score";
        public static final String FRAUD_HIGH_RISK = "core_banking.fraud.high_risk.total";

        // Fund Reservation Metrics
        public static final String FUND_RESERVATION_CREATED = "core_banking.fund_reservation.created.total";
        public static final String FUND_RESERVATION_RELEASED = "core_banking.fund_reservation.released.total";
        public static final String FUND_RESERVATION_EXPIRED = "core_banking.fund_reservation.expired.total";
        public static final String FUND_RESERVATION_ACTIVE = "core_banking.fund_reservation.active";

        // Interest Metrics
        public static final String INTEREST_CALCULATION = "core_banking.interest.calculation.total";
        public static final String INTEREST_CREDITED = "core_banking.interest.credited.total";
        public static final String INTEREST_AMOUNT = "core_banking.interest.amount";

        // Ledger Metrics
        public static final String LEDGER_ENTRY_CREATED = "core_banking.ledger.entry.created.total";
        public static final String LEDGER_BALANCE_VERIFICATION = "core_banking.ledger.balance.verification.total";
        public static final String LEDGER_IMBALANCE_DETECTED = "core_banking.ledger.imbalance.detected.total";

        // Statement Metrics
        public static final String STATEMENT_GENERATION = "core_banking.statement.generation.total";
        public static final String STATEMENT_GENERATION_DURATION = "core_banking.statement.generation.duration";
        public static final String STATEMENT_FAILURE = "core_banking.statement.failure.total";

        // External Integration Metrics
        public static final String EXCHANGE_RATE_FETCH = "core_banking.exchange_rate.fetch.total";
        public static final String EXCHANGE_RATE_CACHE_HIT = "core_banking.exchange_rate.cache_hit.total";
        public static final String EXCHANGE_RATE_CACHE_MISS = "core_banking.exchange_rate.cache_miss.total";

        // GDPR Metrics
        public static final String GDPR_ERASURE_REQUEST = "core_banking.gdpr.erasure.request.total";
        public static final String GDPR_EXPORT_REQUEST = "core_banking.gdpr.export.request.total";
        public static final String GDPR_ERASURE_DURATION = "core_banking.gdpr.erasure.duration";

        // Database Metrics
        public static final String DB_QUERY_DURATION = "core_banking.db.query.duration";
        public static final String DB_TRANSACTION_DURATION = "core_banking.db.transaction.duration";
        public static final String DB_OPTIMISTIC_LOCK_FAILURE = "core_banking.db.optimistic_lock.failure.total";

        // Circuit Breaker Metrics
        public static final String CIRCUIT_BREAKER_STATE = "core_banking.circuit_breaker.state";
        public static final String CIRCUIT_BREAKER_CALL_DURATION = "core_banking.circuit_breaker.call.duration";
    }

    /**
     * Tag keys for metric dimensions
     */
    public static class TagKeys {
        public static final String TRANSACTION_TYPE = "transaction_type";
        public static final String TRANSACTION_STATUS = "status";
        public static final String ACCOUNT_TYPE = "account_type";
        public static final String CURRENCY = "currency";
        public static final String FEE_TYPE = "fee_type";
        public static final String COMPLIANCE_CHECK_TYPE = "check_type";
        public static final String FRAUD_REASON = "fraud_reason";
        public static final String ERROR_TYPE = "error_type";
        public static final String SERVICE_NAME = "service";
        public static final String OPERATION = "operation";
        public static final String RESULT = "result";
    }

    /**
     * Helper method to create tags
     */
    public static Collection<Tag> tags(String... keyValues) {
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("Tag key-value pairs must be even");
        }
        return Tags.of(keyValues);
    }
}
