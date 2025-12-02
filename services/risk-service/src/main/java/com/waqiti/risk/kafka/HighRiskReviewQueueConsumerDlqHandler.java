package com.waqiti.risk.kafka;

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
 * DLQ Handler for HighRiskReviewQueueConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class HighRiskReviewQueueConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public HighRiskReviewQueueConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("HighRiskReviewQueueConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.HighRiskReviewQueueConsumer.dlq:HighRiskReviewQueueConsumer.dlq}",
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
            log.info("Processing HIGH RISK DLQ event: topic={}, event={}",
                headers.get("kafka_receivedTopic"), event.getClass().getSimpleName());

            // This is a CRITICAL topic - high-risk review queue
            log.error("CRITICAL: High-risk review event failed - requires immediate attention");

            // Check if error is recoverable
            Exception lastException = (Exception) headers.get("exception");
            boolean isRecoverable = lastException != null &&
                (lastException instanceof java.net.SocketTimeoutException ||
                 (lastException.getMessage() != null &&
                  (lastException.getMessage().contains("timeout") ||
                   lastException.getMessage().contains("connection") ||
                   lastException.getMessage().contains("unavailable"))));

            if (isRecoverable) {
                log.info("Recoverable error detected - scheduling retry");
                return DlqProcessingResult.RETRY;
            } else {
                log.warn("Non-recoverable error - persisting for manual review");
                // Log additional context for manual review
                log.error("Failed event details: {}", event);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }

        } catch (Exception e) {
            log.error("Error handling DLQ event", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    @Override
    protected String getServiceName() {
        return "HighRiskReviewQueueConsumer";
    }
}
