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
 * DLQ Handler for FraudAlertConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class FraudAlertConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public FraudAlertConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("FraudAlertConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.FraudAlertConsumer.dlq:FraudAlertConsumer.dlq}",
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
            // FIXED: Implement fraud alert recovery logic
            log.warn("Processing DLQ fraud alert event for recovery");

            String alertId = headers.getOrDefault("alertId", "").toString();
            String userId = headers.getOrDefault("userId", "").toString();
            String transactionId = headers.getOrDefault("transactionId", "").toString();
            String alertSeverity = headers.getOrDefault("alertSeverity", "HIGH").toString();
            String failureReason = headers.getOrDefault("failureReason", "").toString();

            if (failureReason.contains("Database") || failureReason.contains("Connection")) {
                log.info("DLQ: Database error, marking for retry. Alert: {}", alertId);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("Duplicate") || failureReason.contains("AlreadyProcessed")) {
                log.info("DLQ: Duplicate fraud alert. Alert: {}. Marking as resolved.", alertId);
                return DlqProcessingResult.DISCARDED;
            } else if (failureReason.contains("NotificationFailed") && "CRITICAL".equals(alertSeverity)) {
                log.error("DLQ: Critical fraud alert notification failed. Alert: {}. Retrying.", alertId);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("TransactionNotFound")) {
                log.warn("DLQ: Transaction not found for fraud alert. Transaction: {}. Retrying.", transactionId);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("Validation") || failureReason.contains("Invalid")) {
                log.error("DLQ: Validation error in fraud alert. Alert: {}. Manual review required.", alertId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            } else {
                log.error("DLQ: Unknown error in fraud alert. Event: {}, Headers: {}. Manual review required.", event, headers);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }
        } catch (Exception e) {
            log.error("Error handling DLQ fraud alert event", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    @Override
    protected String getServiceName() {
        return "FraudAlertConsumer";
    }
}
