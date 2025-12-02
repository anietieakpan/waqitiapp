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
 * DLQ Handler for FraudProcessingConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class FraudProcessingConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public FraudProcessingConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("FraudProcessingConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.FraudProcessingConsumer.dlq:FraudProcessingConsumer.dlq}",
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
            // FIXED: Implement fraud processing recovery logic
            log.warn("Processing DLQ fraud processing event");

            String processingId = headers.getOrDefault("processingId", "").toString();
            String caseId = headers.getOrDefault("caseId", "").toString();
            String processingStage = headers.getOrDefault("processingStage", "").toString();
            String failureReason = headers.getOrDefault("failureReason", "").toString();

            if (failureReason.contains("Database") || failureReason.contains("Connection")) {
                log.info("DLQ: Database error, marking for retry. Processing: {}", processingId);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("Duplicate") || failureReason.contains("AlreadyProcessed")) {
                log.info("DLQ: Fraud already processed. Processing: {}. Marking as resolved.", processingId);
                return DlqProcessingResult.DISCARDED;
            } else if (failureReason.contains("CaseNotFound")) {
                log.warn("DLQ: Case not found for fraud processing. Case: {}. Retrying.", caseId);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("ProcessingFailed")) {
                log.error("DLQ: Fraud processing failed. Processing: {}, Stage: {}. Retrying.", processingId, processingStage);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("Validation") || failureReason.contains("Invalid")) {
                log.error("DLQ: Validation error in fraud processing. Processing: {}. Manual review required.", processingId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            } else {
                log.error("DLQ: Unknown error in fraud processing. Event: {}, Headers: {}.", event, headers);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }
        } catch (Exception e) {
            log.error("Error handling DLQ fraud processing event", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    @Override
    protected String getServiceName() {
        return "FraudProcessingConsumer";
    }
}
