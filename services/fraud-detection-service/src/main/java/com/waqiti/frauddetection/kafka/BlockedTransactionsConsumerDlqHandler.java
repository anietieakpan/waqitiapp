package com.waqiti.frauddetection.kafka;

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
 * DLQ Handler for BlockedTransactionsConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class BlockedTransactionsConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public BlockedTransactionsConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("BlockedTransactionsConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.BlockedTransactionsConsumer.dlq:BlockedTransactionsConsumer.dlq}",
        groupId = "${kafka.consumer.group-id:waqiti-services}-dlq"
    )
    public void handleDlqMessage(
            @Payload Object event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            Acknowledgment acknowledgment) {
        processDlqMessage(event, topic, acknowledgment);
    }

    @Override
    protected DlqProcessingResult handleDlqEvent(Object event, Map<String, Object> headers) {
        try {
            // FIXED: Implement blocked transactions recovery logic
            log.warn("Processing DLQ blocked transactions event");

            String transactionId = headers.getOrDefault("transactionId", "").toString();
            String userId = headers.getOrDefault("userId", "").toString();
            String blockReason = headers.getOrDefault("blockReason", "").toString();
            String fraudScore = headers.getOrDefault("fraudScore", "0").toString();
            String failureReason = headers.getOrDefault("failureReason", "").toString();

            if (failureReason.contains("Database") || failureReason.contains("Connection")) {
                log.info("DLQ: Database error, marking for retry. Transaction: {}", transactionId);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("Duplicate") || failureReason.contains("AlreadyBlocked")) {
                log.info("DLQ: Transaction already blocked. Transaction: {}. Marking as resolved.", transactionId);
                return DlqProcessingResult.DISCARDED;
            } else if (failureReason.contains("TransactionNotFound")) {
                log.warn("DLQ: Transaction not found for blocking. Transaction: {}. Retrying.", transactionId);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("NotificationFailed")) {
                log.error("DLQ: Failed to notify user of blocked transaction. User: {}, Transaction: {}. Retrying.", userId, transactionId);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("ReversalFailed")) {
                log.error("DLQ: Failed to reverse blocked transaction. Transaction: {}. Manual review required.", transactionId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            } else if (failureReason.contains("Validation") || failureReason.contains("Invalid")) {
                log.error("DLQ: Validation error in blocked transaction. Transaction: {}. Manual review required.", transactionId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            } else {
                log.error("DLQ: Unknown error in blocked transactions. Event: {}, Headers: {}.", event, headers);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }
        } catch (Exception e) {
            log.error("Error handling DLQ blocked transactions event", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    @Override
    protected String getServiceName() {
        return "BlockedTransactionsConsumer";
    }
}
