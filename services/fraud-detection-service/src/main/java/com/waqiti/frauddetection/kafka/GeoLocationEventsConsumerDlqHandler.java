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
 * DLQ Handler for GeoLocationEventsConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class GeoLocationEventsConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public GeoLocationEventsConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("GeoLocationEventsConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.GeoLocationEventsConsumer.dlq:GeoLocationEventsConsumer.dlq}",
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
            // FIXED: Implement geo-location fraud detection recovery logic
            log.warn("Processing DLQ geo-location event");

            String eventId = headers.getOrDefault("eventId", "").toString();
            String userId = headers.getOrDefault("userId", "").toString();
            String latitude = headers.getOrDefault("latitude", "0").toString();
            String longitude = headers.getOrDefault("longitude", "0").toString();
            String country = headers.getOrDefault("country", "").toString();
            String suspicionLevel = headers.getOrDefault("suspicionLevel", "").toString();
            String failureReason = headers.getOrDefault("failureReason", "").toString();

            if (failureReason.contains("Database") || failureReason.contains("Connection")) {
                log.info("DLQ: Database error, marking for retry. Event: {}", eventId);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("Duplicate") || failureReason.contains("AlreadyProcessed")) {
                log.info("DLQ: Geo-location already processed. Event: {}. Marking as resolved.", eventId);
                return DlqProcessingResult.DISCARDED;
            } else if (failureReason.contains("SuspiciousLocation") && "HIGH".equals(suspicionLevel)) {
                log.error("DLQ: High-risk location detected. User: {}, Country: {}. Manual review required.", userId, country);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            } else if (failureReason.contains("GeoServiceUnavailable")) {
                log.warn("DLQ: Geo-location service unavailable. Event: {}. Retrying.", eventId);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("UserNotFound")) {
                log.warn("DLQ: User not found for geo-location. User: {}. Retrying.", userId);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("Validation") || failureReason.contains("Invalid")) {
                log.error("DLQ: Validation error in geo-location. Event: {}. Manual review required.", eventId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            } else {
                log.error("DLQ: Unknown error in geo-location. Event: {}, Headers: {}.", event, headers);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }
        } catch (Exception e) {
            log.error("Error handling DLQ geo-location event", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    @Override
    protected String getServiceName() {
        return "GeoLocationEventsConsumer";
    }
}
