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
 * DLQ Handler for AppealReviewTasksConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class AppealReviewTasksConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public AppealReviewTasksConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("AppealReviewTasksConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.AppealReviewTasksConsumer.dlq:AppealReviewTasksConsumer.dlq}",
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
            // FIXED: Implement appeal review tasks recovery logic
            log.warn("Processing DLQ appeal review tasks event");

            String taskId = headers.getOrDefault("taskId", "").toString();
            String appealId = headers.getOrDefault("appealId", "").toString();
            String reviewerId = headers.getOrDefault("reviewerId", "").toString();
            String priority = headers.getOrDefault("priority", "").toString();
            String failureReason = headers.getOrDefault("failureReason", "").toString();

            if (failureReason.contains("Database") || failureReason.contains("Connection")) {
                log.info("DLQ: Database error, marking for retry. Task: {}", taskId);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("Duplicate") || failureReason.contains("AlreadyAssigned")) {
                log.info("DLQ: Appeal review task already assigned. Task: {}. Marking as resolved.", taskId);
                return DlqProcessingResult.DISCARDED;
            } else if (failureReason.contains("ReviewerNotFound") || failureReason.contains("ReviewerUnavailable")) {
                log.warn("DLQ: Reviewer not available for appeal. Reviewer: {}. Retrying.", reviewerId);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("AppealNotFound")) {
                log.warn("DLQ: Appeal not found for review task. Appeal: {}. Retrying.", appealId);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("AssignmentFailed") && "HIGH".equals(priority)) {
                log.error("DLQ: Failed to assign high-priority appeal review. Task: {}. Retrying.", taskId);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("Validation") || failureReason.contains("Invalid")) {
                log.error("DLQ: Validation error in appeal review task. Task: {}. Manual review required.", taskId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            } else {
                log.error("DLQ: Unknown error in appeal review tasks. Event: {}, Headers: {}.", event, headers);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }
        } catch (Exception e) {
            log.error("Error handling DLQ appeal review tasks event", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    @Override
    protected String getServiceName() {
        return "AppealReviewTasksConsumer";
    }
}
