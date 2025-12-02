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
 * DLQ Handler for VelocityCheckEventsConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class VelocityCheckEventsConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public VelocityCheckEventsConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("VelocityCheckEventsConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.VelocityCheckEventsConsumer.dlq:VelocityCheckEventsConsumer.dlq}",
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
            // FIXED: Implement velocity check recovery logic
            log.warn("Processing DLQ velocity check event");

            String checkId = headers.getOrDefault("checkId", "").toString();
            String userId = headers.getOrDefault("userId", "").toString();
            String checkType = headers.getOrDefault("checkType", "").toString();
            String limitExceeded = headers.getOrDefault("limitExceeded", "false").toString();
            String failureReason = headers.getOrDefault("failureReason", "").toString();

            if (failureReason.contains("Database") || failureReason.contains("Connection")) {
                log.info("DLQ: Database error, marking for retry. Check: {}", checkId);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("Duplicate") || failureReason.contains("AlreadyChecked")) {
                log.info("DLQ: Velocity already checked. Check: {}. Marking as resolved.", checkId);
                return DlqProcessingResult.DISCARDED;
            } else if (failureReason.contains("BlockingFailed") && "true".equals(limitExceeded)) {
                log.error("DLQ: Failed to block velocity limit violation. User: {}, Type: {}. Retrying.", userId, checkType);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("UserNotFound")) {
                log.warn("DLQ: User not found for velocity check. User: {}. Retrying.", userId);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("Validation") || failureReason.contains("Invalid")) {
                log.error("DLQ: Validation error in velocity check. Check: {}. Manual review required.", checkId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            } else {
                log.error("DLQ: Unknown error in velocity check. Event: {}, Headers: {}.", event, headers);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }
        } catch (Exception e) {
            log.error("Error handling DLQ velocity check event", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    @Override
    protected String getServiceName() {
        return "VelocityCheckEventsConsumer";
    }
}
