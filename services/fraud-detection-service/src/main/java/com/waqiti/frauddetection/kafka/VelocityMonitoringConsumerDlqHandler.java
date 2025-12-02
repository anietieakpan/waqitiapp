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
 * DLQ Handler for VelocityMonitoringConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class VelocityMonitoringConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public VelocityMonitoringConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("VelocityMonitoringConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.VelocityMonitoringConsumer.dlq:VelocityMonitoringConsumer.dlq}",
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
            // FIXED: Implement velocity monitoring recovery logic
            log.warn("Processing DLQ velocity monitoring event");

            String userId = headers.getOrDefault("userId", "").toString();
            String velocityType = headers.getOrDefault("velocityType", "").toString();
            String threshold = headers.getOrDefault("threshold", "0").toString();
            String actualValue = headers.getOrDefault("actualValue", "0").toString();
            String timeWindow = headers.getOrDefault("timeWindow", "").toString();
            String failureReason = headers.getOrDefault("failureReason", "").toString();

            if (failureReason.contains("Database") || failureReason.contains("Connection")) {
                log.info("DLQ: Database error, marking for retry. User: {}", userId);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("Duplicate") || failureReason.contains("AlreadyMonitored")) {
                log.info("DLQ: Velocity already monitored. User: {}. Marking as resolved.", userId);
                return DlqProcessingResult.DISCARDED;
            } else if (failureReason.contains("ThresholdExceeded") && Double.parseDouble(actualValue) > Double.parseDouble(threshold) * 2) {
                log.error("DLQ: Critical velocity threshold breach. User: {}, Type: {}, Value: {}. Retrying.", userId, velocityType, actualValue);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("BlockingFailed")) {
                log.error("DLQ: Failed to block user for velocity violation. User: {}. Retrying.", userId);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("UserNotFound")) {
                log.warn("DLQ: User not found for velocity check. User: {}. Retrying.", userId);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("Validation") || failureReason.contains("Invalid")) {
                log.error("DLQ: Validation error in velocity monitoring. User: {}. Manual review required.", userId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            } else {
                log.error("DLQ: Unknown error in velocity monitoring. Event: {}, Headers: {}.", event, headers);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }
        } catch (Exception e) {
            log.error("Error handling DLQ velocity monitoring event", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    @Override
    protected String getServiceName() {
        return "VelocityMonitoringConsumer";
    }
}
