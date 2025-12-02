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

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * CRITICAL FIX #15: InternationalTransferDelayedConsumer
 * Notifies users when international transfers are delayed
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class InternationalTransferDelayedConsumer {
    private final NotificationService notificationService;
    private final IdempotencyService idempotencyService;
    private final MetricsCollector metricsCollector;
    private final UniversalDLQHandler dlqHandler;

    @KafkaListener(topics = "international.transfer.delayed", groupId = "notification-transfer-delayed")
    public void handle(TransferDelayedEvent event, Acknowledgment ack) {
        try {
            log.warn("⏱️ TRANSFER DELAYED: transferId={}, userId={}, destination={}, reason={}",
                event.getTransferId(), event.getUserId(), event.getDestinationCountry(), event.getDelayReason());

            String key = "transfer:delayed:" + event.getTransferId();
            if (!idempotencyService.tryAcquire(key, Duration.ofHours(24))) {
                ack.acknowledge();
                return;
            }

            String message = String.format("""
                Your international transfer has been delayed.

                Transfer Details:
                - Amount: %s %s
                - Destination: %s
                - Beneficiary: %s
                - Original ETA: %s
                - Revised ETA: %s

                Delay Reason:
                %s

                What's Happening:
                %s

                We'll notify you as soon as the transfer is processed.
                If you have questions, contact international support:
                Email: international@example.com
                Phone: 1-800-WAQITI
                Reference: %s
                """,
                event.getAmount(),
                event.getCurrency(),
                event.getDestinationCountry(),
                maskAccountDetails(event.getBeneficiaryName()),
                event.getOriginalEta(),
                event.getRevisedEta(),
                event.getDelayReason(),
                getDelayExplanation(event.getDelayReason()),
                event.getTransferId());

            notificationService.sendNotification(event.getUserId(), NotificationType.TRANSFER_DELAYED,
                NotificationChannel.EMAIL, NotificationPriority.HIGH,
                "International Transfer Delayed", message, Map.of());

            notificationService.sendNotification(event.getUserId(), NotificationType.TRANSFER_DELAYED,
                NotificationChannel.SMS, NotificationPriority.MEDIUM, null,
                String.format("Your international transfer to %s is delayed. New ETA: %s. Check email for details.",
                    event.getDestinationCountry(), event.getRevisedEta()), Map.of());

            metricsCollector.incrementCounter("notification.transfer.delayed.sent");
            metricsCollector.incrementCounter("notification.transfer.delayed." +
                event.getDelayReason().toLowerCase().replace(" ", "_"));

            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process transfer delayed event", e);
            dlqHandler.sendToDLQ("international.transfer.delayed", event, e, "Processing failed");
            ack.acknowledge();
        }
    }

    private String getDelayExplanation(String reason) {
        return switch (reason.toLowerCase()) {
            case "bank_holiday" ->
                "The receiving bank is closed for a local holiday. Your transfer will process on the next business day.";
            case "compliance_review" ->
                "Your transfer is undergoing routine compliance verification to ensure security and regulatory compliance.";
            case "correspondent_bank_delay" ->
                "One of the intermediary banks in the transfer chain is experiencing processing delays.";
            case "weekend_processing" ->
                "International transfers don't process on weekends. Your transfer will complete on the next business day.";
            case "beneficiary_bank_hours" ->
                "The beneficiary's bank is currently closed. Processing will resume during their business hours.";
            case "additional_verification" ->
                "Additional verification is required for this transfer. Our team is working to resolve this quickly.";
            default ->
                "Your transfer is experiencing a delay. Our team is working to process it as quickly as possible.";
        };
    }

    private String maskAccountDetails(String name) {
        if (name == null || name.length() <= 3) return name;
        return name.substring(0, 1) + "***" + name.substring(name.length() - 1);
    }

    private static class TransferDelayedEvent {
        private UUID transferId, userId;
        private String destinationCountry, beneficiaryName, delayReason, currency;
        private BigDecimal amount;
        private LocalDateTime originalEta, revisedEta;
        public UUID getTransferId() { return transferId; }
        public UUID getUserId() { return userId; }
        public String getDestinationCountry() { return destinationCountry; }
        public String getBeneficiaryName() { return beneficiaryName; }
        public String getDelayReason() { return delayReason; }
        public String getCurrency() { return currency; }
        public BigDecimal getAmount() { return amount; }
        public LocalDateTime getOriginalEta() { return originalEta; }
        public LocalDateTime getRevisedEta() { return revisedEta; }
    }
}
