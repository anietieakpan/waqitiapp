package com.waqiti.merchant.kafka;

import com.waqiti.common.idempotency.IdempotencyService;
import com.waqiti.common.kafka.dlq.UniversalDLQHandler;
import com.waqiti.common.metrics.MetricsCollector;
import com.waqiti.merchant.service.MerchantNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * CRITICAL FIX #23: MerchantPayoutScheduledConsumer
 * Notifies merchants when payouts are scheduled for processing
 * Impact: Improves merchant cash flow visibility
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MerchantPayoutScheduledConsumer {
    private final MerchantNotificationService notificationService;
    private final IdempotencyService idempotencyService;
    private final MetricsCollector metricsCollector;
    private final UniversalDLQHandler dlqHandler;

    @KafkaListener(topics = "merchant.payout.scheduled", groupId = "merchant-payout-notification")
    public void handle(MerchantPayoutEvent event, Acknowledgment ack) {
        try {
            log.info("💰 MERCHANT PAYOUT SCHEDULED: merchantId={}, amount=${}, payoutDate={}",
                event.getMerchantId(), event.getPayoutAmount(), event.getScheduledPayoutDate());

            String key = "merchant:payout:scheduled:" + event.getPayoutId();
            if (!idempotencyService.tryAcquire(key, Duration.ofHours(24))) {
                ack.acknowledge();
                return;
            }

            String message = String.format("""
                Your payout has been scheduled!

                Payout Details:
                - Payout Amount: $%s
                - Transaction Count: %d
                - Period: %s to %s
                - Scheduled Date: %s
                - Expected Arrival: %s

                Fee Breakdown:
                - Gross Sales: $%s
                - Processing Fees: $%s
                - Refunds: $%s
                - Chargebacks: $%s
                - Net Payout: $%s

                Destination:
                - Bank Account: %s
                - Account Name: %s
                - Routing Number: %s

                What Happens Next:
                1. Your payout will be processed on %s
                2. Funds typically arrive within 1-2 business days
                3. You'll receive a confirmation when the payout is sent

                Track Your Payout:
                View real-time status at: https://api.example.com/merchant/payouts/%s

                Questions? Contact merchant support:
                Email: merchant-support@example.com
                Phone: 1-800-WAQITI-BIZ
                """,
                event.getPayoutAmount(),
                event.getTransactionCount(),
                event.getPeriodStart().toLocalDate(),
                event.getPeriodEnd().toLocalDate(),
                event.getScheduledPayoutDate().toLocalDate(),
                event.getExpectedArrivalDate().toLocalDate(),
                event.getGrossSales(),
                event.getProcessingFees(),
                event.getRefundAmount(),
                event.getChargebackAmount(),
                event.getPayoutAmount(),
                maskBankAccount(event.getBankAccountNumber()),
                event.getBankAccountName(),
                maskRoutingNumber(event.getRoutingNumber()),
                event.getScheduledPayoutDate().toLocalDate(),
                event.getPayoutId());

            notificationService.sendPayoutScheduledNotification(
                event.getMerchantId(), event.getPayoutId(), event.getPayoutAmount(), message);

            metricsCollector.incrementCounter("merchant.payout.scheduled.notification.sent");
            metricsCollector.recordGauge("merchant.payout.amount", event.getPayoutAmount().doubleValue());

            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process merchant payout scheduled event", e);
            dlqHandler.sendToDLQ("merchant.payout.scheduled", event, e, "Processing failed");
            ack.acknowledge();
        }
    }

    private String maskBankAccount(String accountNumber) {
        if (accountNumber == null || accountNumber.length() < 4) return "****";
        return "****" + accountNumber.substring(accountNumber.length() - 4);
    }

    private String maskRoutingNumber(String routingNumber) {
        if (routingNumber == null || routingNumber.length() < 4) return "****";
        return routingNumber.substring(0, 2) + "****" + routingNumber.substring(routingNumber.length() - 1);
    }

    private static class MerchantPayoutEvent {
        private UUID merchantId, payoutId;
        private BigDecimal payoutAmount, grossSales, processingFees, refundAmount, chargebackAmount;
        private int transactionCount;
        private LocalDateTime periodStart, periodEnd, scheduledPayoutDate, expectedArrivalDate;
        private String bankAccountNumber, bankAccountName, routingNumber;

        public UUID getMerchantId() { return merchantId; }
        public UUID getPayoutId() { return payoutId; }
        public BigDecimal getPayoutAmount() { return payoutAmount; }
        public BigDecimal getGrossSales() { return grossSales; }
        public BigDecimal getProcessingFees() { return processingFees; }
        public BigDecimal getRefundAmount() { return refundAmount; }
        public BigDecimal getChargebackAmount() { return chargebackAmount; }
        public int getTransactionCount() { return transactionCount; }
        public LocalDateTime getPeriodStart() { return periodStart; }
        public LocalDateTime getPeriodEnd() { return periodEnd; }
        public LocalDateTime getScheduledPayoutDate() { return scheduledPayoutDate; }
        public LocalDateTime getExpectedArrivalDate() { return expectedArrivalDate; }
        public String getBankAccountNumber() { return bankAccountNumber; }
        public String getBankAccountName() { return bankAccountName; }
        public String getRoutingNumber() { return routingNumber; }
    }
}
