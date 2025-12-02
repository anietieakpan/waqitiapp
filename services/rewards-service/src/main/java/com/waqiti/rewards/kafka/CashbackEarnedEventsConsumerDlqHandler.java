package com.waqiti.rewards.kafka;

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
 * DLQ Handler for CashbackEarnedEventsConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class CashbackEarnedEventsConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public CashbackEarnedEventsConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("CashbackEarnedEventsConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.CashbackEarnedEventsConsumer.dlq:CashbackEarnedEventsConsumer.dlq}",
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
            // FIXED: Implement cashback recovery logic
            log.warn("Processing DLQ cashback earned event for recovery");

            // Extract event details
            String eventType = headers.getOrDefault("eventType", "UNKNOWN").toString();
            String userId = headers.getOrDefault("userId", "").toString();
            String transactionId = headers.getOrDefault("transactionId", "").toString();

            // Attempt recovery based on failure reason
            String failureReason = headers.getOrDefault("failureReason", "").toString();

            if (failureReason.contains("Database") || failureReason.contains("Connection")) {
                // Transient database error - safe to retry
                log.info("DLQ: Database error detected, marking for retry. Transaction: {}", transactionId);
                return DlqProcessingResult.RETRY;

            } else if (failureReason.contains("Validation") || failureReason.contains("Invalid")) {
                // Data validation error - needs manual fix
                log.error("DLQ: Validation error in cashback event. User: {}, Transaction: {}. Manual review required.",
                        userId, transactionId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;

            } else if (failureReason.contains("Duplicate")) {
                // Duplicate event - can be safely ignored
                log.info("DLQ: Duplicate cashback event detected. Transaction: {}. Marking as resolved.",
                        transactionId);
                return DlqProcessingResult.DISCARDED;

            } else {
                // Unknown error - log for investigation
                log.error("DLQ: Unknown error in cashback event. Event: {}, Headers: {}. Requires investigation.",
                        event, headers);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }

        } catch (Exception e) {
            log.error("Error handling DLQ cashback event", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    @Override
    protected String getServiceName() {
        return "CashbackEarnedEventsConsumer";
    }
}
