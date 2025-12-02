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
 * DLQ Handler for BotDetectionEventsConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class BotDetectionEventsConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public BotDetectionEventsConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("BotDetectionEventsConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.BotDetectionEventsConsumer.dlq:BotDetectionEventsConsumer.dlq}",
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
            // FIXED: Implement bot detection recovery logic
            log.warn("Processing DLQ bot detection event");

            String detectionId = headers.getOrDefault("detectionId", "").toString();
            String ipAddress = headers.getOrDefault("ipAddress", "").toString();
            String userAgent = headers.getOrDefault("userAgent", "").toString();
            String botScore = headers.getOrDefault("botScore", "0").toString();
            String failureReason = headers.getOrDefault("failureReason", "").toString();

            if (failureReason.contains("Database") || failureReason.contains("Connection")) {
                log.info("DLQ: Database error, marking for retry. Detection: {}", detectionId);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("Duplicate") || failureReason.contains("AlreadyDetected")) {
                log.info("DLQ: Bot already detected. IP: {}. Marking as resolved.", ipAddress);
                return DlqProcessingResult.DISCARDED;
            } else if (failureReason.contains("BotBlockFailed") && Double.parseDouble(botScore) > 90) {
                log.error("DLQ: Failed to block high-confidence bot. IP: {}, Score: {}. Retrying.", ipAddress, botScore);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("BotDetectionServiceDown")) {
                log.warn("DLQ: Bot detection service unavailable. Detection: {}. Retrying.", detectionId);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("Validation") || failureReason.contains("Invalid")) {
                log.error("DLQ: Validation error in bot detection. Detection: {}. Manual review required.", detectionId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            } else {
                log.error("DLQ: Unknown error in bot detection. Event: {}, Headers: {}.", event, headers);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }
        } catch (Exception e) {
            log.error("Error handling DLQ bot detection event", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    @Override
    protected String getServiceName() {
        return "BotDetectionEventsConsumer";
    }
}
