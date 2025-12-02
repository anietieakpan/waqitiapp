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
 * DLQ Handler for PatternAnalysisConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class PatternAnalysisConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public PatternAnalysisConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("PatternAnalysisConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.PatternAnalysisConsumer.dlq:PatternAnalysisConsumer.dlq}",
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
            // FIXED: Implement pattern analysis recovery logic
            log.warn("Processing DLQ pattern analysis event");

            String analysisId = headers.getOrDefault("analysisId", "").toString();
            String patternType = headers.getOrDefault("patternType", "").toString();
            String entityId = headers.getOrDefault("entityId", "").toString();
            String confidenceScore = headers.getOrDefault("confidenceScore", "0").toString();
            String failureReason = headers.getOrDefault("failureReason", "").toString();

            if (failureReason.contains("Database") || failureReason.contains("Connection")) {
                log.info("DLQ: Database error, marking for retry. Analysis: {}", analysisId);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("Duplicate") || failureReason.contains("AlreadyAnalyzed")) {
                log.info("DLQ: Pattern already analyzed. Analysis: {}. Marking as resolved.", analysisId);
                return DlqProcessingResult.DISCARDED;
            } else if (failureReason.contains("MLModelUnavailable")) {
                log.warn("DLQ: ML pattern analysis model unavailable. Analysis: {}. Retrying.", analysisId);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("EntityNotFound")) {
                log.warn("DLQ: Entity not found for pattern analysis. Entity: {}. Retrying.", entityId);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("Validation") || failureReason.contains("Invalid")) {
                log.error("DLQ: Validation error in pattern analysis. Analysis: {}. Manual review required.", analysisId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            } else {
                log.error("DLQ: Unknown error in pattern analysis. Event: {}, Headers: {}.", event, headers);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }
        } catch (Exception e) {
            log.error("Error handling DLQ pattern analysis event", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    @Override
    protected String getServiceName() {
        return "PatternAnalysisConsumer";
    }
}
