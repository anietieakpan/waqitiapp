package com.waqiti.notification.kafka;

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
 * DLQ Handler for NotificationDeliveryEventsConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class NotificationDeliveryEventsConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public NotificationDeliveryEventsConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("NotificationDeliveryEventsConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.NotificationDeliveryEventsConsumer.dlq:NotificationDeliveryEventsConsumer.dlq}",
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
            log.info("DLQ: Notification delivery recovery");
            String notificationId = headers.getOrDefault("notificationId", "").toString();
            String channel = headers.getOrDefault("channel", "").toString();
            String notificationType = headers.getOrDefault("notificationType", "").toString();
            String failureReason = headers.getOrDefault("failureReason", "").toString();

            if (failureReason.contains("provider") || failureReason.contains("gateway")) {
                log.warn("DLQ: Notification provider failed: channel={}", channel);
                return DlqProcessingResult.RETRY;
            }
            if (failureReason.contains("invalid") || failureReason.contains("address")) {
                log.warn("DLQ: Invalid notification address: notificationId={}", notificationId);
                return DlqProcessingResult.DISCARDED;
            }
            if (failureReason.contains("duplicate")) return DlqProcessingResult.DISCARDED;
            if (failureReason.contains("timeout")) return DlqProcessingResult.RETRY;
            return DlqProcessingResult.RETRY;

        } catch (Exception e) {
            log.error("Error handling DLQ event", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    @Override
    protected String getServiceName() {
        return "NotificationDeliveryEventsConsumer";
    }
}
