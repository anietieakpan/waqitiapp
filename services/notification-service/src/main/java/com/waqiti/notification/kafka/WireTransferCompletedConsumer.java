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
 * CRITICAL FIX #37: WireTransferCompletedConsumer
 * Notifies users when wire transfers successfully complete
 * Impact: High-value transaction confirmation, reduces anxiety
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WireTransferCompletedConsumer {
    private final NotificationService notificationService;
    private final IdempotencyService idempotencyService;
    private final MetricsCollector metricsCollector;
    private final UniversalDLQHandler dlqHandler;

    @KafkaListener(topics = "wire.transfer.completed", groupId = "notification-wire-transfer-completed")
    public void handle(WireTransferEvent event, Acknowledgment ack) {
        try {
            log.info("✅ WIRE TRANSFER COMPLETED: transferId={}, userId={}, amount=${}, type={}",
                event.getTransferId(), event.getUserId(), event.getAmount(), event.getTransferType());

            String key = "wire:transfer:completed:" + event.getTransferId();
            if (!idempotencyService.tryAcquire(key, Duration.ofHours(24))) {
                ack.acknowledge();
                return;
            }

            String message = String.format("""
                ✅ Wire Transfer Completed Successfully

                Your wire transfer has been completed.

                Transfer Details:
                - Amount: $%s
                - Wire Fee: $%s
                - Total Deducted: $%s
                - Transfer Type: %s
                - Completed: %s

                %s

                Beneficiary Information:
                - Name: %s
                - Bank: %s
                - Account: %s
                - %s

                Wire Transfer Details:
                - Wire Reference: %s
                - IMAD: %s
                - OMAD: %s
                - Processing Time: %s

                What Happens Next:
                %s

                Important Information:
                • Wire transfers are generally irreversible
                • Keep this confirmation for your records
                • Contact beneficiary to confirm receipt
                • Report any issues within 24 hours

                %s

                Questions? Contact wire transfer support:
                Email: wires@example.com
                Phone: 1-800-WAQITI-WIRE
                Reference: Wire ID %s
                """,
                event.getAmount(),
                event.getWireFee(),
                event.getAmount().add(event.getWireFee()),
                event.getTransferType(),
                event.getCompletedAt(),
                getTransferTypeDetails(event.getTransferType()),
                event.getBeneficiaryName(),
                event.getBeneficiaryBank(),
                maskAccountNumber(event.getBeneficiaryAccount()),
                event.getTransferType().equalsIgnoreCase("INTERNATIONAL")
                    ? String.format("Country: %s\nSWIFT: %s", event.getBeneficiaryCountry(), event.getSwiftCode())
                    : String.format("Routing Number: %s", event.getRoutingNumber()),
                event.getWireReference(),
                event.getImad() != null ? event.getImad() : "N/A",
                event.getOmad() != null ? event.getOmad() : "N/A",
                event.getProcessingTime(),
                getNextSteps(event.getTransferType()),
                getSecurityReminder(),
                event.getTransferId());

            // Multi-channel notification
            notificationService.sendNotification(event.getUserId(), NotificationType.WIRE_TRANSFER_COMPLETED,
                NotificationChannel.EMAIL, NotificationPriority.HIGH,
                "Wire Transfer Completed", message, Map.of());

            notificationService.sendNotification(event.getUserId(), NotificationType.WIRE_TRANSFER_COMPLETED,
                NotificationChannel.PUSH, NotificationPriority.HIGH,
                "Wire Transfer Sent",
                String.format("Your wire transfer of $%s to %s has been completed successfully.",
                    event.getAmount(), event.getBeneficiaryName()), Map.of());

            // SMS for large amounts
            if (event.getAmount().compareTo(new BigDecimal("10000")) > 0) {
                notificationService.sendNotification(event.getUserId(), NotificationType.WIRE_TRANSFER_COMPLETED,
                    NotificationChannel.SMS, NotificationPriority.MEDIUM, null,
                    String.format("Wire transfer of $%s completed. Ref: %s. Check email for details.",
                        event.getAmount(), event.getWireReference()), Map.of());
            }

            metricsCollector.incrementCounter("notification.wire.transfer.completed.sent");
            metricsCollector.incrementCounter("notification.wire.transfer.completed." +
                event.getTransferType().toLowerCase());
            metricsCollector.recordGauge("wire.transfer.amount", event.getAmount().doubleValue());

            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process wire transfer completed event", e);
            dlqHandler.sendToDLQ("wire.transfer.completed", event, e, "Processing failed");
            ack.acknowledge();
        }
    }

    private String getTransferTypeDetails(String transferType) {
        return switch (transferType.toUpperCase()) {
            case "DOMESTIC" -> """
                Domestic Wire Transfer:
                • Typically arrives same business day
                • Processed through Fedwire system
                • Final and irrevocable once completed
                """;
            case "INTERNATIONAL" -> """
                International Wire Transfer:
                • Typically arrives in 1-5 business days
                • Processed through SWIFT network
                • May involve intermediary banks
                • Exchange rates applied at time of transfer
                """;
            default -> "Wire transfer completed.";
        };
    }

    private String getNextSteps(String transferType) {
        if ("INTERNATIONAL".equalsIgnoreCase(transferType)) {
            return """
                1. Beneficiary should receive funds in 1-5 business days
                2. Contact beneficiary to confirm receipt
                3. Allow time for intermediary bank processing
                4. Check with beneficiary's bank if not received within 5 days
                5. Keep wire reference number for tracking
                """;
        } else {
            return """
                1. Beneficiary should receive funds by end of business day
                2. Contact beneficiary to confirm receipt
                3. Funds are typically available immediately
                4. Contact us if beneficiary doesn't receive funds within 24 hours
                """;
        }
    }

    private String getSecurityReminder() {
        return """
            🔒 Security Reminder:
            Wire transfer fraud is common. Always verify:
            • You know the beneficiary personally
            • Bank details were obtained through trusted channels
            • The request was not made via email or phone call
            • You're not under pressure to send money quickly

            ⚠️ Common Scams:
            • Fake invoice scams (impersonating vendors)
            • Romance scams (fake online relationships)
            • Inheritance scams (fake lawyers/officials)
            • Emergency scams (impersonating family members)

            If you believe you were scammed, contact us immediately.
            """;
    }

    private String maskAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.length() < 4) return "****";
        return "****" + accountNumber.substring(accountNumber.length() - 4);
    }

    private static class WireTransferEvent {
        private UUID userId, transferId;
        private String transferType, beneficiaryName, beneficiaryBank, beneficiaryAccount;
        private String beneficiaryCountry, routingNumber, swiftCode;
        private String wireReference, imad, omad, processingTime;
        private BigDecimal amount, wireFee;
        private LocalDateTime completedAt;

        public UUID getUserId() { return userId; }
        public UUID getTransferId() { return transferId; }
        public String getTransferType() { return transferType; }
        public String getBeneficiaryName() { return beneficiaryName; }
        public String getBeneficiaryBank() { return beneficiaryBank; }
        public String getBeneficiaryAccount() { return beneficiaryAccount; }
        public String getBeneficiaryCountry() { return beneficiaryCountry; }
        public String getRoutingNumber() { return routingNumber; }
        public String getSwiftCode() { return swiftCode; }
        public String getWireReference() { return wireReference; }
        public String getImad() { return imad; }
        public String getOmad() { return omad; }
        public String getProcessingTime() { return processingTime; }
        public BigDecimal getAmount() { return amount; }
        public BigDecimal getWireFee() { return wireFee; }
        public LocalDateTime getCompletedAt() { return completedAt; }
    }
}
