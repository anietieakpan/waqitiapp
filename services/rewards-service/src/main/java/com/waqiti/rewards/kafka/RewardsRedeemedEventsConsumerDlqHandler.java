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
 * DLQ Handler for RewardsRedeemedEventsConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class RewardsRedeemedEventsConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public RewardsRedeemedEventsConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("RewardsRedeemedEventsConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.RewardsRedeemedEventsConsumer.dlq:RewardsRedeemedEventsConsumer.dlq}",
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
            // FIXED: Implement rewards redemption recovery logic
            log.warn("Processing DLQ rewards redeemed event for recovery");

            // Extract event details
            String rewardId = headers.getOrDefault("rewardId", "").toString();
            String userId = headers.getOrDefault("userId", "").toString();
            String redemptionId = headers.getOrDefault("redemptionId", "").toString();
            String failureReason = headers.getOrDefault("failureReason", "").toString();

            // Attempt recovery based on failure reason
            if (failureReason.contains("Database") || failureReason.contains("Connection")) {
                // Transient database error - safe to retry
                log.info("DLQ: Database error detected, marking for retry. Redemption: {}", redemptionId);
                return DlqProcessingResult.RETRY;

            } else if (failureReason.contains("InsufficientBalance") || failureReason.contains("InsufficientPoints")) {
                // User tried to redeem more than available - permanent failure
                log.error("DLQ: Insufficient balance for redemption. User: {}, Reward: {}. Discarding.",
                        userId, rewardId);
                return DlqProcessingResult.DISCARDED;

            } else if (failureReason.contains("RewardExpired") || failureReason.contains("RewardNotAvailable")) {
                // Reward no longer available - discard event
                log.warn("DLQ: Reward expired or not available. Reward: {}. Discarding.", rewardId);
                return DlqProcessingResult.DISCARDED;

            } else if (failureReason.contains("Validation") || failureReason.contains("Invalid")) {
                // Data validation error - needs manual fix
                log.error("DLQ: Validation error in rewards redemption. User: {}, Reward: {}. Manual review required.",
                        userId, rewardId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;

            } else if (failureReason.contains("Duplicate")) {
                // Duplicate redemption event - can be safely ignored
                log.info("DLQ: Duplicate redemption event detected. Redemption: {}. Marking as resolved.",
                        redemptionId);
                return DlqProcessingResult.DISCARDED;

            } else {
                // Unknown error - log for investigation
                log.error("DLQ: Unknown error in rewards redemption event. Event: {}, Headers: {}. Requires investigation.",
                        event, headers);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }

        } catch (Exception e) {
            log.error("Error handling DLQ rewards redeemed event", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    @Override
    protected String getServiceName() {
        return "RewardsRedeemedEventsConsumer";
    }
}
