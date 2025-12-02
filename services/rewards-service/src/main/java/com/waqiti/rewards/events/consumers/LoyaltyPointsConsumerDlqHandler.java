package com.waqiti.rewards.events.consumers;

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
 * DLQ Handler for LoyaltyPointsConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class LoyaltyPointsConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public LoyaltyPointsConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("LoyaltyPointsConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.LoyaltyPointsConsumer.dlq:LoyaltyPointsConsumer.dlq}",
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
            // FIXED: Implement loyalty points consumer recovery logic (duplicate handler)
            log.warn("Processing DLQ loyalty points consumer event for recovery");

            // Extract event details
            String userId = headers.getOrDefault("userId", "").toString();
            String pointsId = headers.getOrDefault("pointsId", "").toString();
            String failureReason = headers.getOrDefault("failureReason", "").toString();

            // Attempt recovery based on failure reason
            if (failureReason.contains("Database") || failureReason.contains("Connection")) {
                log.info("DLQ: Database error detected, marking for retry. User: {}", userId);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("InsufficientPoints")) {
                log.error("DLQ: Insufficient loyalty points. User: {}. Discarding.", userId);
                return DlqProcessingResult.DISCARDED;
            } else if (failureReason.contains("Duplicate")) {
                log.info("DLQ: Duplicate loyalty points event detected. User: {}. Marking as resolved.", userId);
                return DlqProcessingResult.DISCARDED;
            } else if (failureReason.contains("AccountNotFound")) {
                log.warn("DLQ: Loyalty account not found. User: {}. Retrying.", userId);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("Validation") || failureReason.contains("Invalid")) {
                log.error("DLQ: Validation error in loyalty points. User: {}. Manual review required.", userId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            } else {
                log.error("DLQ: Unknown error in loyalty points event. Event: {}, Headers: {}. Requires investigation.",
                        event, headers);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }

        } catch (Exception e) {
            log.error("Error handling DLQ loyalty points event", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    @Override
    protected String getServiceName() {
        return "LoyaltyPointsConsumer";
    }
}
