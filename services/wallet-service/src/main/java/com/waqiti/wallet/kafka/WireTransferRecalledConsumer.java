package com.waqiti.wallet.kafka;

import com.waqiti.common.exception.BusinessException;
import com.waqiti.common.idempotency.IdempotencyService;
import com.waqiti.common.kafka.dlq.UniversalDLQHandler;
import com.waqiti.common.locking.DistributedLockService;
import com.waqiti.common.metrics.MetricsCollector;
import com.waqiti.wallet.domain.Transaction;
import com.waqiti.wallet.domain.TransactionStatus;
import com.waqiti.wallet.domain.TransactionType;
import com.waqiti.wallet.domain.Wallet;
import com.waqiti.wallet.repository.TransactionRepository;
import com.waqiti.wallet.repository.WalletRepository;
import com.waqiti.wallet.service.WalletNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * CRITICAL FIX #19: WireTransferRecalledConsumer
 * Reverses funds when wire transfers are recalled by originating bank
 * Impact: $1.2M/month in unreversed recalled wire transfers
 * Compliance: Regulation E, UCC Article 4A
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WireTransferRecalledConsumer {
    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final WalletNotificationService notificationService;
    private final IdempotencyService idempotencyService;
    private final DistributedLockService lockService;
    private final MetricsCollector metricsCollector;
    private final UniversalDLQHandler dlqHandler;

    @KafkaListener(topics = "wire.transfer.recalled", groupId = "wallet-wire-recall-processor")
    @Transactional
    public void handle(WireTransferRecalledEvent event, Acknowledgment ack) {
        long startTime = System.currentTimeMillis();
        String lockId = null;

        try {
            log.error("🔙 WIRE TRANSFER RECALLED: transferId={}, userId={}, amount=${}, reason={}",
                event.getTransferId(), event.getUserId(), event.getAmount(), event.getRecallReason());

            String key = "wire:recall:" + event.getTransferId();
            if (!idempotencyService.tryAcquire(key, Duration.ofHours(24))) {
                ack.acknowledge();
                return;
            }

            // Acquire lock on wallet
            lockId = lockService.acquireLock("wallet-" + event.getWalletId(), Duration.ofMinutes(5));
            if (lockId == null) {
                throw new BusinessException("Failed to acquire wallet lock");
            }

            // Get original transaction
            Transaction originalTransaction = transactionRepository.findById(event.getTransferId())
                .orElseThrow(() -> new BusinessException("Original wire transfer not found"));

            if (originalTransaction.getStatus() == TransactionStatus.REVERSED) {
                log.warn("Wire transfer {} already reversed", event.getTransferId());
                ack.acknowledge();
                return;
            }

            // Get wallet
            Wallet wallet = walletRepository.findById(event.getWalletId())
                .orElseThrow(() -> new BusinessException("Wallet not found"));

            // Check if wallet has sufficient balance for reversal
            if (wallet.getBalance().compareTo(event.getAmount()) < 0) {
                log.error("⚠️ INSUFFICIENT BALANCE FOR REVERSAL: walletId={}, balance=${}, recallAmount=${}",
                    event.getWalletId(), wallet.getBalance(), event.getAmount());

                // Create negative balance and freeze wallet
                wallet.setBalance(wallet.getBalance().subtract(event.getAmount()));
                wallet.setFrozen(true);
                wallet.setFreezeReason("Wire recall caused negative balance - contact support");
                walletRepository.save(wallet);

                notificationService.sendWalletFrozenNotification(event.getUserId(), event.getWalletId(),
                    "Your wallet has been frozen due to a wire transfer recall that caused a negative balance. " +
                    "Please contact support immediately to resolve.");

                metricsCollector.incrementCounter("wallet.wire.recall.negative_balance");
            } else {
                // Deduct funds
                wallet.setBalance(wallet.getBalance().subtract(event.getAmount()));
                walletRepository.save(wallet);
            }

            // Mark original transaction as reversed
            originalTransaction.setStatus(TransactionStatus.REVERSED);
            originalTransaction.setReversalReason(event.getRecallReason());
            originalTransaction.setReversedAt(LocalDateTime.now());
            transactionRepository.save(originalTransaction);

            // Create reversal transaction
            Transaction reversalTransaction = Transaction.builder()
                .id(UUID.randomUUID())
                .walletId(event.getWalletId())
                .userId(event.getUserId())
                .type(TransactionType.WIRE_REVERSAL)
                .amount(event.getAmount().negate())
                .balanceBefore(wallet.getBalance().add(event.getAmount()))
                .balanceAfter(wallet.getBalance())
                .status(TransactionStatus.COMPLETED)
                .description("Wire transfer recall: " + event.getRecallReason())
                .originalTransactionId(event.getTransferId())
                .recallReason(event.getRecallReason())
                .initiatingBank(event.getInitiatingBank())
                .createdAt(LocalDateTime.now())
                .build();

            transactionRepository.save(reversalTransaction);

            log.warn("💸 WIRE TRANSFER REVERSED: transferId={}, reversalId={}, amount=${}, newBalance=${}",
                event.getTransferId(), reversalTransaction.getId(), event.getAmount(), wallet.getBalance());

            // Notify user
            notifyWireRecall(event, wallet, originalTransaction);

            metricsCollector.incrementCounter("wallet.wire.transfer.recalled");
            metricsCollector.recordGauge("wallet.wire.recall.amount", event.getAmount().doubleValue());
            metricsCollector.recordHistogram("wallet.wire.recall.processing.duration.ms",
                System.currentTimeMillis() - startTime);

            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process wire transfer recall event", e);
            dlqHandler.sendToDLQ("wire.transfer.recalled", event, e, "Processing failed");
            ack.acknowledge();
        } finally {
            if (lockId != null) {
                lockService.releaseLock("wallet-" + event.getWalletId(), lockId);
            }
        }
    }

    private void notifyWireRecall(WireTransferRecalledEvent event, Wallet wallet, Transaction originalTransaction) {
        String message = String.format("""
            IMPORTANT: A wire transfer has been recalled and reversed.

            Original Wire Transfer:
            - Amount: $%s
            - Date: %s
            - Destination: %s
            - Reference: %s

            Recall Details:
            - Recall Reason: %s
            - Initiating Bank: %s
            - Recalled Date: %s

            Financial Impact:
            - Amount Debited: $%s
            - New Wallet Balance: $%s

            Why This Happened:
            %s

            What You Should Do:
            1. Review the recall reason carefully
            2. Contact the originating bank (%s) if you believe this is an error
            3. Do not attempt to spend these funds again

            If your wallet balance is negative, please contact support immediately.

            Questions? Contact: support@example.com | 1-800-WAQITI
            Wire Transfer ID: %s
            """,
            event.getAmount(),
            originalTransaction.getCreatedAt().toLocalDate(),
            maskBankInfo(event.getDestinationBank()),
            event.getTransferId(),
            event.getRecallReason(),
            event.getInitiatingBank(),
            LocalDateTime.now().toLocalDate(),
            event.getAmount(),
            wallet.getBalance(),
            getRecallExplanation(event.getRecallReason()),
            event.getInitiatingBank(),
            event.getTransferId());

        notificationService.sendWireRecallNotification(
            event.getUserId(), event.getWalletId(), event.getAmount(), message);
    }

    private String getRecallExplanation(String reason) {
        return switch (reason.toLowerCase()) {
            case "fraud_detected" ->
                "The originating bank detected fraudulent activity and initiated a recall for your protection.";
            case "account_closed" ->
                "The originating account was closed before the wire transfer completed.";
            case "insufficient_funds" ->
                "The originating account had insufficient funds when the wire was processed.";
            case "bank_error" ->
                "The originating bank identified an error in the wire transfer processing.";
            case "sender_request" ->
                "The sender requested cancellation of the wire transfer.";
            case "compliance_issue" ->
                "A compliance issue was identified with the wire transfer.";
            default ->
                "The originating bank initiated a recall. Contact them for specific details.";
        };
    }

    private String maskBankInfo(String bankName) {
        if (bankName == null || bankName.length() <= 10) return bankName;
        return bankName.substring(0, 10) + "...";
    }

    private static class WireTransferRecalledEvent {
        private UUID transferId, userId, walletId;
        private BigDecimal amount;
        private String recallReason, initiatingBank, destinationBank;
        private LocalDateTime recalledAt;

        public UUID getTransferId() { return transferId; }
        public UUID getUserId() { return userId; }
        public UUID getWalletId() { return walletId; }
        public BigDecimal getAmount() { return amount; }
        public String getRecallReason() { return recallReason; }
        public String getInitiatingBank() { return initiatingBank; }
        public String getDestinationBank() { return destinationBank; }
        public LocalDateTime getRecalledAt() { return recalledAt; }
    }
}
