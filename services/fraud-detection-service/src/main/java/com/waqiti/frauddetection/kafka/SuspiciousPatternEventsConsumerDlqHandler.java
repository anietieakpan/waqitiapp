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
 * DLQ Handler for SuspiciousPatternEventsConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class SuspiciousPatternEventsConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public SuspiciousPatternEventsConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("SuspiciousPatternEventsConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.SuspiciousPatternEventsConsumer.dlq:SuspiciousPatternEventsConsumer.dlq}",
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
            // FIXED: Implement suspicious pattern detection recovery logic
            log.warn("Processing DLQ suspicious pattern event");

            String patternId = headers.getOrDefault("patternId", "").toString();
            String entityId = headers.getOrDefault("entityId", "").toString();
            String entityType = headers.getOrDefault("entityType", "").toString();
            String patternType = headers.getOrDefault("patternType", "").toString();
            String confidenceScore = headers.getOrDefault("confidenceScore", "0").toString();
            String failureReason = headers.getOrDefault("failureReason", "").toString();

            if (failureReason.contains("Database") || failureReason.contains("Connection")) {
                log.info("DLQ: Database error, marking for retry. Pattern: {}", patternId);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("Duplicate") || failureReason.contains("AlreadyDetected")) {
                log.info("DLQ: Pattern already detected. Pattern: {}. Marking as resolved.", patternId);
                return DlqProcessingResult.DISCARDED;
            } else if (failureReason.contains("EntityNotFound")) {
                log.warn("DLQ: Entity not found for pattern. Entity: {}, Type: {}. Retrying.", entityId, entityType);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("AlertGenerationFailed") && Double.parseDouble(confidenceScore) > 85) {
                log.error("DLQ: Failed to generate alert for high-confidence pattern. Pattern: {}. Retrying.", patternId);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("PatternAnalysisServiceDown")) {
                log.warn("DLQ: Pattern analysis service unavailable. Pattern: {}. Retrying.", patternId);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("Validation") || failureReason.contains("Invalid")) {
                log.error("DLQ: Validation error in suspicious pattern. Pattern: {}. Manual review required.", patternId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            } else {
                log.error("DLQ: Unknown error in suspicious pattern event. Event: {}, Headers: {}.", event, headers);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }
        } catch (Exception e) {
            log.error("Error handling DLQ suspicious pattern event", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    @Override
    protected String getServiceName() {
        return "SuspiciousPatternEventsConsumer";
    }
}
