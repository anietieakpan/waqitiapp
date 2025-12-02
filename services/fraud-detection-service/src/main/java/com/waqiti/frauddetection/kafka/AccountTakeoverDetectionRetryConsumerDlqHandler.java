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
 * DLQ Handler for AccountTakeoverDetectionRetryConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class AccountTakeoverDetectionRetryConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public AccountTakeoverDetectionRetryConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("AccountTakeoverDetectionRetryConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.AccountTakeoverDetectionRetryConsumer.dlq:AccountTakeoverDetectionRetryConsumer.dlq}",
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
            // FIXED: Implement account takeover detection retry recovery logic - CRITICAL
            log.error("Processing DLQ account takeover detection retry - SECURITY CRITICAL");

            String detectionId = headers.getOrDefault("detectionId", "").toString();
            String userId = headers.getOrDefault("userId", "").toString();
            String atoScore = headers.getOrDefault("atoScore", "0").toString();
            String retryCount = headers.getOrDefault("retryCount", "0").toString();
            String failureReason = headers.getOrDefault("failureReason", "").toString();

            if (failureReason.contains("Database") || failureReason.contains("Connection")) {
                log.error("DLQ: Database error for CRITICAL ATO detection. User: {}. Retrying.", userId);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("AccountLockFailed") && Double.parseDouble(atoScore) > 85) {
                log.error("DLQ: CRITICAL - Failed to lock account for high ATO score. User: {}, Score: {}. Retrying.", userId, atoScore);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("MaxRetriesExceeded")) {
                log.error("DLQ: Max retries exceeded for ATO detection. User: {}, Retries: {}. Manual intervention required.", userId, retryCount);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            } else if (failureReason.contains("Duplicate") || failureReason.contains("AlreadyDetected")) {
                log.info("DLQ: ATO already detected. Detection: {}. Marking as resolved.", detectionId);
                return DlqProcessingResult.DISCARDED;
            } else if (failureReason.contains("UserNotFound")) {
                log.warn("DLQ: User not found for ATO detection. User: {}. Retrying.", userId);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("Validation") || failureReason.contains("Invalid")) {
                log.error("DLQ: Validation error in CRITICAL ATO detection. Detection: {}. URGENT manual review.", detectionId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            } else {
                log.error("DLQ: Unknown error in CRITICAL ATO detection retry. Event: {}, Headers: {}. URGENT.", event, headers);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }
        } catch (Exception e) {
            log.error("Error handling DLQ account takeover detection retry - SECURITY CRITICAL", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    @Override
    protected String getServiceName() {
        return "AccountTakeoverDetectionRetryConsumer";
    }
}
