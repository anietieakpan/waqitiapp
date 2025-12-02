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
 * DLQ Handler for RiskAssessmentEventConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class RiskAssessmentEventConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public RiskAssessmentEventConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("RiskAssessmentEventConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.RiskAssessmentEventConsumer.dlq:RiskAssessmentEventConsumer.dlq}",
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
            // FIXED: Implement risk assessment event recovery logic (duplicate handler)
            log.warn("Processing DLQ risk assessment event");

            String assessmentId = headers.getOrDefault("assessmentId", "").toString();
            String entityId = headers.getOrDefault("entityId", "").toString();
            String riskLevel = headers.getOrDefault("riskLevel", "").toString();
            String failureReason = headers.getOrDefault("failureReason", "").toString();

            if (failureReason.contains("Database") || failureReason.contains("Connection")) {
                log.info("DLQ: Database error, marking for retry. Assessment: {}", assessmentId);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("Duplicate") || failureReason.contains("AlreadyAssessed")) {
                log.info("DLQ: Risk already assessed. Assessment: {}. Marking as resolved.", assessmentId);
                return DlqProcessingResult.DISCARDED;
            } else if (failureReason.contains("EntityNotFound")) {
                log.warn("DLQ: Entity not found for risk assessment. Entity: {}. Retrying.", entityId);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("CriticalRiskNotProcessed") && "HIGH".equals(riskLevel)) {
                log.error("DLQ: Failed to process high-risk assessment. Assessment: {}. Retrying.", assessmentId);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("RiskModelUnavailable")) {
                log.warn("DLQ: Risk model unavailable. Assessment: {}. Retrying.", assessmentId);
                return DlqProcessingResult.RETRY;
            } else {
                log.error("DLQ: Unknown error in risk assessment event. Event: {}, Headers: {}.", event, headers);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }
        } catch (Exception e) {
            log.error("Error handling DLQ risk assessment event", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    @Override
    protected String getServiceName() {
        return "RiskAssessmentEventConsumer";
    }
}
