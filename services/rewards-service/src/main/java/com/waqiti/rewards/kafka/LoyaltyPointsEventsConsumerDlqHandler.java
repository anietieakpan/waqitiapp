package com.waqiti.rewards.kafka;

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
 * DLQ Handler for LoyaltyPointsEventsConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class LoyaltyPointsEventsConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public LoyaltyPointsEventsConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("LoyaltyPointsEventsConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.LoyaltyPointsEventsConsumer.dlq:LoyaltyPointsEventsConsumer.dlq}",
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
            // FIXED: Implement loyalty points recovery logic
            log.warn("Processing DLQ loyalty points event for recovery");

            // Extract event details
            String userId = headers.getOrDefault("userId", "").toString();
            String eventType = headers.getOrDefault("eventType", "").toString();
            String pointsAmount = headers.getOrDefault("pointsAmount", "0").toString();
            String failureReason = headers.getOrDefault("failureReason", "").toString();

            // Attempt recovery based on failure reason
            if (failureReason.contains("Database") || failureReason.contains("Connection")) {
                // Transient database error - safe to retry
                log.info("DLQ: Database error detected, marking for retry. User: {}", userId);
                return DlqProcessingResult.RETRY;

            } else if (failureReason.contains("InsufficientPoints")) {
                // User tried to redeem more points than available - permanent failure
                log.error("DLQ: Insufficient loyalty points for redemption. User: {}. Discarding.", userId);
                return DlqProcessingResult.DISCARDED;

            } else if (failureReason.contains("Validation") || failureReason.contains("Invalid")) {
                // Data validation error - needs manual fix
                log.error("DLQ: Validation error in loyalty points. User: {}, Event: {}. Manual review required.",
                        userId, eventType);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;

            } else if (failureReason.contains("Duplicate")) {
                // Duplicate points event - can be safely ignored
                log.info("DLQ: Duplicate loyalty points event detected. User: {}. Marking as resolved.", userId);
                return DlqProcessingResult.DISCARDED;

            } else if (failureReason.contains("UserNotFound") || failureReason.contains("LoyaltyAccountNotFound")) {
                // User/account doesn't exist yet - retry later (eventual consistency)
                log.warn("DLQ: User/loyalty account not found. User: {}. Retrying.", userId);
                return DlqProcessingResult.RETRY;

            } else if (failureReason.contains("PointsExpired")) {
                // Points expired before processing - discard
                log.info("DLQ: Loyalty points expired. User: {}. Discarding.", userId);
                return DlqProcessingResult.DISCARDED;

            } else {
                // Unknown error - log for investigation
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
        return "LoyaltyPointsEventsConsumer";
    }
}
