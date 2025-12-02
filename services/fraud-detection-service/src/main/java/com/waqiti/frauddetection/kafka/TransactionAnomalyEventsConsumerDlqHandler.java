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
 * DLQ Handler for TransactionAnomalyEventsConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class TransactionAnomalyEventsConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public TransactionAnomalyEventsConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("TransactionAnomalyEventsConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.TransactionAnomalyEventsConsumer.dlq:TransactionAnomalyEventsConsumer.dlq}",
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
            // FIXED: Implement transaction anomaly recovery logic
            log.warn("Processing DLQ transaction anomaly event");

            String anomalyId = headers.getOrDefault("anomalyId", "").toString();
            String transactionId = headers.getOrDefault("transactionId", "").toString();
            String anomalyType = headers.getOrDefault("anomalyType", "").toString();
            String anomalyScore = headers.getOrDefault("anomalyScore", "0").toString();
            String failureReason = headers.getOrDefault("failureReason", "").toString();

            if (failureReason.contains("Database") || failureReason.contains("Connection")) {
                log.info("DLQ: Database error, marking for retry. Anomaly: {}", anomalyId);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("Duplicate") || failureReason.contains("AlreadyDetected")) {
                log.info("DLQ: Anomaly already detected. Anomaly: {}. Marking as resolved.", anomalyId);
                return DlqProcessingResult.DISCARDED;
            } else if (failureReason.contains("TransactionNotFound")) {
                log.warn("DLQ: Transaction not found for anomaly detection. Transaction: {}. Retrying.", transactionId);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("AlertGenerationFailed") && Double.parseDouble(anomalyScore) > 80) {
                log.error("DLQ: Failed to generate alert for high-score anomaly. Anomaly: {}, Score: {}. Retrying.", anomalyId, anomalyScore);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("AnomalyModelUnavailable")) {
                log.warn("DLQ: Anomaly detection model unavailable. Anomaly: {}. Retrying.", anomalyId);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("Validation") || failureReason.contains("Invalid")) {
                log.error("DLQ: Validation error in transaction anomaly. Anomaly: {}. Manual review required.", anomalyId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            } else {
                log.error("DLQ: Unknown error in transaction anomaly. Event: {}, Headers: {}.", event, headers);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }
        } catch (Exception e) {
            log.error("Error handling DLQ transaction anomaly event", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    @Override
    protected String getServiceName() {
        return "TransactionAnomalyEventsConsumer";
    }
}
