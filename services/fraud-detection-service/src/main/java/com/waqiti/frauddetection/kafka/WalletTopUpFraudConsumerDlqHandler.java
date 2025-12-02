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
 * DLQ Handler for WalletTopUpFraudConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class WalletTopUpFraudConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public WalletTopUpFraudConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("WalletTopUpFraudConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.WalletTopUpFraudConsumer.dlq:WalletTopUpFraudConsumer.dlq}",
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
            // FIXED: Implement wallet top-up fraud detection recovery logic
            log.warn("Processing DLQ wallet top-up fraud event");

            String topUpId = headers.getOrDefault("topUpId", "").toString();
            String userId = headers.getOrDefault("userId", "").toString();
            String walletId = headers.getOrDefault("walletId", "").toString();
            String amount = headers.getOrDefault("amount", "0").toString();
            String fraudScore = headers.getOrDefault("fraudScore", "0").toString();
            String paymentMethod = headers.getOrDefault("paymentMethod", "").toString();
            String failureReason = headers.getOrDefault("failureReason", "").toString();

            if (failureReason.contains("Database") || failureReason.contains("Connection")) {
                log.info("DLQ: Database error, marking for retry. TopUp: {}", topUpId);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("Duplicate") || failureReason.contains("AlreadyAnalyzed")) {
                log.info("DLQ: Top-up already analyzed. TopUp: {}. Marking as resolved.", topUpId);
                return DlqProcessingResult.DISCARDED;
            } else if (failureReason.contains("TopUpBlockFailed") && Double.parseDouble(fraudScore) > 85) {
                log.error("DLQ: Failed to block high-risk top-up. TopUp: {}, Score: {}. Retrying.", topUpId, fraudScore);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("WalletNotFound")) {
                log.warn("DLQ: Wallet not found for top-up fraud check. Wallet: {}. Retrying.", walletId);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("StolenCardDetected")) {
                log.error("DLQ: Stolen card detected for top-up. TopUp: {}, Method: {}. Manual review required.", topUpId, paymentMethod);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            } else if (failureReason.contains("FraudModelUnavailable")) {
                log.warn("DLQ: Fraud model unavailable for top-up. TopUp: {}. Retrying.", topUpId);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("Validation") || failureReason.contains("Invalid")) {
                log.error("DLQ: Validation error in wallet top-up fraud. TopUp: {}. Manual review required.", topUpId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            } else {
                log.error("DLQ: Unknown error in wallet top-up fraud. Event: {}, Headers: {}.", event, headers);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }
        } catch (Exception e) {
            log.error("Error handling DLQ wallet top-up fraud event", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    @Override
    protected String getServiceName() {
        return "WalletTopUpFraudConsumer";
    }
}
