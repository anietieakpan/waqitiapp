package com.waqiti.analytics.events.consumers;

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
 * DLQ Handler for PaymentFailureAnalyticsConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class PaymentFailureAnalyticsConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public PaymentFailureAnalyticsConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("PaymentFailureAnalyticsConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.PaymentFailureAnalyticsConsumer.dlq:PaymentFailureAnalyticsConsumer.dlq}",
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
            log.info("DLQ: Processing analytics event (non-critical, best-effort)");
            String eventId = headers.getOrDefault("eventId", "").toString();
            String failureReason = headers.getOrDefault("failureReason", "").toString();

            // Analytics are non-critical - best effort delivery

            // Strategy 1: Data warehouse unavailable
            if (failureReason.contains("warehouse") || failureReason.contains("elasticsearch")) {
                log.warn("DLQ: Analytics warehouse unavailable, retrying");
                return DlqProcessingResult.RETRY;
            }

            // Strategy 2: Data quality issues
            if (failureReason.contains("invalid data") || failureReason.contains("malformed")) {
                log.info("DLQ: Invalid analytics data, discarding: {}", eventId);
                return DlqProcessingResult.DISCARDED;
            }

            // Strategy 3: Storage quota exceeded
            if (failureReason.contains("quota") || failureReason.contains("storage full")) {
                log.warn("DLQ: Analytics storage full, manual cleanup needed");
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }

            // Strategy 4: Transient
            if (failureReason.contains("timeout") || failureReason.contains("network")) {
                log.info("DLQ: Transient analytics error, retrying");
                return DlqProcessingResult.RETRY;
            }

            // Default: Non-critical, can discard after retries
            log.info("DLQ: Analytics event failed, acceptable loss: {}", eventId);
            return DlqProcessingResult.DISCARDED;

        } catch (Exception e) {
            log.error("DLQ: Error handling analytics event", e);
            return DlqProcessingResult.DISCARDED; // Non-critical
        }
    }

    @Override
    protected String getServiceName() {
        return "PaymentFailureAnalyticsConsumer";
    }
}
