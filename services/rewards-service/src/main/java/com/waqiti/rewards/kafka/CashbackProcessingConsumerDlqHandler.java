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
 * DLQ Handler for CashbackProcessingConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class CashbackProcessingConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public CashbackProcessingConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("CashbackProcessingConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.CashbackProcessingConsumer.dlq:CashbackProcessingConsumer.dlq}",
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
            // FIXED: Implement cashback processing recovery logic
            log.warn("Processing DLQ cashback processing event for recovery");

            // Extract event details
            String cashbackId = headers.getOrDefault("cashbackId", "").toString();
            String userId = headers.getOrDefault("userId", "").toString();
            String walletId = headers.getOrDefault("walletId", "").toString();
            String failureReason = headers.getOrDefault("failureReason", "").toString();

            // Attempt recovery based on failure reason
            if (failureReason.contains("Database") || failureReason.contains("Connection")) {
                // Transient database error - safe to retry
                log.info("DLQ: Database error detected, marking for retry. Cashback: {}", cashbackId);
                return DlqProcessingResult.RETRY;

            } else if (failureReason.contains("WalletNotFound") || failureReason.contains("UserNotFound")) {
                // Wallet/user doesn't exist yet - retry later (eventual consistency)
                log.warn("DLQ: Wallet/user not found for cashback. Wallet: {}, User: {}. Retrying.",
                        walletId, userId);
                return DlqProcessingResult.RETRY;

            } else if (failureReason.contains("Validation") || failureReason.contains("Invalid")) {
                // Data validation error - needs manual fix
                log.error("DLQ: Validation error in cashback processing. Cashback: {}, User: {}. Manual review required.",
                        cashbackId, userId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;

            } else if (failureReason.contains("Duplicate") || failureReason.contains("AlreadyProcessed")) {
                // Cashback already processed - can be safely ignored
                log.info("DLQ: Duplicate cashback processing event detected. Cashback: {}. Marking as resolved.",
                        cashbackId);
                return DlqProcessingResult.DISCARDED;

            } else if (failureReason.contains("CreditFailed") || failureReason.contains("BalanceUpdateFailed")) {
                // Wallet credit failed - critical, needs retry
                log.error("DLQ: Wallet credit failed for cashback. Cashback: {}, Wallet: {}. Retrying.",
                        cashbackId, walletId);
                return DlqProcessingResult.RETRY;

            } else if (failureReason.contains("Expired")) {
                // Cashback expired before processing - discard
                log.info("DLQ: Cashback expired. Cashback: {}. Discarding.", cashbackId);
                return DlqProcessingResult.DISCARDED;

            } else {
                // Unknown error - log for investigation
                log.error("DLQ: Unknown error in cashback processing event. Event: {}, Headers: {}. Requires investigation.",
                        event, headers);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }

        } catch (Exception e) {
            log.error("Error handling DLQ cashback processing event", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    @Override
    protected String getServiceName() {
        return "CashbackProcessingConsumer";
    }
}
