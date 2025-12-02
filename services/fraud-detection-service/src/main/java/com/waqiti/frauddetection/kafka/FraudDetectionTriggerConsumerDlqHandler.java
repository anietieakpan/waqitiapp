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
 * DLQ Handler for FraudDetectionTriggerConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class FraudDetectionTriggerConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public FraudDetectionTriggerConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("FraudDetectionTriggerConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.FraudDetectionTriggerConsumer.dlq:FraudDetectionTriggerConsumer.dlq}",
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
            // FIXED: Implement fraud detection trigger recovery logic
            log.warn("Processing DLQ fraud detection trigger event");

            String triggerId = headers.getOrDefault("triggerId", "").toString();
            String triggerType = headers.getOrDefault("triggerType", "").toString();
            String entityId = headers.getOrDefault("entityId", "").toString();
            String failureReason = headers.getOrDefault("failureReason", "").toString();

            if (failureReason.contains("Database") || failureReason.contains("Connection")) {
                log.info("DLQ: Database error, marking for retry. Trigger: {}", triggerId);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("Duplicate") || failureReason.contains("AlreadyTriggered")) {
                log.info("DLQ: Fraud detection already triggered. Trigger: {}. Marking as resolved.", triggerId);
                return DlqProcessingResult.DISCARDED;
            } else if (failureReason.contains("DetectionServiceDown")) {
                log.warn("DLQ: Fraud detection service unavailable. Trigger: {}. Retrying.", triggerId);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("EntityNotFound")) {
                log.warn("DLQ: Entity not found for fraud trigger. Entity: {}. Retrying.", entityId);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("Validation") || failureReason.contains("Invalid")) {
                log.error("DLQ: Validation error in fraud detection trigger. Trigger: {}. Manual review required.", triggerId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            } else {
                log.error("DLQ: Unknown error in fraud detection trigger. Event: {}, Headers: {}.", event, headers);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }
        } catch (Exception e) {
            log.error("Error handling DLQ fraud detection trigger event", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    @Override
    protected String getServiceName() {
        return "FraudDetectionTriggerConsumer";
    }
}
