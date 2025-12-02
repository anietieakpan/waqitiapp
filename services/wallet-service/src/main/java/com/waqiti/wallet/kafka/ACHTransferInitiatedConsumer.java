package com.waqiti.wallet.kafka;

import com.waqiti.common.idempotency.IdempotencyService;
import com.waqiti.common.kafka.dlq.UniversalDLQHandler;
import com.waqiti.common.metrics.MetricsCollector;
import com.waqiti.wallet.service.WalletNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class ACHTransferInitiatedConsumer {
    private final WalletNotificationService notificationService;
    private final IdempotencyService idempotencyService;
    private final MetricsCollector metricsCollector;
    private final UniversalDLQHandler dlqHandler;

    @KafkaListener(topics = "ach.transfer.initiated", groupId = "wallet-ach-transfer-initiated")
    public void handle(ACHTransferEvent event, Acknowledgment ack) {
        try {
            log.info("ACH TRANSFER INITIATED: userId={}, amount=${}", event.getUserId(), event.getAmount());
            String key = "ach:transfer:" + event.getTransferId();
            if (!idempotencyService.tryAcquire(key, Duration.ofHours(24))) {
                ack.acknowledge();
                return;
            }
            notificationService.sendACHTransferNotification(event.getUserId(), event.getTransferId(), 
                event.getAmount(), "ACH transfer initiated. Expected completion: " + 
                event.getExpectedCompletionDate().toLocalDate());
            metricsCollector.incrementCounter("ach.transfer.initiated");
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process ACH transfer event", e);
            dlqHandler.sendToDLQ("ach.transfer.initiated", event, e, "Processing failed");
            ack.acknowledge();
        }
    }

    private static class ACHTransferEvent {
        private UUID userId, transferId;
        private BigDecimal amount;
        private LocalDateTime expectedCompletionDate;
        public UUID getUserId() { return userId; }
        public UUID getTransferId() { return transferId; }
        public BigDecimal getAmount() { return amount; }
        public LocalDateTime getExpectedCompletionDate() { return expectedCompletionDate; }
    }
}
