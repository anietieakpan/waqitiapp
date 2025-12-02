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
 * DLQ Handler for AsyncReversalPendingConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class AsyncReversalPendingConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public AsyncReversalPendingConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("AsyncReversalPendingConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.AsyncReversalPendingConsumer.dlq:AsyncReversalPendingConsumer.dlq}",
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
            // FIXED: Implement async reversal pending recovery logic
            log.warn("Processing DLQ async reversal pending event");

            String reversalId = headers.getOrDefault("reversalId", "").toString();
            String transactionId = headers.getOrDefault("transactionId", "").toString();
            String pendingDuration = headers.getOrDefault("pendingDuration", "0").toString();
            String failureReason = headers.getOrDefault("failureReason", "").toString();

            if (failureReason.contains("Database") || failureReason.contains("Connection")) {
                log.info("DLQ: Database error, marking for retry. Reversal: {}", reversalId);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("Duplicate") || failureReason.contains("AlreadyPending")) {
                log.info("DLQ: Reversal already marked as pending. Reversal: {}. Marking as resolved.", reversalId);
                return DlqProcessingResult.DISCARDED;
            } else if (failureReason.contains("TimeoutExceeded") && Integer.parseInt(pendingDuration) > 3600000) {
                log.error("DLQ: Reversal pending timeout exceeded (>1hr). Reversal: {}. Manual intervention required.", reversalId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            } else if (failureReason.contains("TransactionNotFound")) {
                log.warn("DLQ: Transaction not found for pending reversal. Transaction: {}. Retrying.", transactionId);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("StatusUpdateFailed")) {
                log.error("DLQ: Failed to update reversal status to pending. Reversal: {}. Retrying.", reversalId);
                return DlqProcessingResult.RETRY;
            } else {
                log.error("DLQ: Unknown error in async reversal pending. Event: {}, Headers: {}.", event, headers);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }
        } catch (Exception e) {
            log.error("Error handling DLQ async reversal pending event", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    @Override
    protected String getServiceName() {
        return "AsyncReversalPendingConsumer";
    }
}
