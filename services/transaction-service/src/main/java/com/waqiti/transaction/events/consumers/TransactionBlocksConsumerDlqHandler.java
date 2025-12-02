package com.waqiti.transaction.events.consumers;

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
 * DLQ Handler for TransactionBlocksConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class TransactionBlocksConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public TransactionBlocksConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("TransactionBlocksConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.TransactionBlocksConsumer.dlq:TransactionBlocksConsumer.dlq}",
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
            // FIXED: Implement transaction blocks consumer recovery logic (duplicate handler)
            log.warn("Processing DLQ transaction blocks event for recovery");

            String transactionId = headers.getOrDefault("transactionId", "").toString();
            String failureReason = headers.getOrDefault("failureReason", "").toString();

            if (failureReason.contains("Database") || failureReason.contains("Connection")) {
                log.info("DLQ: Database error detected, marking for retry. Transaction: {}", transactionId);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("TransactionNotFound")) {
                log.warn("DLQ: Transaction not found for blocking. Transaction: {}. Retrying.", transactionId);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("AlreadyBlocked")) {
                log.info("DLQ: Transaction already blocked. Transaction: {}. Marking as resolved.", transactionId);
                return DlqProcessingResult.DISCARDED;
            } else if (failureReason.contains("TransactionCompleted")) {
                log.warn("DLQ: Cannot block completed transaction. Transaction: {}. Discarding.", transactionId);
                return DlqProcessingResult.DISCARDED;
            } else if (failureReason.contains("Validation") || failureReason.contains("Invalid")) {
                log.error("DLQ: Validation error in transaction blocks. Transaction: {}. Manual review required.", transactionId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            } else {
                log.error("DLQ: Unknown error in transaction blocks event. Event: {}, Headers: {}. Requires investigation.",
                        event, headers);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }
        } catch (Exception e) {
            log.error("Error handling DLQ transaction blocks event", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    @Override
    protected String getServiceName() {
        return "TransactionBlocksConsumer";
    }
}
