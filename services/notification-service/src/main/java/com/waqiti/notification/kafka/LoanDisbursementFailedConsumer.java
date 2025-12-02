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
import java.util.Map;
import java.util.UUID;

/**
 * CRITICAL FIX #8: LoanDisbursementFailedConsumer
 * Notifies borrowers when loan disbursement fails
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LoanDisbursementFailedConsumer {
    private final NotificationService notificationService;
    private final IdempotencyService idempotencyService;
    private final MetricsCollector metricsCollector;
    private final UniversalDLQHandler dlqHandler;

    @KafkaListener(topics = "loan.disbursement.failed", groupId = "notification-loan-failed")
    public void handle(LoanFailedEvent event, Acknowledgment ack) {
        try {
            String key = "loan:failed:" + event.getLoanId();
            if (!idempotencyService.tryAcquire(key, Duration.ofHours(24))) {
                ack.acknowledge();
                return;
            }

            String message = String.format("""
                Your loan disbursement of $%s could not be completed.

                Reason: %s
                Loan ID: %s

                Next Steps:
                1. Review the failure reason above
                2. Contact support if you need assistance
                3. You may reapply after addressing the issue

                Support: loans@example.com | 1-800-WAQITI
                """, event.getAmount(), event.getFailureReason(), event.getLoanId());

            notificationService.sendNotification(event.getBorrowerId(), NotificationType.LOAN_DISBURSEMENT_FAILED,
                NotificationChannel.EMAIL, NotificationPriority.HIGH, "Loan Disbursement Failed", message, Map.of());

            notificationService.sendNotification(event.getBorrowerId(), NotificationType.LOAN_DISBURSEMENT_FAILED,
                NotificationChannel.SMS, NotificationPriority.HIGH, null,
                String.format("Your loan of $%s could not be disbursed. Check email for details.", event.getAmount()), Map.of());

            metricsCollector.incrementCounter("notification.loan.failed.sent");
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process loan disbursement failed event", e);
            dlqHandler.sendToDLQ("loan.disbursement.failed", event, e, "Processing failed");
            ack.acknowledge();
        }
    }

    private static class LoanFailedEvent {
        private UUID loanId, borrowerId;
        private BigDecimal amount;
        private String failureReason;
        public UUID getLoanId() { return loanId; }
        public UUID getBorrowerId() { return borrowerId; }
        public BigDecimal getAmount() { return amount; }
        public String getFailureReason() { return failureReason; }
    }
}
