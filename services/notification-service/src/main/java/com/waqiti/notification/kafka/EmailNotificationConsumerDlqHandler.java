package com.waqiti.notification.kafka;

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
 * DLQ Handler for EmailNotificationConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class EmailNotificationConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public EmailNotificationConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("EmailNotificationConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.EmailNotificationConsumer.dlq:EmailNotificationConsumer.dlq}",
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
            log.info("DLQ: Processing Email notification recovery");

            String recipient = headers.getOrDefault("recipient", "").toString();
            String subject = headers.getOrDefault("subject", "").toString();
            String notificationType = headers.getOrDefault("type", "").toString();
            String failureReason = headers.getOrDefault("failureReason", "").toString();

            // Strategy 1: Invalid email address
            if (failureReason.contains("invalid email") || failureReason.contains("malformed")) {
                log.warn("DLQ: Invalid email address: {}", recipient);
                // Mark as permanent failure, update user profile
                return DlqProcessingResult.PERMANENT_FAILURE;
            }

            // Strategy 2: Email bounce (hard bounce)
            if (failureReason.contains("bounce") && failureReason.contains("hard")) {
                log.warn("DLQ: Hard bounce for email: {}", recipient);
                // Mark email as invalid, suppress future sends
                return DlqProcessingResult.PERMANENT_FAILURE;
            }

            // Strategy 3: Email bounce (soft bounce) - retry
            if (failureReason.contains("bounce") && failureReason.contains("soft")) {
                log.info("DLQ: Soft bounce for email, retrying: {}", recipient);
                return DlqProcessingResult.RETRY;
            }

            // Strategy 4: Rate limit exceeded
            if (failureReason.contains("rate limit") || failureReason.contains("throttled")) {
                log.warn("DLQ: Email rate limit exceeded, retrying with backoff: {}", recipient);
                return DlqProcessingResult.RETRY_WITH_BACKOFF;
            }

            // Strategy 5: Email provider error (SendGrid, SES, etc.)
            if (failureReason.contains("provider error") || failureReason.contains("API error")) {
                log.warn("DLQ: Email provider error, retrying: {}", failureReason);
                return DlqProcessingResult.RETRY;
            }

            // Strategy 6: Template rendering error
            if (failureReason.contains("template") || failureReason.contains("rendering")) {
                log.error("DLQ: Email template error: {}", failureReason);
                // Needs developer fix
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }

            // Strategy 7: User unsubscribed
            if (failureReason.contains("unsubscribed") || failureReason.contains("opted out")) {
                log.info("DLQ: User unsubscribed from emails: {}", recipient);
                return DlqProcessingResult.DISCARDED;
            }

            // Strategy 8: Critical notification (payment, security) - escalate
            if (notificationType.contains("payment") || notificationType.contains("security") ||
                notificationType.contains("fraud")) {
                log.error("DLQ: CRITICAL notification failed: type={}, recipient={}", notificationType, recipient);
                // Try SMS fallback or manual contact
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }

            // Strategy 9: Non-critical notification - acceptable failure
            if (notificationType.contains("marketing") || notificationType.contains("promotional")) {
                log.info("DLQ: Non-critical email failed, discarding: type={}", notificationType);
                return DlqProcessingResult.DISCARDED;
            }

            // Strategy 10: Transient errors
            if (failureReason.contains("timeout") || failureReason.contains("network")) {
                log.info("DLQ: Transient email error, retrying: {}", failureReason);
                return DlqProcessingResult.RETRY;
            }

            // Default: Retry with limit
            log.warn("DLQ: Unknown email failure, retrying: {}", failureReason);
            return DlqProcessingResult.RETRY;

        } catch (Exception e) {
            log.error("DLQ: Error handling email notification event", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    @Override
    protected String getServiceName() {
        return "EmailNotificationConsumer";
    }
}
