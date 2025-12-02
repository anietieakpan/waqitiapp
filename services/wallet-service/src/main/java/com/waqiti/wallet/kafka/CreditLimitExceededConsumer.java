package com.waqiti.wallet.kafka;

import com.waqiti.common.exception.BusinessException;
import com.waqiti.common.idempotency.IdempotencyService;
import com.waqiti.common.kafka.dlq.UniversalDLQHandler;
import com.waqiti.common.locking.DistributedLockService;
import com.waqiti.common.metrics.MetricsCollector;
import com.waqiti.wallet.domain.Transaction;
import com.waqiti.wallet.domain.TransactionStatus;
import com.waqiti.wallet.repository.TransactionRepository;
import com.waqiti.wallet.service.WalletNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

/**
 * CRITICAL FIX #11: CreditLimitExceededConsumer
 * Blocks transactions that exceed user's credit limit
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CreditLimitExceededConsumer {
    private final TransactionRepository transactionRepository;
    private final WalletNotificationService notificationService;
    private final IdempotencyService idempotencyService;
    private final DistributedLockService lockService;
    private final MetricsCollector metricsCollector;
    private final UniversalDLQHandler dlqHandler;

    @KafkaListener(topics = "credit.limit.exceeded", groupId = "wallet-credit-limit-enforcement")
    @Transactional
    public void handle(CreditLimitEvent event, Acknowledgment ack) {
        long startTime = System.currentTimeMillis();
        String lockId = null;

        try {
            log.warn("🚫 CREDIT LIMIT EXCEEDED: userId={}, transactionId={}, amount=${}, limit=${}",
                event.getUserId(), event.getTransactionId(), event.getAttemptedAmount(), event.getCreditLimit());

            String key = "credit:limit:" + event.getTransactionId();
            if (!idempotencyService.tryAcquire(key, Duration.ofHours(24))) {
                ack.acknowledge();
                return;
            }

            // Lock to prevent race condition
            lockId = lockService.acquireLock("credit-limit-" + event.getTransactionId(), Duration.ofMinutes(5));
            if (lockId == null) {
                throw new BusinessException("Failed to acquire lock");
            }

            // Block the transaction
            Transaction transaction = transactionRepository.findById(event.getTransactionId())
                .orElseThrow(() -> new BusinessException("Transaction not found"));

            if (transaction.getStatus() != TransactionStatus.PENDING) {
                log.warn("Transaction {} already processed - status: {}", event.getTransactionId(), transaction.getStatus());
                ack.acknowledge();
                return;
            }

            transaction.setStatus(TransactionStatus.REJECTED);
            transaction.setRejectionReason("Credit limit exceeded");
            transactionRepository.save(transaction);

            log.error("✅ TRANSACTION BLOCKED: transactionId={}, amount=${}, exceeded by ${}",
                event.getTransactionId(), event.getAttemptedAmount(),
                event.getAttemptedAmount().subtract(event.getCreditLimit()));

            // Notify user
            String message = String.format("""
                Transaction declined - credit limit exceeded.

                Attempted Amount: $%s
                Your Credit Limit: $%s
                Over Limit By: $%s

                To increase your limit, contact support or improve your credit score.
                """,
                event.getAttemptedAmount(),
                event.getCreditLimit(),
                event.getAttemptedAmount().subtract(event.getCreditLimit()));

            notificationService.sendCreditLimitNotification(event.getUserId(),
                event.getAttemptedAmount(), event.getCreditLimit(), message);

            metricsCollector.incrementCounter("wallet.credit.limit.exceeded.blocked");
            metricsCollector.recordHistogram("wallet.credit.limit.enforcement.duration.ms",
                System.currentTimeMillis() - startTime);

            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process credit limit exceeded event", e);
            dlqHandler.sendToDLQ("credit.limit.exceeded", event, e, "Processing failed");
            ack.acknowledge();
        } finally {
            if (lockId != null) {
                lockService.releaseLock("credit-limit-" + event.getTransactionId(), lockId);
            }
        }
    }

    private static class CreditLimitEvent {
        private UUID userId, transactionId;
        private BigDecimal attemptedAmount, creditLimit;
        public UUID getUserId() { return userId; }
        public UUID getTransactionId() { return transactionId; }
        public BigDecimal getAttemptedAmount() { return attemptedAmount; }
        public BigDecimal getCreditLimit() { return creditLimit; }
    }
}
