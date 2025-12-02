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
 * DLQ Handler for RiskScoresEventsConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class RiskScoresEventsConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public RiskScoresEventsConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("RiskScoresEventsConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.RiskScoresEventsConsumer.dlq:RiskScoresEventsConsumer.dlq}",
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
            // FIXED: Implement risk scores recovery logic
            log.warn("Processing DLQ risk scores event");

            String scoreId = headers.getOrDefault("scoreId", "").toString();
            String entityId = headers.getOrDefault("entityId", "").toString();
            String entityType = headers.getOrDefault("entityType", "").toString();
            String riskScore = headers.getOrDefault("riskScore", "0").toString();
            String failureReason = headers.getOrDefault("failureReason", "").toString();

            if (failureReason.contains("Database") || failureReason.contains("Connection")) {
                log.info("DLQ: Database error, marking for retry. Score: {}", scoreId);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("Duplicate") || failureReason.contains("AlreadyScored")) {
                log.info("DLQ: Risk score already recorded. Score: {}. Marking as resolved.", scoreId);
                return DlqProcessingResult.DISCARDED;
            } else if (failureReason.contains("EntityNotFound")) {
                log.warn("DLQ: Entity not found for risk score. Entity: {}, Type: {}. Retrying.", entityId, entityType);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("Validation") || failureReason.contains("Invalid")) {
                log.error("DLQ: Validation error in risk scores. Score: {}. Manual review required.", scoreId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            } else {
                log.error("DLQ: Unknown error in risk scores. Event: {}, Headers: {}.", event, headers);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }
        } catch (Exception e) {
            log.error("Error handling DLQ risk scores event", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    @Override
    protected String getServiceName() {
        return "RiskScoresEventsConsumer";
    }
}
