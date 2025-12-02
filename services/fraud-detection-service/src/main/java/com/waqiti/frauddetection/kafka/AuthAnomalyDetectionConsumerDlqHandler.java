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
 * DLQ Handler for AuthAnomalyDetectionConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class AuthAnomalyDetectionConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public AuthAnomalyDetectionConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("AuthAnomalyDetectionConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.AuthAnomalyDetectionConsumer.dlq:AuthAnomalyDetectionConsumer.dlq}",
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
            // FIXED: Implement auth anomaly detection recovery logic
            log.warn("Processing DLQ auth anomaly detection event");

            String userId = headers.getOrDefault("userId", "").toString();
            String sessionId = headers.getOrDefault("sessionId", "").toString();
            String anomalyType = headers.getOrDefault("anomalyType", "").toString();
            String anomalyScore = headers.getOrDefault("anomalyScore", "0").toString();
            String failureReason = headers.getOrDefault("failureReason", "").toString();

            if (failureReason.contains("Database") || failureReason.contains("Connection")) {
                log.info("DLQ: Database error, marking for retry. User: {}", userId);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("Duplicate") || failureReason.contains("AlreadyDetected")) {
                log.info("DLQ: Auth anomaly already detected. User: {}. Marking as resolved.", userId);
                return DlqProcessingResult.DISCARDED;
            } else if (failureReason.contains("SessionRevocationFailed") && Double.parseDouble(anomalyScore) > 80) {
                log.error("DLQ: Failed to revoke high-risk session. User: {}, Score: {}. Retrying.", userId, anomalyScore);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("UserNotFound")) {
                log.warn("DLQ: User not found for auth anomaly. User: {}. Retrying.", userId);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("MLModelNotAvailable")) {
                log.warn("DLQ: ML anomaly model unavailable. User: {}. Retrying.", userId);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("Validation") || failureReason.contains("Invalid")) {
                log.error("DLQ: Validation error in auth anomaly. User: {}. Manual review required.", userId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            } else {
                log.error("DLQ: Unknown error in auth anomaly detection. Event: {}, Headers: {}.", event, headers);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }
        } catch (Exception e) {
            log.error("Error handling DLQ auth anomaly detection event", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    @Override
    protected String getServiceName() {
        return "AuthAnomalyDetectionConsumer";
    }
}
