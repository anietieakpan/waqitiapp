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
 * DLQ Handler for ATODetectionValidationErrorsConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class ATODetectionValidationErrorsConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public ATODetectionValidationErrorsConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("ATODetectionValidationErrorsConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.ATODetectionValidationErrorsConsumer.dlq:ATODetectionValidationErrorsConsumer.dlq}",
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
            // FIXED: Implement ATO detection validation errors recovery logic
            log.error("Processing DLQ ATO detection validation errors - SECURITY RELEVANT");

            String detectionId = headers.getOrDefault("detectionId", "").toString();
            String userId = headers.getOrDefault("userId", "").toString();
            String validationError = headers.getOrDefault("validationError", "").toString();
            String failureReason = headers.getOrDefault("failureReason", "").toString();

            if (failureReason.contains("Database") || failureReason.contains("Connection")) {
                log.info("DLQ: Database error, marking for retry. Detection: {}", detectionId);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("InvalidUserId") || failureReason.contains("UserIdMismatch")) {
                log.error("DLQ: Invalid user ID in ATO detection. Detection: {}, User: {}. Discarding invalid event.", detectionId, userId);
                return DlqProcessingResult.DISCARDED;
            } else if (failureReason.contains("MissingSecurityContext")) {
                log.error("DLQ: Missing security context for ATO validation. Detection: {}. Manual review required.", detectionId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            } else if (failureReason.contains("Duplicate")) {
                log.info("DLQ: Duplicate ATO validation error. Detection: {}. Marking as resolved.", detectionId);
                return DlqProcessingResult.DISCARDED;
            } else if (failureReason.contains("InvalidSignature") || failureReason.contains("TamperedData")) {
                log.error("DLQ: Data integrity issue in ATO detection. Detection: {}. URGENT manual review.", detectionId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            } else {
                log.error("DLQ: Unknown validation error in ATO detection. Event: {}, Headers: {}.", event, headers);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }
        } catch (Exception e) {
            log.error("Error handling DLQ ATO detection validation errors", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    @Override
    protected String getServiceName() {
        return "ATODetectionValidationErrorsConsumer";
    }
}
