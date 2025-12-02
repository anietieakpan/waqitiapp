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
 * DLQ Handler for AsyncReversalValidationErrorsConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class AsyncReversalValidationErrorsConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public AsyncReversalValidationErrorsConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("AsyncReversalValidationErrorsConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.AsyncReversalValidationErrorsConsumer.dlq:AsyncReversalValidationErrorsConsumer.dlq}",
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
            // FIXED: Implement async reversal validation errors recovery logic
            log.warn("Processing DLQ async reversal validation errors");

            String reversalId = headers.getOrDefault("reversalId", "").toString();
            String validationError = headers.getOrDefault("validationError", "").toString();
            String amount = headers.getOrDefault("amount", "0").toString();
            String failureReason = headers.getOrDefault("failureReason", "").toString();

            if (failureReason.contains("Database") || failureReason.contains("Connection")) {
                log.info("DLQ: Database error, marking for retry. Reversal: {}", reversalId);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("InvalidAmount") || failureReason.contains("AmountMismatch")) {
                log.error("DLQ: Invalid amount in reversal. Reversal: {}, Amount: {}. Manual review required.", reversalId, amount);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            } else if (failureReason.contains("MissingRequiredField")) {
                log.error("DLQ: Missing required field in reversal. Reversal: {}. Cannot process, discarding.", reversalId);
                return DlqProcessingResult.DISCARDED;
            } else if (failureReason.contains("Duplicate")) {
                log.info("DLQ: Duplicate validation error already handled. Reversal: {}. Marking as resolved.", reversalId);
                return DlqProcessingResult.DISCARDED;
            } else if (failureReason.contains("InvalidTransactionId")) {
                log.error("DLQ: Invalid transaction ID for reversal. Reversal: {}. Manual review required.", reversalId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            } else {
                log.error("DLQ: Unknown validation error in async reversal. Event: {}, Headers: {}.", event, headers);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }
        } catch (Exception e) {
            log.error("Error handling DLQ async reversal validation errors", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    @Override
    protected String getServiceName() {
        return "AsyncReversalValidationErrorsConsumer";
    }
}
