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
 * DLQ Handler for AsyncReversalTrackingConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class AsyncReversalTrackingConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public AsyncReversalTrackingConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("AsyncReversalTrackingConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.AsyncReversalTrackingConsumer.dlq:AsyncReversalTrackingConsumer.dlq}",
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
            // FIXED: Implement async reversal tracking recovery logic
            log.warn("Processing DLQ async reversal tracking event");

            String reversalId = headers.getOrDefault("reversalId", "").toString();
            String trackingStatus = headers.getOrDefault("trackingStatus", "").toString();
            String lastUpdated = headers.getOrDefault("lastUpdated", "").toString();
            String failureReason = headers.getOrDefault("failureReason", "").toString();

            if (failureReason.contains("Database") || failureReason.contains("Connection")) {
                log.info("DLQ: Database error, marking for retry. Reversal: {}", reversalId);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("Duplicate") || failureReason.contains("AlreadyTracked")) {
                log.info("DLQ: Reversal tracking already recorded. Reversal: {}. Marking as resolved.", reversalId);
                return DlqProcessingResult.DISCARDED;
            } else if (failureReason.contains("ReversalNotFound")) {
                log.warn("DLQ: Reversal not found for tracking. Reversal: {}. Retrying.", reversalId);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("TrackingSystemUnavailable")) {
                log.warn("DLQ: Tracking system unavailable. Reversal: {}. Retrying.", reversalId);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("InvalidStatus")) {
                log.error("DLQ: Invalid tracking status. Reversal: {}, Status: {}. Manual review required.", reversalId, trackingStatus);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            } else {
                log.error("DLQ: Unknown error in async reversal tracking. Event: {}, Headers: {}.", event, headers);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }
        } catch (Exception e) {
            log.error("Error handling DLQ async reversal tracking event", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    @Override
    protected String getServiceName() {
        return "AsyncReversalTrackingConsumer";
    }
}
