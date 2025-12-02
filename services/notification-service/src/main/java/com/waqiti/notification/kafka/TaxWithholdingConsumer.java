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
 * CRITICAL FIX #12: TaxWithholdingConsumer
 * Notifies users when tax is withheld from their transactions
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TaxWithholdingConsumer {
    private final NotificationService notificationService;
    private final IdempotencyService idempotencyService;
    private final MetricsCollector metricsCollector;
    private final UniversalDLQHandler dlqHandler;

    @KafkaListener(topics = "tax.withholding.applied", groupId = "notification-tax-withholding")
    public void handle(TaxEvent event, Acknowledgment ack) {
        try {
            String key = "tax:withholding:" + event.getTransactionId();
            if (!idempotencyService.tryAcquire(key, Duration.ofHours(24))) {
                ack.acknowledge();
                return;
            }

            String message = String.format("""
                Tax withholding applied to your transaction.

                Transaction Amount: $%s
                Tax Withheld: $%s (%.2f%%)
                Net Amount: $%s
                Tax Type: %s
                Tax Year: %d

                This withholding will be reported on your annual tax form (Form 1099).
                For tax questions, consult with a tax professional.

                Tax Support: tax@example.com
                """,
                event.getGrossAmount(),
                event.getTaxAmount(),
                event.getTaxRate().multiply(BigDecimal.valueOf(100)),
                event.getNetAmount(),
                event.getTaxType(),
                event.getTaxYear());

            notificationService.sendNotification(event.getUserId(), NotificationType.TAX_WITHHOLDING,
                NotificationChannel.EMAIL, NotificationPriority.MEDIUM,
                "Tax Withholding Applied", message, Map.of());

            metricsCollector.incrementCounter("notification.tax.withholding.sent");
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process tax withholding event", e);
            dlqHandler.sendToDLQ("tax.withholding.applied", event, e, "Processing failed");
            ack.acknowledge();
        }
    }

    private static class TaxEvent {
        private UUID userId, transactionId;
        private BigDecimal grossAmount, taxAmount, netAmount, taxRate;
        private String taxType;
        private int taxYear;
        public UUID getUserId() { return userId; }
        public UUID getTransactionId() { return transactionId; }
        public BigDecimal getGrossAmount() { return grossAmount; }
        public BigDecimal getTaxAmount() { return taxAmount; }
        public BigDecimal getNetAmount() { return netAmount; }
        public BigDecimal getTaxRate() { return taxRate; }
        public String getTaxType() { return taxType; }
        public int getTaxYear() { return taxYear; }
    }
}
