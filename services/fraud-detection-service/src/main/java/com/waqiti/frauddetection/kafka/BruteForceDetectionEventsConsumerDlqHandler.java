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
 * DLQ Handler for BruteForceDetectionEventsConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class BruteForceDetectionEventsConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public BruteForceDetectionEventsConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("BruteForceDetectionEventsConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.BruteForceDetectionEventsConsumer.dlq:BruteForceDetectionEventsConsumer.dlq}",
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
            // FIXED: Implement brute force detection recovery logic
            log.error("Processing DLQ brute force detection event - SECURITY CRITICAL");

            String detectionId = headers.getOrDefault("detectionId", "").toString();
            String targetUserId = headers.getOrDefault("targetUserId", "").toString();
            String attackerIp = headers.getOrDefault("attackerIp", "").toString();
            String attemptCount = headers.getOrDefault("attemptCount", "0").toString();
            String failureReason = headers.getOrDefault("failureReason", "").toString();

            if (failureReason.contains("Database") || failureReason.contains("Connection")) {
                log.error("DLQ: Database error for CRITICAL brute force detection. IP: {}. Retrying immediately.", attackerIp);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("IPBlockFailed") && Integer.parseInt(attemptCount) > 10) {
                log.error("DLQ: CRITICAL - Failed to block brute force attacker. IP: {}, Attempts: {}. Retrying.", attackerIp, attemptCount);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("Duplicate") || failureReason.contains("AlreadyDetected")) {
                log.info("DLQ: Brute force already detected. Detection: {}. Marking as resolved.", detectionId);
                return DlqProcessingResult.DISCARDED;
            } else if (failureReason.contains("AccountLockFailed")) {
                log.error("DLQ: CRITICAL - Failed to lock targeted account. User: {}. Retrying.", targetUserId);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("Validation") || failureReason.contains("Invalid")) {
                log.error("DLQ: Validation error in brute force detection. Detection: {}. URGENT manual review.", detectionId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            } else {
                log.error("DLQ: Unknown error in CRITICAL brute force detection. Event: {}, Headers: {}.", event, headers);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }
        } catch (Exception e) {
            log.error("Error handling DLQ brute force detection event - SECURITY CRITICAL", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    @Override
    protected String getServiceName() {
        return "BruteForceDetectionEventsConsumer";
    }
}
