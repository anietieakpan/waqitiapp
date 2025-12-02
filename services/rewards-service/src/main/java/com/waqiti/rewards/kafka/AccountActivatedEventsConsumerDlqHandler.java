package com.waqiti.rewards.kafka;

import com.waqiti.common.kafka.BaseDlqConsumer;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.Map;

/**
 * DLQ Handler for AccountActivatedEventsConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class AccountActivatedEventsConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public AccountActivatedEventsConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("AccountActivatedEventsConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.AccountActivatedEventsConsumer.dlq:AccountActivatedEventsConsumer.dlq}",
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
            // FIXED: Implement account activation recovery logic
            log.warn("Processing DLQ account activated event for recovery");

            // Extract event details
            String userId = headers.getOrDefault("userId", "").toString();
            String accountId = headers.getOrDefault("accountId", "").toString();
            String accountType = headers.getOrDefault("accountType", "").toString();
            String failureReason = headers.getOrDefault("failureReason", "").toString();

            // Attempt recovery based on failure reason
            if (failureReason.contains("Database") || failureReason.contains("Connection")) {
                // Transient database error - safe to retry
                log.info("DLQ: Database error detected, marking for retry. User: {}", userId);
                return DlqProcessingResult.RETRY;

            } else if (failureReason.contains("Validation") || failureReason.contains("Invalid")) {
                // Data validation error - needs manual fix
                log.error("DLQ: Validation error in account activation. User: {}, Account: {}. Manual review required.",
                        userId, accountId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;

            } else if (failureReason.contains("Duplicate") || failureReason.contains("AlreadyActivated")) {
                // Account already activated - can be safely ignored
                log.info("DLQ: Duplicate activation event detected. User: {}. Marking as resolved.", userId);
                return DlqProcessingResult.DISCARDED;

            } else if (failureReason.contains("UserNotFound")) {
                // User doesn't exist - retry later (eventual consistency)
                log.warn("DLQ: User not found for account activation. User: {}. Retrying.", userId);
                return DlqProcessingResult.RETRY;

            } else if (failureReason.contains("WelcomeBonusFailed")) {
                // Welcome bonus grant failed but account activated - retry bonus only
                log.warn("DLQ: Welcome bonus failed for activated account. User: {}. Retrying.", userId);
                return DlqProcessingResult.RETRY;

            } else {
                // Unknown error - log for investigation
                log.error("DLQ: Unknown error in account activation event. Event: {}, Headers: {}. Requires investigation.",
                        event, headers);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }

        } catch (Exception e) {
            log.error("Error handling DLQ account activated event", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    @Override
    protected String getServiceName() {
        return "AccountActivatedEventsConsumer";
    }
}
