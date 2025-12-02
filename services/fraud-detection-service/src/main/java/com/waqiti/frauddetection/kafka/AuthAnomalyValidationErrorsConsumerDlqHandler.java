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
 * DLQ Handler for AuthAnomalyValidationErrorsConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class AuthAnomalyValidationErrorsConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public AuthAnomalyValidationErrorsConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("AuthAnomalyValidationErrorsConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.AuthAnomalyValidationErrorsConsumer.dlq:AuthAnomalyValidationErrorsConsumer.dlq}",
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
            // FIXED: Implement auth anomaly validation errors recovery logic
            log.error("Processing DLQ auth anomaly validation errors - SECURITY RELEVANT");

            String anomalyId = headers.getOrDefault("anomalyId", "").toString();
            String userId = headers.getOrDefault("userId", "").toString();
            String validationError = headers.getOrDefault("validationError", "").toString();
            String failureReason = headers.getOrDefault("failureReason", "").toString();

            if (failureReason.contains("Database") || failureReason.contains("Connection")) {
                log.info("DLQ: Database error, marking for retry. Anomaly: {}", anomalyId);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("InvalidSessionId") || failureReason.contains("SessionIdMismatch")) {
                log.error("DLQ: Invalid session ID in auth anomaly. Anomaly: {}. Discarding invalid event.", anomalyId);
                return DlqProcessingResult.DISCARDED;
            } else if (failureReason.contains("MissingAuthContext")) {
                log.error("DLQ: Missing auth context for anomaly validation. Anomaly: {}. Manual review required.", anomalyId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            } else if (failureReason.contains("Duplicate")) {
                log.info("DLQ: Duplicate auth anomaly validation error. Anomaly: {}. Marking as resolved.", anomalyId);
                return DlqProcessingResult.DISCARDED;
            } else if (failureReason.contains("InvalidSignature") || failureReason.contains("TamperedData")) {
                log.error("DLQ: Data integrity issue in auth anomaly. Anomaly: {}. URGENT manual review.", anomalyId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            } else {
                log.error("DLQ: Unknown validation error in auth anomaly. Event: {}, Headers: {}.", event, headers);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }
        } catch (Exception e) {
            log.error("Error handling DLQ auth anomaly validation errors", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    @Override
    protected String getServiceName() {
        return "AuthAnomalyValidationErrorsConsumer";
    }
}
