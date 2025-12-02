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
 * CRITICAL FIX #30: AutopayEnabledConsumer
 * Notifies users when autopay is enabled for bills
 * Impact: Payment transparency, prevents surprise charges
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AutopayEnabledConsumer {
    private final NotificationService notificationService;
    private final IdempotencyService idempotencyService;
    private final MetricsCollector metricsCollector;
    private final UniversalDLQHandler dlqHandler;

    @KafkaListener(topics = "bill.autopay.enabled", groupId = "notification-autopay-enabled")
    public void handle(AutopayEnabledEvent event, Acknowledgment ack) {
        try {
            log.info("🔄 AUTOPAY ENABLED: userId={}, merchant={}, paymentMethod={}",
                event.getUserId(), event.getMerchantName(), event.getPaymentMethod());

            String key = "autopay:enabled:" + event.getAutopayId();
            if (!idempotencyService.tryAcquire(key, Duration.ofHours(24))) {
                ack.acknowledge();
                return;
            }

            String message = String.format("""
                Autopay Enabled Successfully

                You've enabled automatic payments for your bills.

                Merchant/Biller:
                - Name: %s
                - Account Number: %s
                - Bill Type: %s

                Autopay Settings:
                - Payment Method: %s
                - Payment Account: %s
                - Payment Timing: %s
                - Maximum Amount: %s
                - Enabled Date: %s
                - First Payment: %s

                How Autopay Works:
                %s

                What to Expect:
                • You'll receive a notification 3 days before each automatic payment
                • Payments will be processed automatically on the due date
                • You'll receive confirmation after each payment
                • You can disable autopay at any time

                Important Reminders:
                • Ensure your %s always has sufficient funds
                • Update your payment method if your card expires or changes
                • Monitor your email for payment notifications
                • Review your statements regularly

                Autopay Benefits:
                ✅ Never miss a payment
                ✅ Avoid late fees
                ✅ Build positive payment history
                ✅ Save time and hassle

                To Manage Autopay:
                • Disable: https://example.com/bills/autopay/%s
                • Change payment method: https://example.com/payment-methods
                • View autopay schedule: https://example.com/bills/autopay

                Questions? Contact billing support:
                Email: bills@example.com
                Phone: 1-800-WAQITI-BILL
                Reference: Autopay ID %s
                """,
                event.getMerchantName(),
                maskAccountNumber(event.getMerchantAccountNumber()),
                event.getBillType(),
                event.getPaymentMethod(),
                maskPaymentAccount(event.getPaymentAccountNumber()),
                getPaymentTimingDescription(event.getPaymentTiming()),
                event.getMaximumAmount() != null
                    ? String.format("$%s (protection against unexpected charges)", event.getMaximumAmount())
                    : "No limit set",
                event.getEnabledAt(),
                event.getNextPaymentDate() != null ? event.getNextPaymentDate().toLocalDate() : "When next bill is due",
                getHowAutopayWorks(event.getBillType(), event.getPaymentTiming()),
                event.getPaymentMethod(),
                event.getAutopayId(),
                event.getAutopayId());

            notificationService.sendNotification(event.getUserId(), NotificationType.AUTOPAY_ENABLED,
                NotificationChannel.EMAIL, NotificationPriority.MEDIUM,
                "Autopay Enabled", message, Map.of());

            notificationService.sendNotification(event.getUserId(), NotificationType.AUTOPAY_ENABLED,
                NotificationChannel.PUSH, NotificationPriority.MEDIUM,
                "Autopay Enabled",
                String.format("Autopay is now active for %s. Bills will be paid automatically using %s.",
                    event.getMerchantName(), event.getPaymentMethod()), Map.of());

            metricsCollector.incrementCounter("notification.autopay.enabled.sent");
            metricsCollector.incrementCounter("notification.autopay.enabled." +
                event.getBillType().toLowerCase().replace(" ", "_"));

            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process autopay enabled event", e);
            dlqHandler.sendToDLQ("bill.autopay.enabled", event, e, "Processing failed");
            ack.acknowledge();
        }
    }

    private String getPaymentTimingDescription(String paymentTiming) {
        return switch (paymentTiming.toLowerCase()) {
            case "on_due_date" -> "On the bill due date";
            case "before_due_date" -> "3 days before the due date";
            case "upon_receipt" -> "Immediately when bill is received";
            case "minimum_payment" -> "Minimum payment by due date";
            case "statement_balance" -> "Full statement balance by due date";
            default -> paymentTiming;
        };
    }

    private String getHowAutopayWorks(String billType, String paymentTiming) {
        String baseExplanation;

        if ("credit_card".equalsIgnoreCase(billType)) {
            baseExplanation = switch (paymentTiming.toLowerCase()) {
                case "minimum_payment" ->
                    "We'll automatically pay the minimum payment on your credit card by the due date.";
                case "statement_balance" ->
                    "We'll automatically pay your full statement balance by the due date to avoid interest charges.";
                default ->
                    "We'll automatically pay your credit card bill according to your selected payment timing.";
            };
        } else if ("utility".equalsIgnoreCase(billType)) {
            baseExplanation = "When your utility company sends us your bill, we'll automatically pay it on the due date.";
        } else if ("subscription".equalsIgnoreCase(billType)) {
            baseExplanation = "Your subscription will be automatically renewed and paid on the billing date.";
        } else {
            baseExplanation = String.format("Your %s bill will be automatically paid %s.",
                billType, paymentTiming.replace("_", " "));
        }

        return baseExplanation + "\nYou'll always receive advance notice before any payment is processed.";
    }

    private String maskAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.length() < 4) return "****";
        return "****" + accountNumber.substring(accountNumber.length() - 4);
    }

    private String maskPaymentAccount(String accountNumber) {
        if (accountNumber == null || accountNumber.length() < 4) return "****";
        return "****" + accountNumber.substring(accountNumber.length() - 4);
    }

    private static class AutopayEnabledEvent {
        private UUID userId, autopayId, merchantId;
        private String merchantName, merchantAccountNumber, billType;
        private String paymentMethod, paymentAccountNumber, paymentTiming;
        private BigDecimal maximumAmount;
        private LocalDateTime enabledAt, nextPaymentDate;

        public UUID getUserId() { return userId; }
        public UUID getAutopayId() { return autopayId; }
        public UUID getMerchantId() { return merchantId; }
        public String getMerchantName() { return merchantName; }
        public String getMerchantAccountNumber() { return merchantAccountNumber; }
        public String getBillType() { return billType; }
        public String getPaymentMethod() { return paymentMethod; }
        public String getPaymentAccountNumber() { return paymentAccountNumber; }
        public String getPaymentTiming() { return paymentTiming; }
        public BigDecimal getMaximumAmount() { return maximumAmount; }
        public LocalDateTime getEnabledAt() { return enabledAt; }
        public LocalDateTime getNextPaymentDate() { return nextPaymentDate; }
    }
}
