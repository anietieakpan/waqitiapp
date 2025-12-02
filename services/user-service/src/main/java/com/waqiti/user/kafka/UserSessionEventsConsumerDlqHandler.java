package com.waqiti.user.kafka;

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
 * DLQ Handler for UserSessionEventsConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class UserSessionEventsConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public UserSessionEventsConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("UserSessionEventsConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.UserSessionEventsConsumer.dlq:UserSessionEventsConsumer.dlq}",
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
            log.info("DLQ: User session event recovery");
            String sessionId = headers.getOrDefault("sessionId", "").toString();
            String userId = headers.getOrDefault("userId", "").toString();
            String eventType = headers.getOrDefault("eventType", "").toString();
            String failureReason = headers.getOrDefault("failureReason", "").toString();

            // Strategy 1: Session termination failure (security concern)
            if (eventType.contains("TERMINATE") && failureReason.contains("termination failed")) {
                log.warn("DLQ: Failed to terminate session: sessionId={}, userId={}", sessionId, userId);
                return DlqProcessingResult.RETRY;
            }

            // Strategy 2: Session tracking/analytics failure (non-critical)
            if (failureReason.contains("analytics") || failureReason.contains("tracking")) {
                log.info("DLQ: Session analytics failed (non-critical): sessionId={}", sessionId);
                return DlqProcessingResult.DISCARDED;
            }

            // Strategy 3: Concurrent session limit enforcement failure
            if (failureReason.contains("concurrent") || failureReason.contains("limit")) {
                log.warn("DLQ: Concurrent session limit check failed: userId={}", userId);
                return DlqProcessingResult.RETRY;
            }

            // Strategy 4: Session expiry event failure
            if (eventType.contains("EXPIRED")) {
                log.info("DLQ: Session expiry event failed (non-critical): sessionId={}", sessionId);
                return DlqProcessingResult.DISCARDED;
            }

            // Strategy 5: Duplicate event
            if (failureReason.contains("duplicate")) {
                return DlqProcessingResult.DISCARDED;
            }

            // Strategy 6: Transient
            if (failureReason.contains("timeout")) {
                return DlqProcessingResult.RETRY;
            }

            log.info("DLQ: User session event recovery: eventType={}, userId={}", eventType, userId);
            return DlqProcessingResult.RETRY;

        } catch (Exception e) {
            log.error("DLQ: Error in user session handler", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    @Override
    protected String getServiceName() {
        return "UserSessionEventsConsumer";
    }
}
