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
 * DLQ Handler for SmsNotificationConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class SmsNotificationConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public SmsNotificationConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("SmsNotificationConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.SmsNotificationConsumer.dlq:SmsNotificationConsumer.dlq}",
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
            log.info("DLQ: Processing SMS notification recovery");
            String phoneNumber = headers.getOrDefault("phoneNumber", "").toString();
            String messageType = headers.getOrDefault("type", "").toString();
            String failureReason = headers.getOrDefault("failureReason", "").toString();

            // Strategy 1: Invalid phone number
            if (failureReason.contains("invalid phone") || failureReason.contains("malformed")) {
                log.warn("DLQ: Invalid phone number: {}", phoneNumber);
                return DlqProcessingResult.PERMANENT_FAILURE;
            }

            // Strategy 2: Phone number not reachable
            if (failureReason.contains("not reachable") || failureReason.contains("no route")) {
                log.warn("DLQ: Phone not reachable: {}", phoneNumber);
                return DlqProcessingResult.RETRY;
            }

            // Strategy 3: Rate limit exceeded
            if (failureReason.contains("rate limit") || failureReason.contains("throttled")) {
                log.warn("DLQ: SMS rate limit exceeded, retry with backoff");
                return DlqProcessingResult.RETRY_WITH_BACKOFF;
            }

            // Strategy 4: SMS provider error (Twilio, AWS SNS, etc.)
            if (failureReason.contains("provider error") || failureReason.contains("API error")) {
                log.warn("DLQ: SMS provider error, retrying: {}", failureReason);
                return DlqProcessingResult.RETRY;
            }

            // Strategy 5: Carrier blocked/spam filtered
            if (failureReason.contains("blocked") || failureReason.contains("spam")) {
                log.error("DLQ: SMS blocked by carrier: {}", phoneNumber);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }

            // Strategy 6: Critical notifications (2FA, fraud alerts) - escalate
            if (messageType.contains("2FA") || messageType.contains("OTP") ||
                messageType.contains("security") || messageType.contains("fraud")) {
                log.error("DLQ: CRITICAL SMS failed: type={}, phone={}", messageType, phoneNumber);
                // Escalate to voice call or email fallback
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }

            // Strategy 7: User opted out
            if (failureReason.contains("opted out") || failureReason.contains("unsubscribed")) {
                log.info("DLQ: User opted out of SMS: {}", phoneNumber);
                return DlqProcessingResult.DISCARDED;
            }

            // Strategy 8: Transient errors
            if (failureReason.contains("timeout") || failureReason.contains("network")) {
                log.info("DLQ: Transient SMS error, retrying");
                return DlqProcessingResult.RETRY;
            }

            // Default: Retry
            log.warn("DLQ: Unknown SMS failure, retrying: {}", failureReason);
            return DlqProcessingResult.RETRY;

        } catch (Exception e) {
            log.error("DLQ: Error handling SMS notification event", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    @Override
    protected String getServiceName() {
        return "SmsNotificationConsumer";
    }
}
