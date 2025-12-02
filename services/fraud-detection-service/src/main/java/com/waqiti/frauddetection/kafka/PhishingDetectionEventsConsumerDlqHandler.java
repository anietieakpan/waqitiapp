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
 * DLQ Handler for PhishingDetectionEventsConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class PhishingDetectionEventsConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public PhishingDetectionEventsConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("PhishingDetectionEventsConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.PhishingDetectionEventsConsumer.dlq:PhishingDetectionEventsConsumer.dlq}",
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
            // FIXED: Implement phishing detection recovery logic
            log.error("Processing DLQ phishing detection event - SECURITY CRITICAL");

            String detectionId = headers.getOrDefault("detectionId", "").toString();
            String url = headers.getOrDefault("url", "").toString();
            String userId = headers.getOrDefault("userId", "").toString();
            String phishingScore = headers.getOrDefault("phishingScore", "0").toString();
            String failureReason = headers.getOrDefault("failureReason", "").toString();

            if (failureReason.contains("Database") || failureReason.contains("Connection")) {
                log.error("DLQ: Database error for CRITICAL phishing detection. URL: {}. Retrying.", url);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("URLBlockFailed") && Double.parseDouble(phishingScore) > 85) {
                log.error("DLQ: CRITICAL - Failed to block phishing URL. URL: {}, Score: {}. Retrying.", url, phishingScore);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("Duplicate") || failureReason.contains("AlreadyDetected")) {
                log.info("DLQ: Phishing already detected. URL: {}. Marking as resolved.", url);
                return DlqProcessingResult.DISCARDED;
            } else if (failureReason.contains("UserNotificationFailed")) {
                log.error("DLQ: Failed to notify user of phishing attempt. User: {}. Retrying.", userId);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("PhishingServiceDown")) {
                log.warn("DLQ: Phishing detection service unavailable. Detection: {}. Retrying.", detectionId);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("Validation") || failureReason.contains("Invalid")) {
                log.error("DLQ: Validation error in phishing detection. Detection: {}. URGENT manual review.", detectionId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            } else {
                log.error("DLQ: Unknown error in CRITICAL phishing detection. Event: {}, Headers: {}.", event, headers);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }
        } catch (Exception e) {
            log.error("Error handling DLQ phishing detection event - SECURITY CRITICAL", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    @Override
    protected String getServiceName() {
        return "PhishingDetectionEventsConsumer";
    }
}
