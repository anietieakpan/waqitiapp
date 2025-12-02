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
 * DLQ Handler for PasswordResetEventsConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class PasswordResetEventsConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public PasswordResetEventsConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("PasswordResetEventsConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.PasswordResetEventsConsumer.dlq:PasswordResetEventsConsumer.dlq}",
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
            log.info("DLQ: Processing password reset recovery");
            String userId = headers.getOrDefault("userId", "").toString();
            String resetToken = headers.getOrDefault("resetToken", "").toString();
            String failureReason = headers.getOrDefault("failureReason", "").toString();

            // Strategy 1: User not found
            if (failureReason.contains("user not found")) {
                log.warn("DLQ: Password reset for non-existent user: {}", userId);
                return DlqProcessingResult.DISCARDED;
            }

            // Strategy 2: Email delivery failure
            if (failureReason.contains("email") || failureReason.contains("notification")) {
                log.warn("DLQ: Password reset email failed, retrying: userId={}", userId);
                // Critical - user needs this email
                return DlqProcessingResult.RETRY;
            }

            // Strategy 3: Token generation failure
            if (failureReason.contains("token generation")) {
                log.warn("DLQ: Reset token generation failed, retrying: userId={}", userId);
                return DlqProcessingResult.RETRY;
            }

            // Strategy 4: Rate limit exceeded (security)
            if (failureReason.contains("rate limit") || failureReason.contains("too many requests")) {
                log.warn("DLQ: Password reset rate limit hit: userId={}", userId);
                // Security measure - user trying too many resets
                return DlqProcessingResult.DISCARDED;
            }

            // Strategy 5: Account locked/suspended
            if (failureReason.contains("locked") || failureReason.contains("suspended")) {
                log.info("DLQ: Password reset for locked account: userId={}", userId);
                // Account locked - should not allow reset
                return DlqProcessingResult.DISCARDED;
            }

            // Strategy 6: Duplicate reset request
            if (failureReason.contains("duplicate") || failureReason.contains("already exists")) {
                log.info("DLQ: Duplicate password reset: userId={}", userId);
                return DlqProcessingResult.DISCARDED;
            }

            // Strategy 7: Token storage failure
            if (failureReason.contains("storage") || failureReason.contains("database")) {
                log.error("DLQ: Failed to store reset token: userId={}", userId);
                return DlqProcessingResult.RETRY;
            }

            // Strategy 8: Audit logging failure (non-critical)
            if (failureReason.contains("audit")) {
                log.warn("DLQ: Audit logging failed for password reset: userId={}", userId);
                // Password reset successful, just audit failed
                return DlqProcessingResult.SUCCESS;
            }

            // Default: Retry (password resets are critical for user access)
            log.warn("DLQ: Unknown password reset failure, retrying: {}", failureReason);
            return DlqProcessingResult.RETRY;

        } catch (Exception e) {
            log.error("DLQ: Error handling password reset event", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    @Override
    protected String getServiceName() {
        return "PasswordResetEventsConsumer";
    }
}
