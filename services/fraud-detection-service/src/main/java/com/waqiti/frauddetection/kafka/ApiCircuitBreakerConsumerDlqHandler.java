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
 * DLQ Handler for ApiCircuitBreakerConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class ApiCircuitBreakerConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public ApiCircuitBreakerConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("ApiCircuitBreakerConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.ApiCircuitBreakerConsumer.dlq:ApiCircuitBreakerConsumer.dlq}",
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
            // FIXED: Implement API circuit breaker recovery logic
            log.warn("Processing DLQ API circuit breaker event");

            String breakerId = headers.getOrDefault("breakerId", "").toString();
            String serviceName = headers.getOrDefault("serviceName", "").toString();
            String circuitState = headers.getOrDefault("circuitState", "").toString();
            String failureRate = headers.getOrDefault("failureRate", "0").toString();
            String failureReason = headers.getOrDefault("failureReason", "").toString();

            if (failureReason.contains("Database") || failureReason.contains("Connection")) {
                log.info("DLQ: Database error, marking for retry. Breaker: {}", breakerId);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("Duplicate") || failureReason.contains("AlreadyTripped")) {
                log.info("DLQ: Circuit breaker already tripped. Service: {}. Marking as resolved.", serviceName);
                return DlqProcessingResult.DISCARDED;
            } else if (failureReason.contains("NotificationFailed") && "OPEN".equals(circuitState)) {
                log.error("DLQ: Failed to notify about circuit breaker open. Service: {}. Retrying.", serviceName);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("MetricsRecordingFailed")) {
                log.warn("DLQ: Failed to record circuit breaker metrics. Service: {}. Discarding.", serviceName);
                return DlqProcessingResult.DISCARDED; // Non-critical, can discard
            } else if (failureReason.contains("Validation") || failureReason.contains("Invalid")) {
                log.error("DLQ: Validation error in circuit breaker. Breaker: {}. Manual review required.", breakerId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            } else {
                log.error("DLQ: Unknown error in API circuit breaker. Event: {}, Headers: {}.", event, headers);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }
        } catch (Exception e) {
            log.error("Error handling DLQ API circuit breaker event", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    @Override
    protected String getServiceName() {
        return "ApiCircuitBreakerConsumer";
    }
}
