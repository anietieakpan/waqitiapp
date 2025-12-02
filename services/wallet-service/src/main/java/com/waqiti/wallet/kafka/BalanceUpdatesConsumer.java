package com.waqiti.wallet.kafka;

import com.waqiti.common.events.BalanceUpdateEvent;
import com.waqiti.common.kafka.dlq.UniversalDLQHandler;
import com.waqiti.common.idempotency.IdempotencyService;
import com.waqiti.wallet.domain.WalletBalance;
import com.waqiti.wallet.repository.WalletBalanceRepository;
import com.waqiti.wallet.service.BalanceService;
import com.waqiti.wallet.service.BalanceValidationService;
import com.waqiti.wallet.service.LimitCheckService;
import com.waqiti.wallet.metrics.WalletMetricsService;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;

import java.time.LocalDateTime;
import java.time.Instant;
import java.time.Duration;
import java.math.BigDecimal;
import java.util.*;

/**
 * CRITICAL FINANCIAL CONSUMER - Processes balance update events
 * WITH IDEMPOTENCY PROTECTION to prevent duplicate financial transactions
 *
 * Idempotency Strategy:
 * - Uses transactionId from event as idempotency key
 * - 7-day TTL for idempotency records
 * - Atomic check-and-process with distributed locking
 * - Exactly-once semantics for all balance modifications
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class BalanceUpdatesConsumer {

    private final WalletBalanceRepository balanceRepository;
    private final BalanceService balanceService;
    private final BalanceValidationService validationService;
    private final LimitCheckService limitCheckService;
    private final WalletMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final UniversalDLQHandler dlqHandler;
    private final IdempotencyService idempotencyService;
    
    private static final BigDecimal LOW_BALANCE_THRESHOLD = new BigDecimal("50.00");
    private static final BigDecimal ZERO_BALANCE = BigDecimal.ZERO;
    
    @KafkaListener(
        topics = {"balance-updates", "wallet-balance-changes", "account-balance-events"},
        groupId = "balance-updates-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "10"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 20000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handleBalanceUpdate(
            ConsumerRecord<String, BalanceUpdateEvent> record,
            @Payload BalanceUpdateEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            Acknowledgment acknowledgment) {

        String correlationId = String.format("balance-%s-p%d-o%d",
            event.getAccountId(), partition, offset);

        // CRITICAL: Idempotency key based on transactionId
        String idempotencyKey = "balance-update:" + event.getTransactionId();
        UUID operationId = UUID.randomUUID();
        Duration ttl = Duration.ofDays(7); // 7-day retention for balance update idempotency

        log.info("Processing balance update: accountId={}, amount={}, operation={}, transactionId={}, idempotencyKey={}",
            event.getAccountId(), event.getAmount(), event.getOperation(), event.getTransactionId(), idempotencyKey);

        // CRITICAL: Check for duplicate processing
        if (!idempotencyService.startOperation(idempotencyKey, operationId, ttl)) {
            log.warn("⚠️ DUPLICATE DETECTED - Balance update already processed: transactionId={}, accountId={}, operation={}",
                event.getTransactionId(), event.getAccountId(), event.getOperation());

            metricsService.recordDuplicateBalanceUpdate(event.getOperation());

            // Acknowledge the duplicate message to prevent infinite retries
            acknowledgment.acknowledge();
            return;
        }

        try {
            WalletBalance balance = balanceRepository.findByAccountId(event.getAccountId())
                .orElse(createNewBalance(event.getAccountId(), event.getCurrency()));
            
            BigDecimal previousBalance = balance.getAvailableBalance();
            
            switch (event.getOperation()) {
                case "CREDIT":
                    creditBalance(balance, event, correlationId);
                    break;
                    
                case "DEBIT":
                    debitBalance(balance, event, correlationId);
                    break;
                    
                case "HOLD":
                    holdBalance(balance, event, correlationId);
                    break;
                    
                case "RELEASE_HOLD":
                    releaseHold(balance, event, correlationId);
                    break;
                    
                case "CAPTURE_HOLD":
                    captureHold(balance, event, correlationId);
                    break;
                    
                case "ADJUSTMENT":
                    adjustBalance(balance, event, correlationId);
                    break;
                    
                default:
                    log.warn("Unknown balance operation: {}", event.getOperation());
                    return;
            }
            
            balance.setLastUpdatedAt(LocalDateTime.now());
            balanceRepository.save(balance);
            
            validateBalanceIntegrity(balance, event, correlationId);
            checkBalanceThresholds(balance, previousBalance, correlationId);
            
            metricsService.recordBalanceUpdate(event.getOperation(), event.getAmount());
            
            auditService.logWalletEvent("BALANCE_UPDATED", event.getAccountId(),
                Map.of("operation", event.getOperation(), "amount", event.getAmount(),
                    "previousBalance", previousBalance, "newBalance", balance.getAvailableBalance(),
                    "transactionId", event.getTransactionId(), "correlationId", correlationId,
                    "idempotencyKey", idempotencyKey, "operationId", operationId,
                    "timestamp", Instant.now()));

            // CRITICAL: Mark idempotent operation as completed BEFORE acknowledging
            Map<String, Object> result = Map.of(
                "accountId", event.getAccountId(),
                "operation", event.getOperation(),
                "amount", event.getAmount(),
                "previousBalance", previousBalance,
                "newBalance", balance.getAvailableBalance(),
                "processedAt", Instant.now()
            );

            idempotencyService.completeOperation(idempotencyKey, operationId, result, ttl);

            log.info("✅ Balance update completed successfully: transactionId={}, accountId={}, operation={}, newBalance={}",
                event.getTransactionId(), event.getAccountId(), event.getOperation(), balance.getAvailableBalance());

            // Acknowledge AFTER successful processing and idempotency marking
            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("❌ Error processing balance update: transactionId={}, accountId={}, operation={}, error={}",
                    event.getTransactionId(), event.getAccountId(), event.getOperation(), e.getMessage(), e);

            // CRITICAL: Mark idempotent operation as failed
            idempotencyService.failOperation(idempotencyKey, operationId,
                e.getClass().getSimpleName() + ": " + e.getMessage());

            // Send to DLQ for manual review
            dlqHandler.handleFailedMessage(record, e)
                .thenAccept(dlqResult -> log.info("Balance update sent to DLQ: transactionId={}, destination={}, category={}",
                        event.getTransactionId(), dlqResult.getDestinationTopic(), dlqResult.getFailureCategory()))
                .exceptionally(dlqError -> {
                    log.error("🚨 CRITICAL: DLQ handling failed for balance update - MESSAGE MAY BE LOST! " +
                            "transactionId={}, accountId={}, error={}",
                            event.getTransactionId(), event.getAccountId(), dlqError.getMessage(), dlqError);
                    return null;
                });

            throw new RuntimeException("Balance update processing failed: " + event.getTransactionId(), e);
        }
    }
    
    private void creditBalance(WalletBalance balance, BalanceUpdateEvent event, String correlationId) {
        BigDecimal newBalance = balance.getAvailableBalance().add(event.getAmount());
        balance.setAvailableBalance(newBalance);
        balance.setTotalBalance(balance.getTotalBalance().add(event.getAmount()));
        
        kafkaTemplate.send("balance-credited-events", Map.of(
            "accountId", event.getAccountId(),
            "amount", event.getAmount(),
            "newBalance", newBalance,
            "transactionId", event.getTransactionId(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
        
        log.info("Balance credited: accountId={}, amount={}, newBalance={}", 
            event.getAccountId(), event.getAmount(), newBalance);
    }
    
    private void debitBalance(WalletBalance balance, BalanceUpdateEvent event, String correlationId) {
        if (balance.getAvailableBalance().compareTo(event.getAmount()) < 0) {
            throw new RuntimeException("Insufficient funds for debit operation");
        }
        
        BigDecimal newBalance = balance.getAvailableBalance().subtract(event.getAmount());
        balance.setAvailableBalance(newBalance);
        balance.setTotalBalance(balance.getTotalBalance().subtract(event.getAmount()));
        
        kafkaTemplate.send("balance-debited-events", Map.of(
            "accountId", event.getAccountId(),
            "amount", event.getAmount(),
            "newBalance", newBalance,
            "transactionId", event.getTransactionId(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
        
        log.info("Balance debited: accountId={}, amount={}, newBalance={}", 
            event.getAccountId(), event.getAmount(), newBalance);
    }
    
    private void holdBalance(WalletBalance balance, BalanceUpdateEvent event, String correlationId) {
        if (balance.getAvailableBalance().compareTo(event.getAmount()) < 0) {
            throw new RuntimeException("Insufficient available funds for hold");
        }
        
        BigDecimal newAvailable = balance.getAvailableBalance().subtract(event.getAmount());
        BigDecimal newHeld = balance.getHeldBalance().add(event.getAmount());
        
        balance.setAvailableBalance(newAvailable);
        balance.setHeldBalance(newHeld);
        
        balanceService.recordHold(
            event.getAccountId(),
            event.getTransactionId(),
            event.getAmount(),
            event.getHoldDuration()
        );
        
        log.info("Balance held: accountId={}, amount={}, newAvailable={}, newHeld={}", 
            event.getAccountId(), event.getAmount(), newAvailable, newHeld);
    }
    
    private void releaseHold(WalletBalance balance, BalanceUpdateEvent event, String correlationId) {
        BigDecimal heldAmount = balanceService.getHoldAmount(
            event.getAccountId(),
            event.getTransactionId()
        );
        
        BigDecimal newAvailable = balance.getAvailableBalance().add(heldAmount);
        BigDecimal newHeld = balance.getHeldBalance().subtract(heldAmount);
        
        balance.setAvailableBalance(newAvailable);
        balance.setHeldBalance(newHeld);
        
        balanceService.releaseHold(event.getAccountId(), event.getTransactionId());
        
        log.info("Hold released: accountId={}, amount={}, newAvailable={}, newHeld={}", 
            event.getAccountId(), heldAmount, newAvailable, newHeld);
    }
    
    private void captureHold(WalletBalance balance, BalanceUpdateEvent event, String correlationId) {
        BigDecimal heldAmount = balanceService.getHoldAmount(
            event.getAccountId(),
            event.getTransactionId()
        );
        
        BigDecimal captureAmount = event.getAmount().min(heldAmount);
        BigDecimal releaseAmount = heldAmount.subtract(captureAmount);
        
        balance.setHeldBalance(balance.getHeldBalance().subtract(heldAmount));
        balance.setTotalBalance(balance.getTotalBalance().subtract(captureAmount));
        
        if (releaseAmount.compareTo(ZERO_BALANCE) > 0) {
            balance.setAvailableBalance(balance.getAvailableBalance().add(releaseAmount));
        }
        
        balanceService.captureHold(event.getAccountId(), event.getTransactionId(), captureAmount);
        
        log.info("Hold captured: accountId={}, captureAmount={}, releaseAmount={}", 
            event.getAccountId(), captureAmount, releaseAmount);
    }
    
    private void adjustBalance(WalletBalance balance, BalanceUpdateEvent event, String correlationId) {
        BigDecimal oldBalance = balance.getAvailableBalance();
        
        if (event.getAmount().compareTo(ZERO_BALANCE) > 0) {
            balance.setAvailableBalance(oldBalance.add(event.getAmount()));
            balance.setTotalBalance(balance.getTotalBalance().add(event.getAmount()));
        } else {
            BigDecimal adjustmentAmount = event.getAmount().abs();
            if (oldBalance.compareTo(adjustmentAmount) < 0) {
                throw new RuntimeException("Insufficient funds for balance adjustment");
            }
            balance.setAvailableBalance(oldBalance.subtract(adjustmentAmount));
            balance.setTotalBalance(balance.getTotalBalance().subtract(adjustmentAmount));
        }
        
        auditService.logWalletEvent("BALANCE_ADJUSTED", event.getAccountId(),
            Map.of("adjustmentAmount", event.getAmount(), "reason", event.getAdjustmentReason(),
                "oldBalance", oldBalance, "newBalance", balance.getAvailableBalance(),
                "approvedBy", event.getApprovedBy(), "correlationId", correlationId));
        
        log.warn("Balance adjusted: accountId={}, amount={}, reason={}, approvedBy={}", 
            event.getAccountId(), event.getAmount(), event.getAdjustmentReason(), event.getApprovedBy());
    }
    
    private void validateBalanceIntegrity(WalletBalance balance, BalanceUpdateEvent event, String correlationId) {
        if (balance.getAvailableBalance().compareTo(ZERO_BALANCE) < 0) {
            kafkaTemplate.send("balance-integrity-alerts", Map.of(
                "accountId", event.getAccountId(),
                "alertType", "NEGATIVE_BALANCE",
                "balance", balance.getAvailableBalance(),
                "transactionId", event.getTransactionId(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
            
            log.error("CRITICAL: Negative balance detected: accountId={}, balance={}", 
                event.getAccountId(), balance.getAvailableBalance());
        }
        
        BigDecimal calculatedTotal = balance.getAvailableBalance().add(balance.getHeldBalance());
        if (calculatedTotal.compareTo(balance.getTotalBalance()) != 0) {
            kafkaTemplate.send("balance-integrity-alerts", Map.of(
                "accountId", event.getAccountId(),
                "alertType", "BALANCE_MISMATCH",
                "expected", balance.getTotalBalance(),
                "calculated", calculatedTotal,
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
            
            log.error("Balance integrity violation: accountId={}, expected={}, calculated={}", 
                event.getAccountId(), balance.getTotalBalance(), calculatedTotal);
        }
    }
    
    private void checkBalanceThresholds(WalletBalance balance, BigDecimal previousBalance, String correlationId) {
        if (balance.getAvailableBalance().compareTo(LOW_BALANCE_THRESHOLD) < 0 &&
            previousBalance.compareTo(LOW_BALANCE_THRESHOLD) >= 0) {
            
            notificationService.sendNotification(balance.getAccountId(), "Low Balance Alert",
                String.format("Your wallet balance is low: %s %s. Consider adding funds.", 
                    balance.getAvailableBalance(), balance.getCurrency()),
                correlationId);
            
            metricsService.recordLowBalanceAlert(balance.getCurrency());
        }
        
        if (balance.getAvailableBalance().compareTo(ZERO_BALANCE) == 0 &&
            previousBalance.compareTo(ZERO_BALANCE) > 0) {
            
            notificationService.sendNotification(balance.getAccountId(), "Zero Balance Alert",
                "Your wallet balance has reached zero. Add funds to continue using your account.",
                correlationId);
            
            metricsService.recordZeroBalanceAlert(balance.getCurrency());
        }
    }
    
    private WalletBalance createNewBalance(String accountId, String currency) {
        return WalletBalance.builder()
            .accountId(accountId)
            .currency(currency)
            .availableBalance(ZERO_BALANCE)
            .heldBalance(ZERO_BALANCE)
            .totalBalance(ZERO_BALANCE)
            .createdAt(LocalDateTime.now())
            .lastUpdatedAt(LocalDateTime.now())
            .build();
    }
}