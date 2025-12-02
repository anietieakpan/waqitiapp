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
 * DLQ Handler for AnomalyReviewQueueConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class AnomalyReviewQueueConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public AnomalyReviewQueueConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("AnomalyReviewQueueConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.AnomalyReviewQueueConsumer.dlq:AnomalyReviewQueueConsumer.dlq}",
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
            // FIXED: Implement anomaly review queue recovery logic
            log.warn("Processing DLQ anomaly review queue event");

            String reviewId = headers.getOrDefault("reviewId", "").toString();
            String anomalyId = headers.getOrDefault("anomalyId", "").toString();
            String priority = headers.getOrDefault("priority", "").toString();
            String assignedTo = headers.getOrDefault("assignedTo", "").toString();
            String failureReason = headers.getOrDefault("failureReason", "").toString();

            if (failureReason.contains("Database") || failureReason.contains("Connection")) {
                log.info("DLQ: Database error, marking for retry. Review: {}", reviewId);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("Duplicate") || failureReason.contains("AlreadyQueued")) {
                log.info("DLQ: Anomaly already queued for review. Review: {}. Marking as resolved.", reviewId);
                return DlqProcessingResult.DISCARDED;
            } else if (failureReason.contains("AssignmentFailed") && "CRITICAL".equals(priority)) {
                log.error("DLQ: Failed to assign critical anomaly for review. Review: {}. Retrying.", reviewId);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("ReviewerNotFound")) {
                log.warn("DLQ: Reviewer not found for anomaly. Assigned: {}. Retrying.", assignedTo);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("AnomalyNotFound")) {
                log.warn("DLQ: Anomaly not found for review queue. Anomaly: {}. Retrying.", anomalyId);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("Validation") || failureReason.contains("Invalid")) {
                log.error("DLQ: Validation error in anomaly review queue. Review: {}. Manual review required.", reviewId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            } else {
                log.error("DLQ: Unknown error in anomaly review queue. Event: {}, Headers: {}.", event, headers);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }
        } catch (Exception e) {
            log.error("Error handling DLQ anomaly review queue event", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    @Override
    protected String getServiceName() {
        return "AnomalyReviewQueueConsumer";
    }
}
