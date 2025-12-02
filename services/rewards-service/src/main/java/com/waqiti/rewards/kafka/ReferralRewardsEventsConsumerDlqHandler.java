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
 * DLQ Handler for ReferralRewardsEventsConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class ReferralRewardsEventsConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public ReferralRewardsEventsConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("ReferralRewardsEventsConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.ReferralRewardsEventsConsumer.dlq:ReferralRewardsEventsConsumer.dlq}",
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
            // FIXED: Implement referral rewards recovery logic
            log.warn("Processing DLQ referral rewards event for recovery");

            // Extract event details
            String rewardId = headers.getOrDefault("rewardId", "").toString();
            String referrerId = headers.getOrDefault("referrerId", "").toString();
            String referredUserId = headers.getOrDefault("referredUserId", "").toString();
            String referralCode = headers.getOrDefault("referralCode", "").toString();
            String failureReason = headers.getOrDefault("failureReason", "").toString();

            // Attempt recovery based on failure reason
            if (failureReason.contains("Database") || failureReason.contains("Connection")) {
                // Transient database error - safe to retry
                log.info("DLQ: Database error detected, marking for retry. Reward: {}", rewardId);
                return DlqProcessingResult.RETRY;

            } else if (failureReason.contains("ReferrerNotFound") || failureReason.contains("ReferredUserNotFound")) {
                // User doesn't exist yet - retry later (eventual consistency)
                log.warn("DLQ: Referrer/referred user not found. Referrer: {}, Referred: {}. Retrying.",
                        referrerId, referredUserId);
                return DlqProcessingResult.RETRY;

            } else if (failureReason.contains("Validation") || failureReason.contains("Invalid")) {
                // Data validation error - needs manual fix
                log.error("DLQ: Validation error in referral rewards. Reward: {}, Referral: {}. Manual review required.",
                        rewardId, referralCode);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;

            } else if (failureReason.contains("Duplicate") || failureReason.contains("AlreadyRewarded")) {
                // Reward already granted - can be safely ignored
                log.info("DLQ: Duplicate referral reward event detected. Reward: {}. Marking as resolved.",
                        rewardId);
                return DlqProcessingResult.DISCARDED;

            } else if (failureReason.contains("ReferralInvalid") || failureReason.contains("ReferralExpired")) {
                // Referral code invalid or expired - discard
                log.info("DLQ: Invalid/expired referral. Code: {}. Discarding.", referralCode);
                return DlqProcessingResult.DISCARDED;

            } else if (failureReason.contains("WalletCreditFailed")) {
                // Failed to credit wallet - critical, needs retry
                log.error("DLQ: Wallet credit failed for referral reward. Reward: {}. Retrying.", rewardId);
                return DlqProcessingResult.RETRY;

            } else if (failureReason.contains("SelfReferral")) {
                // User tried to refer themselves - discard
                log.warn("DLQ: Self-referral detected. User: {}. Discarding.", referrerId);
                return DlqProcessingResult.DISCARDED;

            } else {
                // Unknown error - log for investigation
                log.error("DLQ: Unknown error in referral rewards event. Event: {}, Headers: {}. Requires investigation.",
                        event, headers);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }

        } catch (Exception e) {
            log.error("Error handling DLQ referral rewards event", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    @Override
    protected String getServiceName() {
        return "ReferralRewardsEventsConsumer";
    }
}
