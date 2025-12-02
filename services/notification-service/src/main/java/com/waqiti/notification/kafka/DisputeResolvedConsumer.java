package com.waqiti.notification.kafka;

import com.waqiti.common.idempotency.IdempotencyService;
import com.waqiti.common.kafka.dlq.UniversalDLQHandler;
import com.waqiti.common.metrics.MetricsCollector;
import com.waqiti.notification.domain.NotificationChannel;
import com.waqiti.notification.domain.NotificationPriority;
import com.waqiti.notification.domain.NotificationType;
import com.waqiti.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * CRITICAL FIX #7: DisputeResolvedConsumer
 * Notifies users/merchants when disputes are resolved
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DisputeResolvedConsumer {
    private final NotificationService notificationService;
    private final IdempotencyService idempotencyService;
    private final MetricsCollector metricsCollector;
    private final UniversalDLQHandler dlqHandler;

    @KafkaListener(topics = "transaction.disputed.resolved", groupId = "notification-dispute-resolved")
    @Transactional
    public void handle(DisputeResolvedEvent event, Acknowledgment ack) {
        try {
            String key = "dispute:resolved:" + event.getDisputeId();
            if (!idempotencyService.tryAcquire(key, Duration.ofHours(24))) {
                ack.acknowledge();
                return;
            }

            String outcome = event.getResolution();
            String message = String.format("Your dispute for $%s has been resolved: %s. Resolution: %s",
                event.getAmount(), outcome, event.getResolutionDetails());

            notificationService.sendNotification(event.getUserId(), NotificationType.DISPUTE_RESOLVED,
                NotificationChannel.EMAIL, NotificationPriority.HIGH, "Dispute Resolved", message, Map.of());

            if (event.getMerchantId() != null) {
                notificationService.sendNotification(event.getMerchantId(), NotificationType.DISPUTE_RESOLVED,
                    NotificationChannel.EMAIL, NotificationPriority.HIGH, "Dispute Resolution",
                    String.format("Dispute case %s resolved: %s", event.getDisputeId(), outcome), Map.of());
            }

            metricsCollector.incrementCounter("notification.dispute.resolved.sent");
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process dispute resolved event", e);
            dlqHandler.sendToDLQ("transaction.disputed.resolved", event, e, "Processing failed");
            ack.acknowledge();
        }
    }

    private static class DisputeResolvedEvent {
        private UUID disputeId, userId, merchantId;
        private BigDecimal amount;
        private String resolution, resolutionDetails;
        public UUID getDisputeId() { return disputeId; }
        public UUID getUserId() { return userId; }
        public UUID getMerchantId() { return merchantId; }
        public BigDecimal getAmount() { return amount; }
        public String getResolution() { return resolution; }
        public String getResolutionDetails() { return resolutionDetails; }
    }
}
