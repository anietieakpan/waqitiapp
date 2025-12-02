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
 * DLQ Handler for FraudInvestigationsConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class FraudInvestigationsConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public FraudInvestigationsConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("FraudInvestigationsConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.FraudInvestigationsConsumer.dlq:FraudInvestigationsConsumer.dlq}",
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
            // FIXED: Implement fraud investigations recovery logic
            log.warn("Processing DLQ fraud investigations event");

            String investigationId = headers.getOrDefault("investigationId", "").toString();
            String caseId = headers.getOrDefault("caseId", "").toString();
            String investigationType = headers.getOrDefault("investigationType", "").toString();
            String priority = headers.getOrDefault("priority", "").toString();
            String status = headers.getOrDefault("status", "").toString();
            String failureReason = headers.getOrDefault("failureReason", "").toString();

            if (failureReason.contains("Database") || failureReason.contains("Connection")) {
                log.info("DLQ: Database error, marking for retry. Investigation: {}", investigationId);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("Duplicate") || failureReason.contains("AlreadyInvestigated")) {
                log.info("DLQ: Investigation already exists. Investigation: {}. Marking as resolved.", investigationId);
                return DlqProcessingResult.DISCARDED;
            } else if (failureReason.contains("AssignmentFailed") && "CRITICAL".equals(priority)) {
                log.error("DLQ: Failed to assign critical fraud investigation. Investigation: {}. Retrying.", investigationId);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("NotificationFailed")) {
                log.warn("DLQ: Failed to notify investigator. Investigation: {}. Retrying.", investigationId);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("CaseNotFound")) {
                log.warn("DLQ: Case not found for investigation. Case: {}. Retrying.", caseId);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("Validation") || failureReason.contains("Invalid")) {
                log.error("DLQ: Validation error in fraud investigation. Investigation: {}. Manual review required.", investigationId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            } else {
                log.error("DLQ: Unknown error in fraud investigations. Event: {}, Headers: {}.", event, headers);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }
        } catch (Exception e) {
            log.error("Error handling DLQ fraud investigations event", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    @Override
    protected String getServiceName() {
        return "FraudInvestigationsConsumer";
    }
}
