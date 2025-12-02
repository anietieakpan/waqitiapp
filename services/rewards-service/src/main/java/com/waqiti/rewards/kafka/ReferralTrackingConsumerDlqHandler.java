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
 * DLQ Handler for ReferralTrackingConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class ReferralTrackingConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public ReferralTrackingConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("ReferralTrackingConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.ReferralTrackingConsumer.dlq:ReferralTrackingConsumer.dlq}",
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
            // FIXED: Implement referral tracking recovery logic
            log.warn("Processing DLQ referral tracking event for recovery");

            // Extract event details
            String referralCode = headers.getOrDefault("referralCode", "").toString();
            String referrerId = headers.getOrDefault("referrerId", "").toString();
            String referredUserId = headers.getOrDefault("referredUserId", "").toString();
            String failureReason = headers.getOrDefault("failureReason", "").toString();

            // Attempt recovery based on failure reason
            if (failureReason.contains("Database") || failureReason.contains("Connection")) {
                // Transient database error - safe to retry
                log.info("DLQ: Database error detected, marking for retry. Referral: {}", referralCode);
                return DlqProcessingResult.RETRY;

            } else if (failureReason.contains("Validation") || failureReason.contains("Invalid")) {
                // Data validation error - check if referral code is valid
                if (referralCode == null || referralCode.isBlank()) {
                    log.error("DLQ: Invalid referral code. Event: {}. Discarding.", event);
                    return DlqProcessingResult.DISCARDED;
                }
                log.error("DLQ: Validation error in referral tracking. Code: {}, Referrer: {}. Manual review required.",
                        referralCode, referrerId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;

            } else if (failureReason.contains("Duplicate")) {
                // Duplicate referral tracking - can be safely ignored
                log.info("DLQ: Duplicate referral tracking event detected. Code: {}. Marking as resolved.",
                        referralCode);
                return DlqProcessingResult.DISCARDED;

            } else if (failureReason.contains("UserNotFound") || failureReason.contains("ReferrerNotFound")) {
                // User doesn't exist yet - retry later
                log.warn("DLQ: User not found for referral. Referrer: {}, Referred: {}. Retrying.",
                        referrerId, referredUserId);
                return DlqProcessingResult.RETRY;

            } else {
                // Unknown error - log for investigation
                log.error("DLQ: Unknown error in referral tracking event. Event: {}, Headers: {}. Requires investigation.",
                        event, headers);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }

        } catch (Exception e) {
            log.error("Error handling DLQ referral tracking event", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    @Override
    protected String getServiceName() {
        return "ReferralTrackingConsumer";
    }
}
