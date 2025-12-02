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
 * DLQ Handler for RewardsEarnedEventsConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class RewardsEarnedEventsConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public RewardsEarnedEventsConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("RewardsEarnedEventsConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.RewardsEarnedEventsConsumer.dlq:RewardsEarnedEventsConsumer.dlq}",
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
            // FIXED: Implement rewards earned recovery logic
            log.warn("Processing DLQ rewards earned event for recovery");

            // Extract event details
            String rewardId = headers.getOrDefault("rewardId", "").toString();
            String userId = headers.getOrDefault("userId", "").toString();
            String rewardType = headers.getOrDefault("rewardType", "").toString();
            String failureReason = headers.getOrDefault("failureReason", "").toString();

            // Attempt recovery based on failure reason
            if (failureReason.contains("Database") || failureReason.contains("Connection")) {
                // Transient database error - safe to retry
                log.info("DLQ: Database error detected, marking for retry. Reward: {}", rewardId);
                return DlqProcessingResult.RETRY;

            } else if (failureReason.contains("UserNotFound") || failureReason.contains("WalletNotFound")) {
                // User/wallet doesn't exist yet - retry later (eventual consistency)
                log.warn("DLQ: User/wallet not found for reward. User: {}. Retrying.", userId);
                return DlqProcessingResult.RETRY;

            } else if (failureReason.contains("Validation") || failureReason.contains("Invalid")) {
                // Data validation error - needs manual fix
                log.error("DLQ: Validation error in rewards earned. Reward: {}, User: {}. Manual review required.",
                        rewardId, userId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;

            } else if (failureReason.contains("Duplicate") || failureReason.contains("AlreadyEarned")) {
                // Reward already earned - can be safely ignored
                log.info("DLQ: Duplicate reward earned event detected. Reward: {}. Marking as resolved.",
                        rewardId);
                return DlqProcessingResult.DISCARDED;

            } else if (failureReason.contains("CreditFailed")) {
                // Failed to credit reward - critical, needs retry
                log.error("DLQ: Credit failed for reward. Reward: {}. Retrying.", rewardId);
                return DlqProcessingResult.RETRY;

            } else if (failureReason.contains("RewardExpired")) {
                // Reward expired before processing - discard
                log.info("DLQ: Reward expired. Reward: {}. Discarding.", rewardId);
                return DlqProcessingResult.DISCARDED;

            } else {
                // Unknown error - log for investigation
                log.error("DLQ: Unknown error in rewards earned event. Event: {}, Headers: {}. Requires investigation.",
                        event, headers);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }

        } catch (Exception e) {
            log.error("Error handling DLQ rewards earned event", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    @Override
    protected String getServiceName() {
        return "RewardsEarnedEventsConsumer";
    }
}
