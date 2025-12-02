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
 * DLQ Handler for CashbackCalculationEventsConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class CashbackCalculationEventsConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public CashbackCalculationEventsConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("CashbackCalculationEventsConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.CashbackCalculationEventsConsumer.dlq:CashbackCalculationEventsConsumer.dlq}",
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
            // FIXED: Implement cashback calculation recovery logic
            log.warn("Processing DLQ cashback calculation event for recovery");

            // Extract event details
            String transactionId = headers.getOrDefault("transactionId", "").toString();
            String userId = headers.getOrDefault("userId", "").toString();
            String merchantId = headers.getOrDefault("merchantId", "").toString();
            String failureReason = headers.getOrDefault("failureReason", "").toString();

            // Attempt recovery based on failure reason
            if (failureReason.contains("Database") || failureReason.contains("Connection")) {
                // Transient database error - safe to retry
                log.info("DLQ: Database error detected, marking for retry. Transaction: {}", transactionId);
                return DlqProcessingResult.RETRY;

            } else if (failureReason.contains("MerchantNotFound") || failureReason.contains("CashbackRuleNotFound")) {
                // Merchant or cashback rules missing - retry later (eventual consistency)
                log.warn("DLQ: Merchant/rule data not found. Merchant: {}, Transaction: {}. Retrying.",
                        merchantId, transactionId);
                return DlqProcessingResult.RETRY;

            } else if (failureReason.contains("Validation") || failureReason.contains("Invalid")) {
                // Data validation error - needs manual fix
                log.error("DLQ: Validation error in cashback calculation. Transaction: {}, User: {}. Manual review required.",
                        transactionId, userId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;

            } else if (failureReason.contains("Duplicate") || failureReason.contains("AlreadyCalculated")) {
                // Cashback already calculated - can be safely ignored
                log.info("DLQ: Duplicate cashback calculation event detected. Transaction: {}. Marking as resolved.",
                        transactionId);
                return DlqProcessingResult.DISCARDED;

            } else if (failureReason.contains("ZeroAmount") || failureReason.contains("NotEligible")) {
                // Transaction not eligible for cashback - discard
                log.info("DLQ: Transaction not eligible for cashback. Transaction: {}. Discarding.", transactionId);
                return DlqProcessingResult.DISCARDED;

            } else if (failureReason.contains("ArithmeticException") || failureReason.contains("NumberFormat")) {
                // Calculation error - needs manual review
                log.error("DLQ: Calculation error in cashback. Transaction: {}. Manual review required.", transactionId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;

            } else {
                // Unknown error - log for investigation
                log.error("DLQ: Unknown error in cashback calculation event. Event: {}, Headers: {}. Requires investigation.",
                        event, headers);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }

        } catch (Exception e) {
            log.error("Error handling DLQ cashback calculation event", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    @Override
    protected String getServiceName() {
        return "CashbackCalculationEventsConsumer";
    }
}
