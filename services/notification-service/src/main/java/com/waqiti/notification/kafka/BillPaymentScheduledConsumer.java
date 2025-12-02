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
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

/**
 * CRITICAL FIX #48: BillPaymentScheduledConsumer
 * Notifies users when bill payments are scheduled
 * Impact: Payment transparency, avoid late fees
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BillPaymentScheduledConsumer {
    private final NotificationService notificationService;
    private final IdempotencyService idempotencyService;
    private final MetricsCollector metricsCollector;
    private final UniversalDLQHandler dlqHandler;

    @KafkaListener(topics = "bill.payment.scheduled", groupId = "notification-bill-payment-scheduled")
    public void handle(BillPaymentScheduledEvent event, Acknowledgment ack) {
        try {
            log.info("📅 BILL PAYMENT SCHEDULED: userId={}, merchant={}, amount=${}, scheduledDate={}",
                event.getUserId(), event.getMerchantName(), event.getAmount(), event.getScheduledPaymentDate());

            String key = "bill:payment:scheduled:" + event.getPaymentId();
            if (!idempotencyService.tryAcquire(key, Duration.ofHours(24))) {
                ack.acknowledge();
                return;
            }

            long daysUntilPayment = ChronoUnit.DAYS.between(LocalDateTime.now(), event.getScheduledPaymentDate());

            String message = String.format("""
                📅 Bill Payment Scheduled

                Your bill payment has been scheduled successfully.

                Payment Details:
                - Payee: %s
                - Amount: $%s
                - Payment Date: %s
                - Days Until Payment: %d
                - Payment Method: %s
                - Confirmation: #%s

                Bill Information:
                - Bill Type: %s
                - Account Number: %s
                - Due Date: %s
                - %s

                What Happens Next:
                • Payment will be processed automatically on %s
                • Funds will be withdrawn from your %s
                • You'll receive confirmation after payment completes
                • The merchant typically receives payment in 1-3 business days

                %s

                Important Reminders:
                ⚠️ Ensure Sufficient Funds:
                • Make sure your account has at least $%s on %s
                • Payments may fail if funds are insufficient
                • Failed payments may incur fees from merchant

                ✅ You Can Still:
                • Cancel payment (if more than 24 hours before scheduled date)
                • Modify payment amount
                • Change payment date
                • Update payment method

                Manage This Payment:
                • View details: https://example.com/bills/payments/%s
                • Cancel payment: https://example.com/bills/cancel/%s
                • Modify payment: https://example.com/bills/edit/%s

                %s

                Bill Payment Tips:
                💡 Never Miss a Payment:
                • Set up autopay for recurring bills
                • Enable payment reminders (3 days before due)
                • Keep buffer in payment account
                • Review bills for accuracy before paying

                💡 Save Money on Bills:
                • Pay on time to avoid late fees
                • Consider early payment discounts
                • Review bills for errors
                • Negotiate better rates annually

                Questions? Contact bill pay support:
                Email: billpay@example.com
                Phone: 1-800-WAQITI-BILL
                """,
                event.getMerchantName(),
                event.getAmount(),
                event.getScheduledPaymentDate().toLocalDate(),
                daysUntilPayment,
                event.getPaymentMethod(),
                event.getPaymentId(),
                event.getBillType(),
                maskAccountNumber(event.getMerchantAccountNumber()),
                event.getDueDate().toLocalDate(),
                getPaymentTimingStatus(event.getScheduledPaymentDate(), event.getDueDate()),
                event.getScheduledPaymentDate().toLocalDate(),
                event.getPaymentMethod(),
                getAdditionalReminders(daysUntilPayment, event.getAmount()),
                event.getAmount(),
                event.getScheduledPaymentDate().toLocalDate(),
                event.getPaymentId(),
                event.getPaymentId(),
                event.getPaymentId(),
                getRecurringInfo(event.isRecurring(), event.getFrequency()));

            // Priority based on days until payment
            NotificationPriority priority = daysUntilPayment <= 3
                ? NotificationPriority.HIGH : NotificationPriority.MEDIUM;

            notificationService.sendNotification(event.getUserId(), NotificationType.BILL_PAYMENT_SCHEDULED,
                NotificationChannel.EMAIL, priority,
                String.format("Bill Payment Scheduled: %s ($%s)", event.getMerchantName(), event.getAmount()),
                message, Map.of());

            notificationService.sendNotification(event.getUserId(), NotificationType.BILL_PAYMENT_SCHEDULED,
                NotificationChannel.PUSH, NotificationPriority.MEDIUM,
                "Payment Scheduled",
                String.format("$%s payment to %s scheduled for %s",
                    event.getAmount(), event.getMerchantName(), event.getScheduledPaymentDate().toLocalDate()),
                Map.of());

            metricsCollector.incrementCounter("notification.bill.payment.scheduled.sent");
            metricsCollector.recordGauge("bill.payment.amount", event.getAmount().doubleValue());

            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process bill payment scheduled event", e);
            dlqHandler.sendToDLQ("bill.payment.scheduled", event, e, "Processing failed");
            ack.acknowledge();
        }
    }

    private String getPaymentTimingStatus(LocalDateTime scheduledDate, LocalDateTime dueDate) {
        long daysDifference = ChronoUnit.DAYS.between(scheduledDate, dueDate);

        if (daysDifference > 5) {
            return String.format("✅ Early payment (due in %d days)", daysDifference);
        } else if (daysDifference > 0) {
            return String.format("✅ On-time payment (due in %d days)", daysDifference);
        } else if (daysDifference == 0) {
            return "⚠️ Payment scheduled for due date";
        } else {
            return String.format("⚠️ Payment scheduled %d day(s) after due date", Math.abs(daysDifference));
        }
    }

    private String getAdditionalReminders(long daysUntilPayment, BigDecimal amount) {
        if (daysUntilPayment <= 1) {
            return String.format("""
                ⏰ Payment Tomorrow!
                Your payment of $%s will be processed tomorrow.
                Please ensure sufficient funds are available.
                """, amount);
        } else if (daysUntilPayment <= 3) {
            return String.format("""
                📌 Upcoming Payment:
                Your payment is scheduled in %d days.
                You'll receive another reminder 1 day before.
                """, daysUntilPayment);
        }
        return "";
    }

    private String getRecurringInfo(boolean isRecurring, String frequency) {
        if (isRecurring && frequency != null) {
            return String.format("""
                🔄 Recurring Payment:
                This is a recurring payment that will automatically repeat %s.

                Recurring Payment Benefits:
                • Never miss a due date
                • Avoid late fees
                • Better credit score
                • One-time setup

                Manage recurring payments: https://example.com/bills/recurring
                """, frequency.toLowerCase());
        }
        return """
            💡 Consider Autopay:
            Set up recurring payments for this bill to:
            • Never worry about due dates
            • Avoid late fees automatically
            • Simplify bill management

            Set up autopay: https://example.com/bills/autopay
            """;
    }

    private String maskAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.length() < 4) return "****";
        return "****" + accountNumber.substring(accountNumber.length() - 4);
    }

    private static class BillPaymentScheduledEvent {
        private UUID userId, paymentId;
        private String merchantName, merchantAccountNumber, billType;
        private String paymentMethod, frequency;
        private BigDecimal amount;
        private LocalDateTime scheduledPaymentDate, dueDate;
        private boolean recurring;

        public UUID getUserId() { return userId; }
        public UUID getPaymentId() { return paymentId; }
        public String getMerchantName() { return merchantName; }
        public String getMerchantAccountNumber() { return merchantAccountNumber; }
        public String getBillType() { return billType; }
        public String getPaymentMethod() { return paymentMethod; }
        public String getFrequency() { return frequency; }
        public BigDecimal getAmount() { return amount; }
        public LocalDateTime getScheduledPaymentDate() { return scheduledPaymentDate; }
        public LocalDateTime getDueDate() { return dueDate; }
        public boolean isRecurring() { return recurring; }
    }
}
