package com.waqiti.wallet.kafka;

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
 * DLQ Handler for WalletCreatedConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class WalletCreatedConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public WalletCreatedConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("WalletCreatedConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.WalletCreatedConsumer.dlq:WalletCreatedConsumer.dlq}",
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
            log.info("DLQ: Processing WalletCreated event recovery");

            String walletId = headers.getOrDefault("walletId", "").toString();
            String userId = headers.getOrDefault("userId", "").toString();
            String failureReason = headers.getOrDefault("failureReason", "").toString();

            // Strategy 1: Duplicate wallet creation
            if (failureReason.contains("duplicate") || failureReason.contains("already exists")) {
                log.info("DLQ: Wallet already exists - discarding duplicate: {}", walletId);
                return DlqProcessingResult.DISCARDED;
            }

            // Strategy 2: User not found (eventual consistency)
            if (failureReason.contains("user not found")) {
                log.warn("DLQ: User not found for wallet creation, retrying: userId={}", userId);
                return DlqProcessingResult.RETRY;
            }

            // Strategy 3: Notification failure (non-critical)
            if (failureReason.contains("notification")) {
                log.info("DLQ: Wallet created but notification failed: {}", walletId);
                // Wallet was created, just notification failed - mark success
                return DlqProcessingResult.SUCCESS;
            }

            // Strategy 4: Rewards initiation failure
            if (failureReason.contains("rewards") || failureReason.contains("signup bonus")) {
                log.warn("DLQ: Signup rewards failed for wallet: {}", walletId);
                // Retry rewards crediting
                return DlqProcessingResult.RETRY;
            }

            // Strategy 5: Database/transient error
            if (failureReason.contains("timeout") || failureReason.contains("database")) {
                log.info("DLQ: Transient error during wallet creation, retrying: {}", walletId);
                return DlqProcessingResult.RETRY;
            }

            // Default: Manual review
            log.error("DLQ: Unknown wallet creation failure: {}", failureReason);
            return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;

        } catch (Exception e) {
            log.error("DLQ: Error handling wallet created event", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    @Override
    protected String getServiceName() {
        return "WalletCreatedConsumer";
    }
}
