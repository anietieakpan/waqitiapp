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
 * CRITICAL FIX #29: ScheduledTransferCancelledConsumer
 * Notifies users when scheduled transfers are cancelled
 * Impact: Prevents missed payments and user confusion
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ScheduledTransferCancelledConsumer {
    private final NotificationService notificationService;
    private final IdempotencyService idempotencyService;
    private final MetricsCollector metricsCollector;
    private final UniversalDLQHandler dlqHandler;

    @KafkaListener(topics = "transfer.scheduled.cancelled", groupId = "notification-scheduled-transfer-cancelled")
    public void handle(ScheduledTransferCancelledEvent event, Acknowledgment ack) {
        try {
            log.info("🚫 SCHEDULED TRANSFER CANCELLED: transferId={}, userId={}, amount=${}, reason={}",
                event.getTransferId(), event.getUserId(), event.getAmount(), event.getCancellationReason());

            String key = "scheduled:transfer:cancelled:" + event.getTransferId();
            if (!idempotencyService.tryAcquire(key, Duration.ofHours(24))) {
                ack.acknowledge();
                return;
            }

            String message = String.format("""
                Scheduled Transfer Cancelled

                A scheduled transfer has been cancelled.

                Transfer Details:
                - Amount: $%s
                - From: %s
                - To: %s
                - Scheduled Date: %s
                - Transfer Type: %s
                - Frequency: %s

                Cancellation Information:
                - Cancelled By: %s
                - Cancellation Date: %s
                - Reason: %s

                %s

                What This Means:
                • This transfer will NOT be processed on %s
                • No funds will be moved
                • %s

                If You Did NOT Cancel This Transfer:
                🚨 Contact support immediately:
                Email: support@example.com
                Phone: 1-800-WAQITI
                This could indicate unauthorized access to your account.

                To Set Up a New Transfer:
                1. Visit: https://example.com/transfers/schedule
                2. Or use the mobile app: Transfers > Schedule New

                Questions? Contact transfer support:
                Email: transfers@example.com
                Phone: 1-800-WAQITI-XFER
                Reference: Transfer ID %s
                """,
                event.getAmount(),
                event.getFromAccountName(),
                event.getToAccountName(),
                event.getScheduledDate().toLocalDate(),
                event.getTransferType(),
                event.getFrequency(),
                getCancelledByDescription(event.getCancelledBy()),
                event.getCancelledAt(),
                event.getCancellationReason(),
                getReasonExplanation(event.getCancellationCode()),
                event.getScheduledDate().toLocalDate(),
                getRecurringNote(event.getFrequency(), event.isRecurring()),
                event.getTransferId());

            notificationService.sendNotification(event.getUserId(), NotificationType.TRANSFER_CANCELLED,
                NotificationChannel.EMAIL, NotificationPriority.MEDIUM,
                "Scheduled Transfer Cancelled", message, Map.of());

            notificationService.sendNotification(event.getUserId(), NotificationType.TRANSFER_CANCELLED,
                NotificationChannel.PUSH, NotificationPriority.MEDIUM,
                "Transfer Cancelled",
                String.format("Your scheduled transfer of $%s to %s on %s has been cancelled.",
                    event.getAmount(), event.getToAccountName(), event.getScheduledDate().toLocalDate()), Map.of());

            metricsCollector.incrementCounter("notification.scheduled.transfer.cancelled.sent");
            metricsCollector.incrementCounter("notification.scheduled.transfer.cancelled." +
                event.getCancellationCode().toLowerCase().replace(" ", "_"));

            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process scheduled transfer cancelled event", e);
            dlqHandler.sendToDLQ("transfer.scheduled.cancelled", event, e, "Processing failed");
            ack.acknowledge();
        }
    }

    private String getCancelledByDescription(String cancelledBy) {
        return switch (cancelledBy.toLowerCase()) {
            case "user" -> "You (via web or mobile app)";
            case "system" -> "System (automatic cancellation)";
            case "support" -> "Customer Support";
            case "fraud" -> "Fraud Prevention System";
            case "insufficient_funds" -> "Automatic (insufficient funds)";
            default -> cancelledBy;
        };
    }

    private String getReasonExplanation(String cancellationCode) {
        return switch (cancellationCode.toLowerCase()) {
            case "user_requested" ->
                "You cancelled this transfer through the app or website.";
            case "insufficient_funds" ->
                """
                ⚠️ This transfer was automatically cancelled due to insufficient funds.
                To reschedule, ensure your account has sufficient balance before the transfer date.
                """;
            case "account_closed" ->
                """
                This transfer was cancelled because the destination account has been closed.
                Please verify the account details and create a new transfer if needed.
                """;
            case "fraud_suspected" ->
                """
                🚨 This transfer was cancelled by our fraud prevention system for your protection.
                If this was a legitimate transfer, please contact support to verify and reschedule.
                """;
            case "compliance_hold" ->
                """
                This transfer was cancelled due to compliance requirements.
                Our compliance team may contact you for additional information.
                """;
            case "technical_error" ->
                """
                This transfer was cancelled due to a technical issue.
                You can reschedule the transfer at any time.
                """;
            default ->
                "See cancellation reason above for details.";
        };
    }

    private String getRecurringNote(String frequency, boolean isRecurring) {
        if (isRecurring && !"one-time".equalsIgnoreCase(frequency)) {
            return String.format("""
                IMPORTANT: This was a recurring transfer (%s).
                All future occurrences have also been cancelled.
                If you want to continue this recurring transfer, you'll need to set it up again.
                """, frequency);
        }
        return "This was a one-time transfer.";
    }

    private static class ScheduledTransferCancelledEvent {
        private UUID userId, transferId, fromAccountId, toAccountId;
        private String fromAccountName, toAccountName, transferType, frequency;
        private String cancellationReason, cancellationCode, cancelledBy;
        private BigDecimal amount;
        private LocalDateTime scheduledDate, cancelledAt;
        private boolean recurring;

        public UUID getUserId() { return userId; }
        public UUID getTransferId() { return transferId; }
        public UUID getFromAccountId() { return fromAccountId; }
        public UUID getToAccountId() { return toAccountId; }
        public String getFromAccountName() { return fromAccountName; }
        public String getToAccountName() { return toAccountName; }
        public String getTransferType() { return transferType; }
        public String getFrequency() { return frequency; }
        public String getCancellationReason() { return cancellationReason; }
        public String getCancellationCode() { return cancellationCode; }
        public String getCancelledBy() { return cancelledBy; }
        public BigDecimal getAmount() { return amount; }
        public LocalDateTime getScheduledDate() { return scheduledDate; }
        public LocalDateTime getCancelledAt() { return cancelledAt; }
        public boolean isRecurring() { return recurring; }
    }
}
