package com.waqiti.user.kafka;

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
 * DLQ Handler for AccountVerificationConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class AccountVerificationConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public AccountVerificationConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("AccountVerificationConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.AccountVerificationConsumer.dlq:AccountVerificationConsumer.dlq}",
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
            log.info("DLQ: Processing account verification recovery");
            String userId = headers.getOrDefault("userId", "").toString();
            String verificationType = headers.getOrDefault("verificationType", "").toString();
            String failureReason = headers.getOrDefault("failureReason", "").toString();

            // Strategy 1: User not found
            if (failureReason.contains("user not found")) {
                log.error("DLQ: Verification for non-existent user: {}", userId);
                return DlqProcessingResult.PERMANENT_FAILURE;
            }

            // Strategy 2: Email verification - delivery failure
            if (verificationType.contains("email") && failureReason.contains("notification")) {
                log.warn("DLQ: Email verification failed, retrying: userId={}", userId);
                return DlqProcessingResult.RETRY;
            }

            // Strategy 3: Phone verification - SMS delivery failure
            if (verificationType.contains("phone") && failureReason.contains("SMS")) {
                log.warn("DLQ: Phone verification SMS failed, retrying: userId={}", userId);
                return DlqProcessingResult.RETRY;
            }

            // Strategy 4: ID verification - document processing failure
            if (verificationType.contains("ID") || verificationType.contains("identity")) {
                log.warn("DLQ: ID verification processing failed: userId={}", userId);
                // Needs manual review for identity documents
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }

            // Strategy 5: Already verified (duplicate)
            if (failureReason.contains("already verified") || failureReason.contains("duplicate")) {
                log.info("DLQ: Account already verified: userId={}", userId);
                return DlqProcessingResult.DISCARDED;
            }

            // Strategy 6: Account status update failure
            if (failureReason.contains("status update")) {
                log.error("DLQ: Failed to update account status after verification: userId={}", userId);
                // Critical - user verified but account not updated
                return DlqProcessingResult.RETRY;
            }

            // Strategy 7: Verification token expired
            if (failureReason.contains("expired") || failureReason.contains("timeout")) {
                log.warn("DLQ: Verification token expired: userId={}", userId);
                // Generate new token and resend
                return DlqProcessingResult.RETRY;
            }

            // Strategy 8: Compliance check failure
            if (failureReason.contains("compliance") || failureReason.contains("KYC")) {
                log.warn("DLQ: Compliance check failed during verification: userId={}", userId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }

            // Default: Retry
            log.warn("DLQ: Unknown verification failure, retrying: {}", failureReason);
            return DlqProcessingResult.RETRY;

        } catch (Exception e) {
            log.error("DLQ: Error handling account verification event", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    @Override
    protected String getServiceName() {
        return "AccountVerificationConsumer";
    }
}
