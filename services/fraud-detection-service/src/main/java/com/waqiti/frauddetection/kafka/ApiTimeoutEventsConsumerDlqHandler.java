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
 * DLQ Handler for ApiTimeoutEventsConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class ApiTimeoutEventsConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public ApiTimeoutEventsConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("ApiTimeoutEventsConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.ApiTimeoutEventsConsumer.dlq:ApiTimeoutEventsConsumer.dlq}",
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
            // FIXED: Implement API timeout events recovery logic
            log.warn("Processing DLQ API timeout events");

            String timeoutId = headers.getOrDefault("timeoutId", "").toString();
            String serviceName = headers.getOrDefault("serviceName", "").toString();
            String endpoint = headers.getOrDefault("endpoint", "").toString();
            String timeoutDuration = headers.getOrDefault("timeoutDuration", "0").toString();
            String failureReason = headers.getOrDefault("failureReason", "").toString();

            if (failureReason.contains("Database") || failureReason.contains("Connection")) {
                log.info("DLQ: Database error, marking for retry. Timeout: {}", timeoutId);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("Duplicate") || failureReason.contains("AlreadyRecorded")) {
                log.info("DLQ: Timeout already recorded. Service: {}, Endpoint: {}. Marking as resolved.", serviceName, endpoint);
                return DlqProcessingResult.DISCARDED;
            } else if (failureReason.contains("MetricsRecordingFailed")) {
                log.warn("DLQ: Failed to record timeout metrics. Service: {}. Discarding non-critical event.", serviceName);
                return DlqProcessingResult.DISCARDED;
            } else if (failureReason.contains("AlertingFailed") && Integer.parseInt(timeoutDuration) > 30000) {
                log.error("DLQ: Failed to alert on severe timeout (>30s). Service: {}, Endpoint: {}. Retrying.", serviceName, endpoint);
                return DlqProcessingResult.RETRY;
            } else {
                log.error("DLQ: Unknown error in API timeout events. Event: {}, Headers: {}.", event, headers);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }
        } catch (Exception e) {
            log.error("Error handling DLQ API timeout events", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    @Override
    protected String getServiceName() {
        return "ApiTimeoutEventsConsumer";
    }
}
