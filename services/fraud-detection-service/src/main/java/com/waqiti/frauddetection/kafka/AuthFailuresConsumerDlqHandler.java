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
 * DLQ Handler for AuthFailuresConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class AuthFailuresConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public AuthFailuresConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("AuthFailuresConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.AuthFailuresConsumer.dlq:AuthFailuresConsumer.dlq}",
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
            // FIXED: Implement auth failure tracking recovery logic
            log.warn("Processing DLQ auth failures event");

            String userId = headers.getOrDefault("userId", "").toString();
            String ipAddress = headers.getOrDefault("ipAddress", "").toString();
            String failureCount = headers.getOrDefault("failureCount", "0").toString();
            String timeWindow = headers.getOrDefault("timeWindow", "").toString();
            String failureType = headers.getOrDefault("failureType", "").toString();
            String failureReason = headers.getOrDefault("failureReason", "").toString();

            if (failureReason.contains("Database") || failureReason.contains("Connection")) {
                log.info("DLQ: Database error, marking for retry. User: {}", userId);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("Duplicate") || failureReason.contains("AlreadyTracked")) {
                log.info("DLQ: Auth failure already tracked. User: {}. Marking as resolved.", userId);
                return DlqProcessingResult.DISCARDED;
            } else if (failureReason.contains("BruteForceDetected") && Integer.parseInt(failureCount) > 10) {
                log.error("DLQ: Brute force attack detected. User: {}, IP: {}, Count: {}. Retrying.", userId, ipAddress, failureCount);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("AccountLockFailed")) {
                log.error("DLQ: Failed to lock account after auth failures. User: {}. Retrying.", userId);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("UserNotFound")) {
                log.warn("DLQ: User not found for auth failure tracking. User: {}. Retrying.", userId);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("Validation") || failureReason.contains("Invalid")) {
                log.error("DLQ: Validation error in auth failures. User: {}. Manual review required.", userId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            } else {
                log.error("DLQ: Unknown error in auth failures. Event: {}, Headers: {}.", event, headers);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }
        } catch (Exception e) {
            log.error("Error handling DLQ auth failures event", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    @Override
    protected String getServiceName() {
        return "AuthFailuresConsumer";
    }
}
