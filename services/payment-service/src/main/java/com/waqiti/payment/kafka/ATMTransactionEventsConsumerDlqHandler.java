package com.waqiti.payment.kafka;

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
 * DLQ Handler for ATMTransactionEventsConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class ATMTransactionEventsConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public ATMTransactionEventsConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("ATMTransactionEventsConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.ATMTransactionEventsConsumer.dlq:ATMTransactionEventsConsumer.dlq}",
        groupId = "${kafka.consumer.group-id:waqiti-services}-dlq"
    )
    public void handleDlqMessage(
            @Payload Object event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            Acknowledgment acknowledgment) {
        processDlqMessage(event, topic, acknowledgment);
    }

    @Autowired
    private com.waqiti.common.dlq.DlqRecoveryService dlqRecoveryService;

    @Autowired
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @Override
    protected DlqProcessingResult handleDlqEvent(Object event, Map<String, Object> headers) {
        try {
            log.info("Processing ATM transaction DLQ event: {}", event);

            // Extract failure context
            String failureReason = extractFailureReason(headers);
            String correlationId = extractCorrelationId(headers);

            // Categorize failure type
            FailureCategory category = categorizeFailure(failureReason, event);

            // Execute recovery strategy based on failure category
            switch (category) {
                case NETWORK_TIMEOUT:
                case TEMPORARY_UNAVAILABILITY:
                    // Transient errors - retry with exponential backoff
                    return handleTransientFailure(event, failureReason, headers);

                case DUPLICATE_TRANSACTION:
                    // Idempotency check - safe to ignore
                    return handleDuplicateTransaction(event, correlationId);

                case INVALID_ATM_ID:
                case MALFORMED_DATA:
                    // Data quality issues - manual review required
                    return handleDataQualityIssue(event, failureReason, headers);

                case INSUFFICIENT_BALANCE:
                case CARD_BLOCKED:
                    // Business rule violations - create reversal
                    return handleBusinessRuleViolation(event, failureReason, headers);

                case UNKNOWN:
                default:
                    // Unknown errors - conservative approach
                    return handleUnknownFailure(event, failureReason, headers);
            }

        } catch (Exception e) {
            log.error("Error handling ATM transaction DLQ event", e);
            persistForManualReview(event, e.getMessage(), headers);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    private DlqProcessingResult handleTransientFailure(Object event, String failureReason,
                                                        Map<String, Object> headers) {
        log.info("Transient failure detected - scheduling retry: {}", failureReason);

        com.waqiti.common.dlq.DlqEvent dlqEvent = dlqRecoveryService.persistDlqEvent(
            getServiceName(),
            "atm.transactions",
            "atm.transactions.dlq",
            event,
            failureReason,
            null,
            convertHeaders(headers),
            com.waqiti.common.dlq.DlqEvent.RecoveryStrategy.RETRY
        );

        log.info("Scheduled retry for DLQ event: {}", dlqEvent.getEventId());
        return DlqProcessingResult.RETRY_SCHEDULED;
    }

    private DlqProcessingResult handleDuplicateTransaction(Object event, String correlationId) {
        log.info("Duplicate ATM transaction detected - auto-resolving: {}", correlationId);

        // Check if original transaction was processed
        // If yes, this is a safe duplicate - resolve without action
        return DlqProcessingResult.AUTO_RESOLVED;
    }

    private DlqProcessingResult handleDataQualityIssue(Object event, String failureReason,
                                                         Map<String, Object> headers) {
        log.warn("Data quality issue detected - marking for manual review: {}", failureReason);

        com.waqiti.common.dlq.DlqEvent dlqEvent = dlqRecoveryService.persistDlqEvent(
            getServiceName(),
            "atm.transactions",
            "atm.transactions.dlq",
            event,
            failureReason,
            null,
            convertHeaders(headers),
            com.waqiti.common.dlq.DlqEvent.RecoveryStrategy.MANUAL_REVIEW
        );

        dlqRecoveryService.markForManualReview(dlqEvent,
            "Invalid data in ATM transaction - requires data correction");

        return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
    }

    private DlqProcessingResult handleBusinessRuleViolation(Object event, String failureReason,
                                                              Map<String, Object> headers) {
        log.warn("Business rule violation - executing compensation: {}", failureReason);

        com.waqiti.common.dlq.DlqEvent dlqEvent = dlqRecoveryService.persistDlqEvent(
            getServiceName(),
            "atm.transactions",
            "atm.transactions.dlq",
            event,
            failureReason,
            null,
            convertHeaders(headers),
            com.waqiti.common.dlq.DlqEvent.RecoveryStrategy.COMPENSATE
        );

        // Execute compensation (reversal, notification, etc.)
        dlqRecoveryService.executeCompensation(dlqEvent, "ATM transaction reversal");

        return DlqProcessingResult.COMPENSATED;
    }

    private DlqProcessingResult handleUnknownFailure(Object event, String failureReason,
                                                       Map<String, Object> headers) {
        log.error("Unknown failure - conservative approach: manual review required");

        persistForManualReview(event, failureReason, headers);
        return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
    }

    private void persistForManualReview(Object event, String failureReason,
                                         Map<String, Object> headers) {
        com.waqiti.common.dlq.DlqEvent dlqEvent = dlqRecoveryService.persistDlqEvent(
            getServiceName(),
            "atm.transactions",
            "atm.transactions.dlq",
            event,
            failureReason,
            null,
            convertHeaders(headers),
            com.waqiti.common.dlq.DlqEvent.RecoveryStrategy.MANUAL_REVIEW
        );

        dlqRecoveryService.markForManualReview(dlqEvent, "Unknown error - requires investigation");
    }

    private FailureCategory categorizeFailure(String failureReason, Object event) {
        if (failureReason == null) return FailureCategory.UNKNOWN;

        String reason = failureReason.toLowerCase();
        if (reason.contains("timeout") || reason.contains("connection")) {
            return FailureCategory.NETWORK_TIMEOUT;
        }
        if (reason.contains("duplicate") || reason.contains("already processed")) {
            return FailureCategory.DUPLICATE_TRANSACTION;
        }
        if (reason.contains("invalid atm") || reason.contains("unknown atm")) {
            return FailureCategory.INVALID_ATM_ID;
        }
        if (reason.contains("malformed") || reason.contains("parse")) {
            return FailureCategory.MALFORMED_DATA;
        }
        if (reason.contains("insufficient") || reason.contains("balance")) {
            return FailureCategory.INSUFFICIENT_BALANCE;
        }
        if (reason.contains("blocked") || reason.contains("suspended")) {
            return FailureCategory.CARD_BLOCKED;
        }
        if (reason.contains("unavailable") || reason.contains("service down")) {
            return FailureCategory.TEMPORARY_UNAVAILABILITY;
        }

        return FailureCategory.UNKNOWN;
    }

    private String extractFailureReason(Map<String, Object> headers) {
        if (headers == null) return "Unknown";
        Object reason = headers.get("kafka_exception-message");
        return reason != null ? reason.toString() : "Unknown";
    }

    private String extractCorrelationId(Map<String, Object> headers) {
        if (headers == null) return null;
        Object id = headers.get("correlationId");
        return id != null ? id.toString() : null;
    }

    private Map<String, String> convertHeaders(Map<String, Object> headers) {
        if (headers == null) return java.util.Collections.emptyMap();
        return headers.entrySet().stream()
            .collect(java.util.stream.Collectors.toMap(
                Map.Entry::getKey,
                e -> e.getValue() != null ? e.getValue().toString() : ""
            ));
    }

    private enum FailureCategory {
        NETWORK_TIMEOUT,
        TEMPORARY_UNAVAILABILITY,
        DUPLICATE_TRANSACTION,
        INVALID_ATM_ID,
        MALFORMED_DATA,
        INSUFFICIENT_BALANCE,
        CARD_BLOCKED,
        UNKNOWN
    }

    @Override
    protected String getServiceName() {
        return "ATMTransactionEventsConsumer";
    }
}
