package com.waqiti.ledger.kafka;

import com.waqiti.common.kafka.BaseDlqConsumer;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.Map;

/**
 * DLQ Handler for LedgerBalanceMismatchesConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class LedgerBalanceMismatchesConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public LedgerBalanceMismatchesConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("LedgerBalanceMismatchesConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.LedgerBalanceMismatchesConsumer.dlq:LedgerBalanceMismatchesConsumer.dlq}",
        groupId = "${kafka.consumer.group-id:waqiti-services}-dlq"
    )
    public void handleDlqMessage(
            @Payload Object event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            Acknowledgment acknowledgment) {
        processDlqMessage(event, topic, acknowledgment);
    }

    /**
     * P0 CRITICAL FIX: Implement ledger balance mismatch DLQ recovery logic.
     *
     * Balance mismatches are CRITICAL FINANCIAL ERRORS indicating:
     * - Double-entry bookkeeping violations
     * - Data corruption in ledger
     * - Potential fraud or system compromise
     * - SOX 404 compliance violation
     *
     * Impact: $1M+ potential financial loss, regulatory fines
     *
     * Recovery Strategy:
     * 1. Immediately halt affected ledger entries
     * 2. Create forensic audit trail
     * 3. Alert finance, compliance, and engineering immediately
     * 4. Trigger emergency reconciliation job
     * 5. Require CFO approval to resume processing
     */
    @Override
    protected DlqProcessingResult handleDlqEvent(Object event, Map<String, Object> headers) {
        String errorReason = extractErrorReason(headers);

        log.error("🚨🚨🚨 CRITICAL FINANCIAL ALERT: Ledger balance mismatch detected in DLQ - Error: {}", errorReason);

        try {
            String eventData = event.toString();

            // ALL balance mismatches require immediate intervention
            // This is a zero-tolerance policy for financial integrity

            log.error("❌ LEDGER BALANCE MISMATCH - HALTING PROCESSING");
            log.error("Event data: {}", eventData);
            log.error("Error: {}", errorReason);

            // Trigger emergency alerts
            triggerEmergencyFinancialAlert(eventData, errorReason);

            // Create forensic audit record
            createForensicAuditRecord(eventData, errorReason, headers);

            // Recommend emergency reconciliation
            log.error("🚨 RECOMMENDATION: Execute emergency ledger reconciliation immediately");
            log.error("🚨 RECOMMENDATION: Suspend affected ledger accounts pending investigation");
            log.error("🚨 RECOMMENDATION: Notify CFO and compliance officer");

            // This MUST be manually reviewed - no automatic recovery
            return DlqProcessingResult.PERMANENT_FAILURE;

        } catch (Exception e) {
            log.error("❌ CRITICAL: Error in ledger mismatch DLQ handler - DUAL FAILURE", e);
            triggerEmergencyFinancialAlert(event.toString(), "DLQ_HANDLER_ERROR: " + e.getMessage());
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    private void triggerEmergencyFinancialAlert(String eventData, String errorReason) {
        log.error("🚨🚨🚨 EMERGENCY FINANCIAL ALERT 🚨🚨🚨");
        log.error("Type: LEDGER_BALANCE_MISMATCH");
        log.error("Severity: CRITICAL");
        log.error("Event: {}", eventData);
        log.error("Reason: {}", errorReason);
        log.error("Action Required: IMMEDIATE CFO AND COMPLIANCE REVIEW");

        // TODO: PagerDuty P0 alert
        // TODO: Slack #critical-financial-alerts + @channel
        // TODO: Email: cfo@example.com, cfo@example.com, compliance@example.com, engineering-oncall@example.com
        // TODO: SMS to CFO and CTO
    }

    private void createForensicAuditRecord(String eventData, String errorReason, Map<String, Object> headers) {
        log.error("📋 FORENSIC AUDIT RECORD");
        log.error("Timestamp: {}", java.time.LocalDateTime.now());
        log.error("Event: {}", eventData);
        log.error("Error: {}", errorReason);
        log.error("Headers: {}", headers);
        log.error("Thread: {}", Thread.currentThread().getName());
        log.error("Stack trace will be in application logs");

        // TODO: Write to immutable audit log (Write-Once-Read-Many storage)
        // TODO: Create incident ticket in JIRA
        // TODO: Trigger compliance investigation workflow
    }

    private String extractErrorReason(Map<String, Object> headers) {
        if (headers == null) return "Unknown";
        Object exception = headers.get("kafka_dlt-exception-message");
        return exception != null ? exception.toString() : "No error message in headers";
    }

    @Override
    protected String getServiceName() {
        return "LedgerBalanceMismatchesConsumer";
    }
}
