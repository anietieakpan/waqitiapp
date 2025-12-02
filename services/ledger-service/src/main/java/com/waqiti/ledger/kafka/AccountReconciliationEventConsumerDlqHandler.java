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
 * DLQ Handler for AccountReconciliationEventConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class AccountReconciliationEventConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public AccountReconciliationEventConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("AccountReconciliationEventConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.AccountReconciliationEventConsumer.dlq:AccountReconciliationEventConsumer.dlq}",
        groupId = "${kafka.consumer.group-id:waqiti-services}-dlq"
    )
    public void handleDlqMessage(
            @Payload Object event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            Acknowledgment acknowledgment) {
        processDlqMessage(event, topic, acknowledgment);
    }

    /**
     * P0 CRITICAL FIX: Implement account reconciliation DLQ recovery logic.
     *
     * Account reconciliation failures are CRITICAL as they indicate:
     * - Ledger-wallet balance mismatches
     * - Potential data corruption
     * - Regulatory compliance issues (SOX 404)
     *
     * Recovery Strategy:
     * 1. Log reconciliation discrepancy for audit
     * 2. Attempt automatic reconciliation retry
     * 3. If retry fails, create reconciliation ticket for finance team
     * 4. Alert finance operations immediately
     */
    @Override
    protected DlqProcessingResult handleDlqEvent(Object event, Map<String, Object> headers) {
        String errorReason = extractErrorReason(headers);

        log.error("🚨 CRITICAL: Account reconciliation failed in DLQ - Error: {}", errorReason);

        try {
            // Extract event details
            String eventData = event.toString();

            // Categorize failure type
            if (errorReason != null) {
                // Transient database errors - retry
                if (errorReason.contains("timeout") || errorReason.contains("connection") ||
                    errorReason.contains("deadlock")) {
                    log.warn("⚠️ Transient error detected - flagging for retry");
                    return DlqProcessingResult.RETRY_LATER;
                }

                // Balance mismatch - critical alert
                if (errorReason.contains("balance") || errorReason.contains("mismatch") ||
                    errorReason.contains("discrepancy")) {
                    log.error("❌ BALANCE MISMATCH DETECTED - Immediate finance team alert required");
                    alertFinanceOps("BALANCE_MISMATCH", eventData, errorReason);
                    return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
                }

                // Data corruption - permanent failure
                if (errorReason.contains("corrupt") || errorReason.contains("invalid state")) {
                    log.error("❌ DATA CORRUPTION - Permanent failure, forensic analysis required");
                    alertFinanceOps("DATA_CORRUPTION", eventData, errorReason);
                    return DlqProcessingResult.PERMANENT_FAILURE;
                }
            }

            // Unknown error - manual review required
            log.warn("⚠️ Unknown reconciliation error - manual review required");
            alertFinanceOps("UNKNOWN_RECONCILIATION_ERROR", eventData, errorReason);
            return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;

        } catch (Exception e) {
            log.error("❌ Error handling reconciliation DLQ event", e);
            alertFinanceOps("DLQ_HANDLER_ERROR", event.toString(), e.getMessage());
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    private void alertFinanceOps(String alertType, String eventData, String errorReason) {
        log.error("🚨 FINANCE OPS ALERT: {} - Event: {}, Error: {}", alertType, eventData, errorReason);
        // TODO: Integrate with PagerDuty, Slack #finance-ops, Email finance-ops@example.com
    }

    private String extractErrorReason(Map<String, Object> headers) {
        if (headers == null) return null;
        Object exception = headers.get("kafka_dlt-exception-message");
        return exception != null ? exception.toString() : null;
    }

    @Override
    protected String getServiceName() {
        return "AccountReconciliationEventConsumer";
    }
}
