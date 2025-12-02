package com.waqiti.currency.service;

import com.waqiti.currency.model.EscalationPriority;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Treasury Operations Escalation Service
 *
 * Escalates currency conversion failures to treasury operations team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TreasuryOperationsEscalationService {

    private final MeterRegistry meterRegistry;

    /**
     * Escalate conversion failure to treasury operations
     */
    public void escalateConversionFailure(String conversionId, String fromCurrency, String toCurrency,
                                         BigDecimal amount, String failureReason, String eventId,
                                         String correlationId, EscalationPriority priority) {

        log.error("Escalating conversion failure to treasury operations: conversionId={} {}/{} amount={} reason={} priority={} correlationId={}",
                conversionId, fromCurrency, toCurrency, amount, failureReason, priority, correlationId);

        // Create escalation record
        createEscalationRecord(conversionId, fromCurrency, toCurrency, amount, failureReason,
                eventId, priority, correlationId);

        // Send treasury operations alert
        sendTreasuryOpsAlert(conversionId, fromCurrency, toCurrency, amount, failureReason, priority, correlationId);

        Counter.builder("treasury.escalation.conversion_failure")
                .tag("fromCurrency", fromCurrency)
                .tag("toCurrency", toCurrency)
                .tag("priority", priority.name())
                .register(meterRegistry)
                .increment();

        log.error("Treasury operations escalation created: conversionId={} correlationId={}", conversionId, correlationId);
    }

    /**
     * Create escalation record
     */
    private void createEscalationRecord(String conversionId, String fromCurrency, String toCurrency,
                                       BigDecimal amount, String failureReason, String eventId,
                                       EscalationPriority priority, String correlationId) {
        log.debug("Creating treasury escalation record: conversionId={} correlationId={}", conversionId, correlationId);
        // In production: Persist to database
    }

    /**
     * Send treasury operations alert
     */
    private void sendTreasuryOpsAlert(String conversionId, String fromCurrency, String toCurrency,
                                     BigDecimal amount, String failureReason,
                                     EscalationPriority priority, String correlationId) {
        log.error("Sending treasury ops alert: conversionId={} priority={} correlationId={}",
                conversionId, priority, correlationId);
        // In production: Send via alert channels (email, Slack, PagerDuty)
    }
}
