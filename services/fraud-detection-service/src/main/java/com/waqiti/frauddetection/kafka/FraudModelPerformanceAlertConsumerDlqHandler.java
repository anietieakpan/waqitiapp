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
 * DLQ Handler for FraudModelPerformanceAlertConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class FraudModelPerformanceAlertConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public FraudModelPerformanceAlertConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("FraudModelPerformanceAlertConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.FraudModelPerformanceAlertConsumer.dlq:FraudModelPerformanceAlertConsumer.dlq}",
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
            // FIXED: Implement fraud model performance alert recovery logic
            log.warn("Processing DLQ fraud model performance alert");

            String alertId = headers.getOrDefault("alertId", "").toString();
            String modelName = headers.getOrDefault("modelName", "").toString();
            String performanceMetric = headers.getOrDefault("performanceMetric", "").toString();
            String metricValue = headers.getOrDefault("metricValue", "0").toString();
            String threshold = headers.getOrDefault("threshold", "0").toString();
            String failureReason = headers.getOrDefault("failureReason", "").toString();

            if (failureReason.contains("Database") || failureReason.contains("Connection")) {
                log.info("DLQ: Database error, marking for retry. Alert: {}", alertId);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("Duplicate") || failureReason.contains("AlreadyAlerted")) {
                log.info("DLQ: Model performance alert already sent. Model: {}. Marking as resolved.", modelName);
                return DlqProcessingResult.DISCARDED;
            } else if (failureReason.contains("ModelNotFound")) {
                log.warn("DLQ: Fraud model not found for performance alert. Model: {}. Discarding.", modelName);
                return DlqProcessingResult.DISCARDED;
            } else if (failureReason.contains("NotificationFailed") && Double.parseDouble(metricValue) < Double.parseDouble(threshold) * 0.5) {
                log.error("DLQ: Failed to notify about critical model degradation. Model: {}, Metric: {}. Retrying.", modelName, performanceMetric);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("MetricsStorageFailed")) {
                log.warn("DLQ: Failed to store model performance metrics. Model: {}. Discarding non-critical.", modelName);
                return DlqProcessingResult.DISCARDED;
            } else {
                log.error("DLQ: Unknown error in fraud model performance alert. Event: {}, Headers: {}.", event, headers);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }
        } catch (Exception e) {
            log.error("Error handling DLQ fraud model performance alert", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    @Override
    protected String getServiceName() {
        return "FraudModelPerformanceAlertConsumer";
    }
}
