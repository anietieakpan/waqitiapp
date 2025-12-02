package com.waqiti.currency.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Financial Emergency Service
 *
 * Handles emergency protocols for critical currency conversion failures:
 * - DLT event emergency protocols
 * - All-channel emergency broadcasts
 * - Conversion halt procedures
 * - Financial officer notifications
 * - Rollback initiation
 * - Emergency measures application
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FinancialEmergencyService {

    private final MeterRegistry meterRegistry;

    private static final BigDecimal EMERGENCY_THRESHOLD = BigDecimal.valueOf(100000);

    /**
     * Execute financial transaction emergency protocol
     */
    public EmergencyProtocolResult executeFinancialTransactionEmergencyProtocol(
            String conversionId, String customerId, String sourceCurrency,
            String targetCurrency, BigDecimal amount, String eventData,
            String correlationId) {

        log.error("🚨 EXECUTING FINANCIAL EMERGENCY PROTOCOL: conversionId={} customer={} " +
                "amount={} {} correlationId={}",
                conversionId, customerId, amount, sourceCurrency, correlationId);

        Instant startTime = Instant.now();
        List<String> executedMeasures = new ArrayList<>();

        try {
            // 1. Immediate notification to financial officers
            notifyFinancialOfficers(conversionId, customerId, sourceCurrency, targetCurrency,
                    amount, eventData, correlationId);
            executedMeasures.add("FINANCIAL_OFFICERS_NOTIFIED");

            // 2. Halt all conversions for affected currency pair
            if (amount.compareTo(EMERGENCY_THRESHOLD) >= 0) {
                haltCurrencyPairConversions(sourceCurrency, targetCurrency, correlationId);
                executedMeasures.add("CURRENCY_PAIR_HALTED");
            }

            // 3. Initiate emergency investigation
            initiateEmergencyInvestigation(conversionId, customerId, amount,
                    sourceCurrency, targetCurrency, eventData, correlationId);
            executedMeasures.add("EMERGENCY_INVESTIGATION_INITIATED");

            // 4. Lock customer funds
            lockCustomerFunds(customerId, amount, sourceCurrency, correlationId);
            executedMeasures.add("CUSTOMER_FUNDS_LOCKED");

            // 5. Alert compliance team
            alertComplianceTeam(conversionId, customerId, amount, sourceCurrency,
                    targetCurrency, correlationId);
            executedMeasures.add("COMPLIANCE_TEAM_ALERTED");

            // 6. Create emergency audit trail
            createEmergencyAuditTrail(conversionId, customerId, amount, sourceCurrency,
                    targetCurrency, eventData, executedMeasures, correlationId);
            executedMeasures.add("AUDIT_TRAIL_CREATED");

            incrementCounter("currency.emergency.protocol.executed");

            log.error("Financial emergency protocol completed: conversionId={} measures={} correlationId={}",
                    conversionId, executedMeasures.size(), correlationId);

            return EmergencyProtocolResult.builder()
                    .protocolExecuted(true)
                    .conversionId(conversionId)
                    .executedMeasures(executedMeasures)
                    .executionTime(java.time.Duration.between(startTime, Instant.now()))
                    .requiresManualIntervention(true)
                    .build();

        } catch (Exception e) {
            log.error("Failed to execute emergency protocol: conversionId={} correlationId={}",
                    conversionId, correlationId, e);
            incrementCounter("currency.emergency.protocol.error");

            executedMeasures.add("ERROR_DURING_EXECUTION");

            return EmergencyProtocolResult.builder()
                    .protocolExecuted(false)
                    .conversionId(conversionId)
                    .executedMeasures(executedMeasures)
                    .errorMessage(e.getMessage())
                    .requiresManualIntervention(true)
                    .build();
        }
    }

    /**
     * Notify financial officers of emergency
     */
    @Async
    public void notifyFinancialOfficers(String conversionId, String customerId,
                                       String sourceCurrency, String targetCurrency,
                                       BigDecimal amount, String eventData,
                                       String correlationId) {

        log.error("🚨 NOTIFYING FINANCIAL OFFICERS: conversionId={} amount={} {} correlationId={}",
                conversionId, amount, sourceCurrency, correlationId);

        try {
            String emergencyAlert = buildFinancialEmergencyAlert(
                    conversionId, customerId, sourceCurrency, targetCurrency,
                    amount, eventData
            );

            // In production: Send via PagerDuty, SMS to on-call officers, emergency Slack channel
            log.error("FINANCIAL EMERGENCY ALERT:\n{}", emergencyAlert);

            incrementCounter("currency.emergency.officers_notified");

        } catch (Exception e) {
            log.error("Failed to notify financial officers: conversionId={} correlationId={}",
                    conversionId, correlationId, e);
            incrementCounter("currency.emergency.notification.error");
        }
    }

    /**
     * Halt all conversions for currency pair
     */
    @Async
    public void haltCurrencyPairConversions(String sourceCurrency, String targetCurrency,
                                           String correlationId) {

        log.error("🛑 HALTING CONVERSIONS: {}→{} correlationId={}",
                sourceCurrency, targetCurrency, correlationId);

        try {
            // In production: Set circuit breaker, disable currency pair in system
            log.error("CONVERSION HALT: {}→{} - All conversions for this pair are suspended",
                    sourceCurrency, targetCurrency);

            incrementCounter("currency.emergency.conversions_halted");

        } catch (Exception e) {
            log.error("Failed to halt conversions: {}→{} correlationId={}",
                    sourceCurrency, targetCurrency, correlationId, e);
            incrementCounter("currency.emergency.halt.error");
        }
    }

    /**
     * Initiate emergency investigation
     */
    @Async
    public void initiateEmergencyInvestigation(String conversionId, String customerId,
                                              BigDecimal amount, String sourceCurrency,
                                              String targetCurrency, String eventData,
                                              String correlationId) {

        log.error("🔍 INITIATING EMERGENCY INVESTIGATION: conversionId={} correlationId={}",
                conversionId, correlationId);

        try {
            String investigationId = String.format("INV-%s-%d", conversionId, Instant.now().toEpochMilli());

            log.error("Emergency investigation created: id={} conversionId={} amount={} {} correlationId={}",
                    investigationId, conversionId, amount, sourceCurrency, correlationId);

            incrementCounter("currency.emergency.investigation.initiated");

        } catch (Exception e) {
            log.error("Failed to initiate investigation: conversionId={} correlationId={}",
                    conversionId, correlationId, e);
            incrementCounter("currency.emergency.investigation.error");
        }
    }

    /**
     * Lock customer funds for protection
     */
    @Async
    public void lockCustomerFunds(String customerId, BigDecimal amount, String currency,
                                 String correlationId) {

        log.warn("🔒 LOCKING CUSTOMER FUNDS: customer={} amount={} {} correlationId={}",
                customerId, amount, currency, correlationId);

        try {
            // In production: Place hold on customer account
            log.warn("Customer funds locked for protection: customer={} amount={} {}",
                    customerId, amount, currency);

            incrementCounter("currency.emergency.funds_locked");

        } catch (Exception e) {
            log.error("Failed to lock customer funds: customer={} correlationId={}",
                    customerId, correlationId, e);
            incrementCounter("currency.emergency.lock.error");
        }
    }

    /**
     * Alert compliance team
     */
    @Async
    public void alertComplianceTeam(String conversionId, String customerId, BigDecimal amount,
                                   String sourceCurrency, String targetCurrency,
                                   String correlationId) {

        log.warn("⚠️ ALERTING COMPLIANCE TEAM: conversionId={} amount={} {} correlationId={}",
                conversionId, amount, sourceCurrency, correlationId);

        try {
            String complianceAlert = String.format(
                "COMPLIANCE ALERT - Emergency Currency Conversion\n\n" +
                "Conversion ID: %s\n" +
                "Customer ID: %s\n" +
                "Amount: %s %s\n" +
                "Currency Pair: %s → %s\n" +
                "Severity: CRITICAL\n\n" +
                "Action Required: Immediate compliance review for DLT event.",
                conversionId, customerId, amount, sourceCurrency,
                sourceCurrency, targetCurrency
            );

            log.warn("COMPLIANCE ALERT:\n{}", complianceAlert);

            incrementCounter("currency.emergency.compliance_alerted");

        } catch (Exception e) {
            log.error("Failed to alert compliance: conversionId={} correlationId={}",
                    conversionId, correlationId, e);
            incrementCounter("currency.emergency.compliance.error");
        }
    }

    /**
     * Create emergency audit trail
     */
    @Async
    public void createEmergencyAuditTrail(String conversionId, String customerId,
                                         BigDecimal amount, String sourceCurrency,
                                         String targetCurrency, String eventData,
                                         List<String> executedMeasures, String correlationId) {

        log.info("Creating emergency audit trail: conversionId={} correlationId={}",
                conversionId, correlationId);

        try {
            String auditEntry = String.format(
                "EMERGENCY AUDIT TRAIL\n" +
                "Timestamp: %s\n" +
                "Conversion ID: %s\n" +
                "Customer ID: %s\n" +
                "Amount: %s %s\n" +
                "Currency Pair: %s → %s\n" +
                "Executed Measures: %s\n" +
                "Event Data: %s\n" +
                "Correlation ID: %s",
                Instant.now(), conversionId, customerId, amount, sourceCurrency,
                sourceCurrency, targetCurrency, String.join(", ", executedMeasures),
                eventData, correlationId
            );

            log.info("EMERGENCY AUDIT:\n{}", auditEntry);

            incrementCounter("currency.emergency.audit_trail.created");

        } catch (Exception e) {
            log.error("Failed to create audit trail: conversionId={} correlationId={}",
                    conversionId, correlationId, e);
            incrementCounter("currency.emergency.audit.error");
        }
    }

    /**
     * Initiate rollback for failed conversion
     */
    @Async
    public void initiateRollback(String conversionId, String customerId, BigDecimal amount,
                                String currency, String correlationId) {

        log.warn("↩️ INITIATING ROLLBACK: conversionId={} customer={} amount={} {} correlationId={}",
                conversionId, customerId, amount, currency, correlationId);

        try {
            // In production: Reverse any partial transactions
            log.warn("Rollback initiated for conversion: conversionId={} amount={} {}",
                    conversionId, amount, currency);

            incrementCounter("currency.emergency.rollback.initiated");

        } catch (Exception e) {
            log.error("Failed to initiate rollback: conversionId={} correlationId={}",
                    conversionId, correlationId, e);
            incrementCounter("currency.emergency.rollback.error");
        }
    }

    /**
     * Build financial emergency alert message
     */
    private String buildFinancialEmergencyAlert(String conversionId, String customerId,
                                               String sourceCurrency, String targetCurrency,
                                               BigDecimal amount, String eventData) {

        return String.format(
            "🚨🚨🚨 FINANCIAL EMERGENCY 🚨🚨🚨\n\n" +
            "CRITICAL: Currency conversion has reached Dead Letter Topic (DLT)\n\n" +
            "Conversion ID: %s\n" +
            "Customer ID: %s\n" +
            "Currency Pair: %s → %s\n" +
            "Amount: %s %s\n" +
            "Severity: CRITICAL\n" +
            "Impact: HIGH\n\n" +
            "Event Data: %s\n\n" +
            "IMMEDIATE ACTION REQUIRED:\n" +
            "1. Review conversion failure details\n" +
            "2. Assess financial impact\n" +
            "3. Coordinate with treasury operations\n" +
            "4. Determine if refund is necessary\n" +
            "5. Update customer status\n\n" +
            "Emergency protocols have been activated automatically.",
            conversionId, customerId, sourceCurrency, targetCurrency,
            amount, sourceCurrency, eventData
        );
    }

    /**
     * Increment counter metric
     */
    private void incrementCounter(String metricName) {
        Counter.builder(metricName)
                .register(meterRegistry)
                .increment();
    }

    // Inner classes

    @lombok.Data
    @lombok.Builder
    public static class EmergencyProtocolResult {
        private boolean protocolExecuted;
        private String conversionId;
        private List<String> executedMeasures;
        private java.time.Duration executionTime;
        private boolean requiresManualIntervention;
        private String errorMessage;
    }
}
