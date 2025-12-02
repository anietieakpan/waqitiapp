package com.waqiti.rewards.events.consumers;

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
 * DLQ Handler for WalletCreatedEventsConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class WalletCreatedEventsConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public WalletCreatedEventsConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("WalletCreatedEventsConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.WalletCreatedEventsConsumer.dlq:WalletCreatedEventsConsumer.dlq}",
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
            // FIXED: Implement wallet created recovery logic
            log.warn("Processing DLQ wallet created event for recovery");

            // Extract event details
            String walletId = headers.getOrDefault("walletId", "").toString();
            String userId = headers.getOrDefault("userId", "").toString();
            String currency = headers.getOrDefault("currency", "").toString();
            String walletType = headers.getOrDefault("walletType", "").toString();
            String failureReason = headers.getOrDefault("failureReason", "").toString();

            // Attempt recovery based on failure reason
            if (failureReason.contains("Database") || failureReason.contains("Connection")) {
                // Transient database error - safe to retry
                log.info("DLQ: Database error detected, marking for retry. Wallet: {}", walletId);
                return DlqProcessingResult.RETRY;

            } else if (failureReason.contains("UserNotFound")) {
                // User doesn't exist yet - retry later (eventual consistency)
                log.warn("DLQ: User not found for wallet creation. User: {}. Retrying.", userId);
                return DlqProcessingResult.RETRY;

            } else if (failureReason.contains("Validation") || failureReason.contains("Invalid")) {
                // Data validation error - needs manual fix
                log.error("DLQ: Validation error in wallet created. Wallet: {}, User: {}. Manual review required.",
                        walletId, userId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;

            } else if (failureReason.contains("Duplicate") || failureReason.contains("WalletAlreadyExists")) {
                // Wallet already exists - can be safely ignored
                log.info("DLQ: Duplicate wallet created event detected. Wallet: {}. Marking as resolved.",
                        walletId);
                return DlqProcessingResult.DISCARDED;

            } else if (failureReason.contains("WelcomeBonusFailed")) {
                // Welcome bonus grant failed - retry to grant bonus
                log.warn("DLQ: Welcome bonus failed for new wallet. Wallet: {}. Retrying.", walletId);
                return DlqProcessingResult.RETRY;

            } else if (failureReason.contains("LoyaltyAccountCreationFailed")) {
                // Failed to create loyalty account - retry
                log.warn("DLQ: Loyalty account creation failed. Wallet: {}. Retrying.", walletId);
                return DlqProcessingResult.RETRY;

            } else {
                // Unknown error - log for investigation
                log.error("DLQ: Unknown error in wallet created event. Event: {}, Headers: {}. Requires investigation.",
                        event, headers);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }

        } catch (Exception e) {
            log.error("Error handling DLQ wallet created event", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    @Override
    protected String getServiceName() {
        return "WalletCreatedEventsConsumer";
    }
}
