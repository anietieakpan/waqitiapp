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
 * CRITICAL FIX #13: CryptoWithdrawalCompletedConsumer
 * Notifies users when crypto withdrawals complete
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CryptoWithdrawalCompletedConsumer {
    private final NotificationService notificationService;
    private final IdempotencyService idempotencyService;
    private final MetricsCollector metricsCollector;
    private final UniversalDLQHandler dlqHandler;

    @KafkaListener(topics = "crypto.withdrawal.completed", groupId = "notification-crypto-withdrawal")
    public void handle(CryptoEvent event, Acknowledgment ack) {
        try {
            String key = "crypto:withdrawal:" + event.getWithdrawalId();
            if (!idempotencyService.tryAcquire(key, Duration.ofHours(24))) {
                ack.acknowledge();
                return;
            }

            String message = String.format("""
                Your cryptocurrency withdrawal is complete!

                Cryptocurrency: %s
                Amount: %s %s
                Destination Address: %s
                Transaction Hash: %s
                Network: %s
                Blockchain Confirmations: %d
                Fee: %s %s
                Completed At: %s

                View on blockchain explorer:
                %s

                SECURITY REMINDER: If you did not authorize this withdrawal, contact support immediately.
                Support: crypto@example.com | 1-800-WAQITI
                """,
                event.getCryptocurrency(),
                event.getAmount(),
                event.getCryptocurrency(),
                maskAddress(event.getDestinationAddress()),
                event.getTransactionHash(),
                event.getNetwork(),
                event.getConfirmations(),
                event.getNetworkFee(),
                event.getCryptocurrency(),
                event.getCompletedAt(),
                event.getBlockchainExplorerUrl());

            notificationService.sendNotification(event.getUserId(), NotificationType.CRYPTO_WITHDRAWAL_COMPLETED,
                NotificationChannel.EMAIL, NotificationPriority.HIGH,
                "Crypto Withdrawal Complete", message, Map.of());

            notificationService.sendNotification(event.getUserId(), NotificationType.CRYPTO_WITHDRAWAL_COMPLETED,
                NotificationChannel.PUSH, NotificationPriority.HIGH,
                "Withdrawal Complete",
                String.format("Your %s withdrawal of %s has been completed. Check email for details.",
                    event.getCryptocurrency(), event.getAmount()), Map.of());

            metricsCollector.incrementCounter("notification.crypto.withdrawal.sent");
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process crypto withdrawal event", e);
            dlqHandler.sendToDLQ("crypto.withdrawal.completed", event, e, "Processing failed");
            ack.acknowledge();
        }
    }

    private String maskAddress(String address) {
        if (address == null || address.length() < 10) return address;
        return address.substring(0, 6) + "..." + address.substring(address.length() - 4);
    }

    private static class CryptoEvent {
        private UUID userId, withdrawalId;
        private String cryptocurrency, destinationAddress, transactionHash, network, blockchainExplorerUrl;
        private BigDecimal amount, networkFee;
        private int confirmations;
        private LocalDateTime completedAt;
        public UUID getUserId() { return userId; }
        public UUID getWithdrawalId() { return withdrawalId; }
        public String getCryptocurrency() { return cryptocurrency; }
        public String getDestinationAddress() { return destinationAddress; }
        public String getTransactionHash() { return transactionHash; }
        public String getNetwork() { return network; }
        public String getBlockchainExplorerUrl() { return blockchainExplorerUrl; }
        public BigDecimal getAmount() { return amount; }
        public BigDecimal getNetworkFee() { return networkFee; }
        public int getConfirmations() { return confirmations; }
        public LocalDateTime getCompletedAt() { return completedAt; }
    }
}
