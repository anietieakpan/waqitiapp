package com.waqiti.transaction.kafka;

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
 * DLQ Handler for SettlementCompletedConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class SettlementCompletedConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public SettlementCompletedConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("SettlementCompletedConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.SettlementCompletedConsumer.dlq:SettlementCompletedConsumer.dlq}",
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
            // FIXED: Implement settlement completed recovery logic
            log.warn("Processing DLQ settlement completed event for recovery");

            // Extract event details
            String settlementId = headers.getOrDefault("settlementId", "").toString();
            String transactionId = headers.getOrDefault("transactionId", "").toString();
            String merchantId = headers.getOrDefault("merchantId", "").toString();
            String failureReason = headers.getOrDefault("failureReason", "").toString();

            // Attempt recovery based on failure reason
            if (failureReason.contains("Database") || failureReason.contains("Connection")) {
                log.info("DLQ: Database error detected, marking for retry. Settlement: {}", settlementId);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("TransactionNotFound") || failureReason.contains("MerchantNotFound")) {
                log.warn("DLQ: Transaction/merchant not found for settlement. Settlement: {}. Retrying.", settlementId);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("Duplicate") || failureReason.contains("AlreadySettled")) {
                log.info("DLQ: Settlement already completed. Settlement: {}. Marking as resolved.", settlementId);
                return DlqProcessingResult.DISCARDED;
            } else if (failureReason.contains("AccountingUpdateFailed")) {
                log.error("DLQ: Accounting update failed for settlement. Settlement: {}. Retrying.", settlementId);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("ReconFailed")) {
                log.warn("DLQ: Reconciliation failed for settlement. Settlement: {}. Manual review required.", settlementId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            } else if (failureReason.contains("Validation") || failureReason.contains("Invalid")) {
                log.error("DLQ: Validation error in settlement completion. Settlement: {}. Manual review required.", settlementId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            } else {
                log.error("DLQ: Unknown error in settlement completed event. Event: {}, Headers: {}. Requires investigation.",
                        event, headers);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }
        } catch (Exception e) {
            log.error("Error handling DLQ settlement completed event", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    @Override
    protected String getServiceName() {
        return "SettlementCompletedConsumer";
    }
}
