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
 * DLQ Handler for SuspiciousTransactionConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class SuspiciousTransactionConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public SuspiciousTransactionConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("SuspiciousTransactionConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.SuspiciousTransactionConsumer.dlq:SuspiciousTransactionConsumer.dlq}",
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
            // FIXED: Implement suspicious transaction recovery logic
            log.warn("Processing DLQ suspicious transaction event");

            String transactionId = headers.getOrDefault("transactionId", "").toString();
            String userId = headers.getOrDefault("userId", "").toString();
            String suspicionReason = headers.getOrDefault("suspicionReason", "").toString();
            String suspicionScore = headers.getOrDefault("suspicionScore", "0").toString();
            String failureReason = headers.getOrDefault("failureReason", "").toString();

            if (failureReason.contains("Database") || failureReason.contains("Connection")) {
                log.info("DLQ: Database error, marking for retry. Transaction: {}", transactionId);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("Duplicate") || failureReason.contains("AlreadyFlagged")) {
                log.info("DLQ: Transaction already flagged. Transaction: {}. Marking as resolved.", transactionId);
                return DlqProcessingResult.DISCARDED;
            } else if (failureReason.contains("ReviewQueueFailed") && Double.parseDouble(suspicionScore) > 80) {
                log.error("DLQ: Failed to queue high-suspicion transaction for review. Transaction: {}, Score: {}. Retrying.", transactionId, suspicionScore);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("TransactionNotFound")) {
                log.warn("DLQ: Transaction not found for suspicious flag. Transaction: {}. Retrying.", transactionId);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("NotificationFailed")) {
                log.warn("DLQ: Failed to notify about suspicious transaction. Transaction: {}. Retrying.", transactionId);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("Validation") || failureReason.contains("Invalid")) {
                log.error("DLQ: Validation error in suspicious transaction. Transaction: {}. Manual review required.", transactionId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            } else {
                log.error("DLQ: Unknown error in suspicious transaction. Event: {}, Headers: {}.", event, headers);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }
        } catch (Exception e) {
            log.error("Error handling DLQ suspicious transaction event", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    @Override
    protected String getServiceName() {
        return "SuspiciousTransactionConsumer";
    }
}
