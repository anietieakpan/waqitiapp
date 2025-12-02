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
 * DLQ Handler for FraudDetectionConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class FraudDetectionConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public FraudDetectionConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("FraudDetectionConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.FraudDetectionConsumer.dlq:FraudDetectionConsumer.dlq}",
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
            // FIXED: Implement fraud detection recovery logic
            log.warn("Processing DLQ fraud detection event");

            String detectionId = headers.getOrDefault("detectionId", "").toString();
            String entityId = headers.getOrDefault("entityId", "").toString();
            String entityType = headers.getOrDefault("entityType", "").toString();
            String fraudScore = headers.getOrDefault("fraudScore", "0").toString();
            String fraudType = headers.getOrDefault("fraudType", "").toString();
            String failureReason = headers.getOrDefault("failureReason", "").toString();

            if (failureReason.contains("Database") || failureReason.contains("Connection")) {
                log.info("DLQ: Database error, marking for retry. Detection: {}", detectionId);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("Duplicate") || failureReason.contains("AlreadyDetected")) {
                log.info("DLQ: Fraud already detected. Detection: {}. Marking as resolved.", detectionId);
                return DlqProcessingResult.DISCARDED;
            } else if (failureReason.contains("HighRiskNotBlocked") && Double.parseDouble(fraudScore) > 90) {
                log.error("DLQ: CRITICAL - High risk fraud not blocked. Detection: {}, Score: {}. Retrying.", detectionId, fraudScore);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("MLModelUnavailable") || failureReason.contains("ScoringServiceDown")) {
                log.warn("DLQ: Fraud detection model unavailable. Detection: {}. Retrying.", detectionId);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("EntityNotFound")) {
                log.warn("DLQ: Entity not found for fraud detection. Entity: {}, Type: {}. Retrying.", entityId, entityType);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("Validation") || failureReason.contains("Invalid")) {
                log.error("DLQ: Validation error in fraud detection. Detection: {}. Manual review required.", detectionId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            } else {
                log.error("DLQ: Unknown error in fraud detection. Event: {}, Headers: {}.", event, headers);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }
        } catch (Exception e) {
            log.error("Error handling DLQ fraud detection event", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    @Override
    protected String getServiceName() {
        return "FraudDetectionConsumer";
    }
}
