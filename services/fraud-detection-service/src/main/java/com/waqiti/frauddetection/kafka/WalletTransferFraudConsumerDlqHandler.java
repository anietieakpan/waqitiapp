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
 * DLQ Handler for WalletTransferFraudConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class WalletTransferFraudConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public WalletTransferFraudConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("WalletTransferFraudConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.WalletTransferFraudConsumer.dlq:WalletTransferFraudConsumer.dlq}",
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
            // FIXED: Implement wallet transfer fraud detection recovery logic
            log.warn("Processing DLQ wallet transfer fraud event");

            String transferId = headers.getOrDefault("transferId", "").toString();
            String sourceWalletId = headers.getOrDefault("sourceWalletId", "").toString();
            String targetWalletId = headers.getOrDefault("targetWalletId", "").toString();
            String amount = headers.getOrDefault("amount", "0").toString();
            String fraudScore = headers.getOrDefault("fraudScore", "0").toString();
            String failureReason = headers.getOrDefault("failureReason", "").toString();

            if (failureReason.contains("Database") || failureReason.contains("Connection")) {
                log.info("DLQ: Database error, marking for retry. Transfer: {}", transferId);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("Duplicate") || failureReason.contains("AlreadyAnalyzed")) {
                log.info("DLQ: Transfer already analyzed. Transfer: {}. Marking as resolved.", transferId);
                return DlqProcessingResult.DISCARDED;
            } else if (failureReason.contains("TransferBlockFailed") && Double.parseDouble(fraudScore) > 85) {
                log.error("DLQ: Failed to block high-risk transfer. Transfer: {}, Score: {}. Retrying.", transferId, fraudScore);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("WalletNotFound")) {
                log.warn("DLQ: Wallet not found for transfer fraud check. Source: {}, Target: {}. Retrying.", sourceWalletId, targetWalletId);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("FraudModelUnavailable")) {
                log.warn("DLQ: Fraud model unavailable for transfer. Transfer: {}. Retrying.", transferId);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("MoneyMuleDetected")) {
                log.error("DLQ: Money mule activity detected. Transfer: {}. Manual review required.", transferId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            } else if (failureReason.contains("Validation") || failureReason.contains("Invalid")) {
                log.error("DLQ: Validation error in wallet transfer fraud. Transfer: {}. Manual review required.", transferId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            } else {
                log.error("DLQ: Unknown error in wallet transfer fraud. Event: {}, Headers: {}.", event, headers);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }
        } catch (Exception e) {
            log.error("Error handling DLQ wallet transfer fraud event", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    @Override
    protected String getServiceName() {
        return "WalletTransferFraudConsumer";
    }
}
