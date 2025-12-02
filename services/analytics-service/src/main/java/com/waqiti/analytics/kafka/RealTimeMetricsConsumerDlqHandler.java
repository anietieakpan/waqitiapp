package com.waqiti.analytics.kafka;

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
 * DLQ Handler for RealTimeMetricsConsumer
 *
 * Handles failed real-time metric events from the dead letter topic
 *
 * @author Waqiti Engineering Team
 * @version 1.0.0
 */
@Service
@Slf4j
public class RealTimeMetricsConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public RealTimeMetricsConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("RealTimeMetricsConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.RealTimeMetricsConsumer.dlq:RealTimeMetricsConsumer.dlq}",
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
            String failureReason = (String) headers.getOrDefault("kafka_exception-message", "Unknown");
            int failureCount = (int) headers.getOrDefault("kafka_dlt-original-offset", 0);

            log.warn("Processing real-time metrics DLQ event: failureReason={} failureCount={}",
                failureReason, failureCount);

            // Most metric failures are non-critical and can be skipped
            if (isDataValidationError(failureReason)) {
                log.warn("Skipping invalid metric data: {}", failureReason);
                return DlqProcessingResult.SUCCESS; // Skip invalid metrics
            }

            if (isTransientError(failureReason)) {
                if (failureCount < 3) {
                    return DlqProcessingResult.RETRY;
                } else {
                    log.warn("Max retries exceeded for transient error, skipping metric");
                    return DlqProcessingResult.SUCCESS; // Skip after retries
                }
            }

            // For unknown errors, log and skip (metrics are not critical)
            log.info("Skipping DLQ metric event with unknown error: {}", failureReason);
            return DlqProcessingResult.SUCCESS;

        } catch (Exception e) {
            log.error("Error handling real-time metrics DLQ event", e);
            return DlqProcessingResult.SUCCESS; // Don't block on metric failures
        }
    }

    private boolean isDataValidationError(String reason) {
        return reason != null && (
            reason.contains("validation") ||
            reason.contains("invalid") ||
            reason.contains("missing") ||
            reason.contains("null")
        );
    }

    private boolean isTransientError(String reason) {
        return reason != null && (
            reason.contains("timeout") ||
            reason.contains("connection") ||
            reason.contains("unavailable")
        );
    }

    @Override
    protected String getServiceName() {
        return "RealTimeMetricsConsumer";
    }

    @Override
    protected boolean isCriticalEvent(Object event) {
        // Metrics are non-critical, system can continue without them
        return false;
    }
}
