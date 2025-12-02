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
 * DLQ Handler for CardFraudDetectionConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class CardFraudDetectionConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public CardFraudDetectionConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("CardFraudDetectionConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.CardFraudDetectionConsumer.dlq:CardFraudDetectionConsumer.dlq}",
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
            // FIXED: Implement card fraud detection recovery logic
            log.warn("Processing DLQ card fraud detection event");

            String detectionId = headers.getOrDefault("detectionId", "").toString();
            String cardNumber = headers.getOrDefault("cardNumber", "").toString();
            String transactionId = headers.getOrDefault("transactionId", "").toString();
            String fraudScore = headers.getOrDefault("fraudScore", "0").toString();
            String failureReason = headers.getOrDefault("failureReason", "").toString();

            if (failureReason.contains("Database") || failureReason.contains("Connection")) {
                log.info("DLQ: Database error, marking for retry. Detection: {}", detectionId);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("Duplicate") || failureReason.contains("AlreadyDetected")) {
                log.info("DLQ: Card fraud already detected. Detection: {}. Marking as resolved.", detectionId);
                return DlqProcessingResult.DISCARDED;
            } else if (failureReason.contains("CardBlockFailed") && Double.parseDouble(fraudScore) > 85) {
                log.error("DLQ: Failed to block high-risk card. Card: {}, Score: {}. Retrying.", cardNumber, fraudScore);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("CardNotFound") || failureReason.contains("TransactionNotFound")) {
                log.warn("DLQ: Card/transaction not found for fraud detection. Transaction: {}. Retrying.", transactionId);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("FraudModelUnavailable")) {
                log.warn("DLQ: Card fraud model unavailable. Detection: {}. Retrying.", detectionId);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("Validation") || failureReason.contains("Invalid")) {
                log.error("DLQ: Validation error in card fraud detection. Detection: {}. Manual review required.", detectionId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            } else {
                log.error("DLQ: Unknown error in card fraud detection. Event: {}, Headers: {}.", event, headers);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }
        } catch (Exception e) {
            log.error("Error handling DLQ card fraud detection event", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    @Override
    protected String getServiceName() {
        return "CardFraudDetectionConsumer";
    }
}
