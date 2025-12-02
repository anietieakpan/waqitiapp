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
 * DLQ Handler for DeviceFingerprintEventsConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class DeviceFingerprintEventsConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public DeviceFingerprintEventsConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("DeviceFingerprintEventsConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.DeviceFingerprintEventsConsumer.dlq:DeviceFingerprintEventsConsumer.dlq}",
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
            // FIXED: Implement device fingerprint recovery logic
            log.warn("Processing DLQ device fingerprint event");

            String deviceId = headers.getOrDefault("deviceId", "").toString();
            String userId = headers.getOrDefault("userId", "").toString();
            String fingerprintHash = headers.getOrDefault("fingerprintHash", "").toString();
            String trustScore = headers.getOrDefault("trustScore", "0").toString();
            String failureReason = headers.getOrDefault("failureReason", "").toString();

            if (failureReason.contains("Database") || failureReason.contains("Connection")) {
                log.info("DLQ: Database error, marking for retry. Device: {}", deviceId);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("Duplicate") || failureReason.contains("AlreadyRegistered")) {
                log.info("DLQ: Device fingerprint already registered. Device: {}. Marking as resolved.", deviceId);
                return DlqProcessingResult.DISCARDED;
            } else if (failureReason.contains("SuspiciousDevice") && Double.parseDouble(trustScore) < 30) {
                log.error("DLQ: Low trust device fingerprint. Device: {}, Score: {}. Manual review required.", deviceId, trustScore);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            } else if (failureReason.contains("FingerprintServiceDown")) {
                log.warn("DLQ: Device fingerprint service unavailable. Device: {}. Retrying.", deviceId);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("UserNotFound")) {
                log.warn("DLQ: User not found for device fingerprint. User: {}. Retrying.", userId);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("Validation") || failureReason.contains("Invalid")) {
                log.error("DLQ: Validation error in device fingerprint. Device: {}. Manual review required.", deviceId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            } else {
                log.error("DLQ: Unknown error in device fingerprint. Event: {}, Headers: {}.", event, headers);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }
        } catch (Exception e) {
            log.error("Error handling DLQ device fingerprint event", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    @Override
    protected String getServiceName() {
        return "DeviceFingerprintEventsConsumer";
    }
}
