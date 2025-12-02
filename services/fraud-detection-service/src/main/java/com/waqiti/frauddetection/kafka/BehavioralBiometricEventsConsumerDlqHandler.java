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
 * DLQ Handler for BehavioralBiometricEventsConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class BehavioralBiometricEventsConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public BehavioralBiometricEventsConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("BehavioralBiometricEventsConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.BehavioralBiometricEventsConsumer.dlq:BehavioralBiometricEventsConsumer.dlq}",
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
            // FIXED: Implement behavioral biometric recovery logic
            log.warn("Processing DLQ behavioral biometric event");

            String biometricId = headers.getOrDefault("biometricId", "").toString();
            String userId = headers.getOrDefault("userId", "").toString();
            String biometricType = headers.getOrDefault("biometricType", "").toString();
            String matchScore = headers.getOrDefault("matchScore", "0").toString();
            String failureReason = headers.getOrDefault("failureReason", "").toString();

            if (failureReason.contains("Database") || failureReason.contains("Connection")) {
                log.info("DLQ: Database error, marking for retry. Biometric: {}", biometricId);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("Duplicate") || failureReason.contains("AlreadyProcessed")) {
                log.info("DLQ: Biometric already processed. Biometric: {}. Marking as resolved.", biometricId);
                return DlqProcessingResult.DISCARDED;
            } else if (failureReason.contains("BiometricMismatch") && Double.parseDouble(matchScore) < 40) {
                log.warn("DLQ: Low biometric match score. User: {}, Score: {}. Manual review required.", userId, matchScore);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            } else if (failureReason.contains("BiometricServiceDown")) {
                log.warn("DLQ: Biometric service unavailable. Biometric: {}. Retrying.", biometricId);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("UserNotFound")) {
                log.warn("DLQ: User not found for biometric. User: {}. Retrying.", userId);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("Validation") || failureReason.contains("Invalid")) {
                log.error("DLQ: Validation error in behavioral biometric. Biometric: {}. Manual review required.", biometricId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            } else {
                log.error("DLQ: Unknown error in behavioral biometric. Event: {}, Headers: {}.", event, headers);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }
        } catch (Exception e) {
            log.error("Error handling DLQ behavioral biometric event", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    @Override
    protected String getServiceName() {
        return "BehavioralBiometricEventsConsumer";
    }
}
