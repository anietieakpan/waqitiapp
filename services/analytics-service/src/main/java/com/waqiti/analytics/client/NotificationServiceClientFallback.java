package com.waqiti.analytics.client;

import com.waqiti.analytics.dto.notification.NotificationRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Fallback implementation for NotificationServiceClient
 *
 * When notification-service is unavailable, publishes notification request
 * to Kafka topic for asynchronous delivery when service recovers.
 *
 * @author Waqiti Analytics Team
 * @version 1.0.0-PRODUCTION
 * @since 2025-11-15
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationServiceClientFallback implements NotificationServiceClient {

    private final KafkaTemplate<String, NotificationRequest> kafkaTemplate;

    private static final String NOTIFICATION_FALLBACK_TOPIC = "analytics.notifications.fallback";

    @Override
    public void sendNotification(NotificationRequest request) {
        log.warn("Notification service unavailable, publishing to Kafka fallback topic: {}",
                request.getCorrelationId());

        try {
            kafkaTemplate.send(NOTIFICATION_FALLBACK_TOPIC, request.getCorrelationId(), request);
            log.info("Published notification to fallback topic: correlationId={}",
                    request.getCorrelationId());
        } catch (Exception e) {
            log.error("CRITICAL: Failed to publish notification to fallback topic. " +
                     "Notification may be lost! correlationId={}",
                     request.getCorrelationId(), e);
        }
    }
}
