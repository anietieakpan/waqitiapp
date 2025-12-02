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
 * DLQ Handler for ATMWithdrawalRequestedConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class ATMWithdrawalRequestedConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public ATMWithdrawalRequestedConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("ATMWithdrawalRequestedConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.ATMWithdrawalRequestedConsumer.dlq:ATMWithdrawalRequestedConsumer.dlq}",
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
            // FIXED: Implement ATM withdrawal fraud detection recovery logic
            log.warn("Processing DLQ ATM withdrawal requested event");

            String withdrawalId = headers.getOrDefault("withdrawalId", "").toString();
            String atmId = headers.getOrDefault("atmId", "").toString();
            String cardNumber = headers.getOrDefault("cardNumber", "").toString();
            String amount = headers.getOrDefault("amount", "0").toString();
            String location = headers.getOrDefault("location", "").toString();
            String failureReason = headers.getOrDefault("failureReason", "").toString();

            if (failureReason.contains("Database") || failureReason.contains("Connection")) {
                log.info("DLQ: Database error, marking for retry. Withdrawal: {}", withdrawalId);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("Duplicate") || failureReason.contains("AlreadyProcessed")) {
                log.info("DLQ: ATM withdrawal already processed. Withdrawal: {}. Marking as resolved.", withdrawalId);
                return DlqProcessingResult.DISCARDED;
            } else if (failureReason.contains("FraudDetectionFailed") || failureReason.contains("RiskScoringFailed")) {
                log.error("DLQ: Failed to analyze ATM withdrawal for fraud. Withdrawal: {}. Retrying.", withdrawalId);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("ATMNotRecognized") || failureReason.contains("SuspiciousLocation")) {
                log.warn("DLQ: Suspicious ATM withdrawal location. ATM: {}, Location: {}. Manual review required.", atmId, location);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            } else if (failureReason.contains("CardNotFound")) {
                log.warn("DLQ: Card not found for ATM withdrawal. Card: {}. Retrying.", cardNumber);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("Validation") || failureReason.contains("Invalid")) {
                log.error("DLQ: Validation error in ATM withdrawal. Withdrawal: {}. Manual review required.", withdrawalId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            } else {
                log.error("DLQ: Unknown error in ATM withdrawal. Event: {}, Headers: {}.", event, headers);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }
        } catch (Exception e) {
            log.error("Error handling DLQ ATM withdrawal event", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    @Override
    protected String getServiceName() {
        return "ATMWithdrawalRequestedConsumer";
    }
}
