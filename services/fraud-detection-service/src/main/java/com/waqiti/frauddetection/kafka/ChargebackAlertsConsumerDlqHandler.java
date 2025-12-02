package com.waqiti.frauddetection.kafka;

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
 * DLQ Handler for ChargebackAlertsConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class ChargebackAlertsConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public ChargebackAlertsConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("ChargebackAlertsConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.ChargebackAlertsConsumer.dlq:ChargebackAlertsConsumer.dlq}",
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
            // FIXED: Implement chargeback alert recovery logic
            log.warn("Processing DLQ chargeback alert event");

            String chargebackId = headers.getOrDefault("chargebackId", "").toString();
            String transactionId = headers.getOrDefault("transactionId", "").toString();
            String merchantId = headers.getOrDefault("merchantId", "").toString();
            String amount = headers.getOrDefault("amount", "0").toString();
            String alertType = headers.getOrDefault("alertType", "").toString();
            String failureReason = headers.getOrDefault("failureReason", "").toString();

            if (failureReason.contains("Database") || failureReason.contains("Connection")) {
                log.info("DLQ: Database error, marking for retry. Chargeback: {}", chargebackId);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("Duplicate") || failureReason.contains("AlreadyAlerted")) {
                log.info("DLQ: Chargeback alert already sent. Chargeback: {}. Marking as resolved.", chargebackId);
                return DlqProcessingResult.DISCARDED;
            } else if (failureReason.contains("MerchantNotificationFailed")) {
                log.error("DLQ: Failed to notify merchant of chargeback. Merchant: {}, Chargeback: {}. Retrying.", merchantId, chargebackId);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("DisputeServiceDown")) {
                log.warn("DLQ: Dispute service unavailable for chargeback. Chargeback: {}. Retrying.", chargebackId);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("TransactionNotFound")) {
                log.warn("DLQ: Transaction not found for chargeback alert. Transaction: {}. Retrying.", transactionId);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("Validation") || failureReason.contains("Invalid")) {
                log.error("DLQ: Validation error in chargeback alert. Chargeback: {}. Manual review required.", chargebackId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            } else {
                log.error("DLQ: Unknown error in chargeback alert. Event: {}, Headers: {}.", event, headers);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }
        } catch (Exception e) {
            log.error("Error handling DLQ chargeback alert event", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    @Override
    protected String getServiceName() {
        return "ChargebackAlertsConsumer";
    }
}
