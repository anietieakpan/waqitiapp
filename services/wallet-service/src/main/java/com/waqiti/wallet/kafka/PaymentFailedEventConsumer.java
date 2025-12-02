package com.waqiti.wallet.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.eventsourcing.PaymentFailedEvent;
import com.waqiti.common.exception.BusinessException;
import com.waqiti.common.idempotency.IdempotencyService;
import com.waqiti.common.kafka.dlq.UniversalDLQHandler;
import com.waqiti.common.locking.DistributedLockService;
import com.waqiti.common.metrics.MetricsCollector;
import com.waqiti.common.notification.NotificationService;
import com.waqiti.wallet.domain.*;
import com.waqiti.wallet.repository.WalletRepository;
import com.waqiti.wallet.repository.FundRecoveryRepository;
import com.waqiti.wallet.service.WalletTransactionService;
import com.waqiti.wallet.service.WalletAuditService;
import com.waqiti.wallet.service.WalletNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * PRODUCTION-GRADE PaymentFailedEvent Consumer
 * 
 * Handles customer fund recovery when payments fail
 * Prevents permanent fund lockup and ensures customer satisfaction
 * 
 * CRITICAL BUSINESS FUNCTION:
 * - Processes payment failure events with exactly-once semantics
 * - Implements idempotent fund release operations
 * - Creates audit trails for regulatory compliance  
 * - Provides customer notifications for transparency
 * - Manages complex failure scenarios with manual escalation
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentFailedEventConsumer {

    private final WalletRepository walletRepository;
    private final FundRecoveryRepository fundRecoveryRepository;
    private final WalletTransactionService transactionService;
    private final WalletAuditService auditService;
    private final WalletNotificationService notificationService;
    private final IdempotencyService idempotencyService;
    private final DistributedLockService lockService;
    private final MetricsCollector metricsCollector;
    private final ObjectMapper objectMapper;
    private final UniversalDLQHandler dlqHandler;

    private static final String CONSUMER_GROUP = "wallet-payment-failed-recovery";
    private static final String LOCK_PREFIX = "payment-failed-";
    private static final Duration LOCK_TIMEOUT = Duration.ofMinutes(5);

    /**
     * Primary consumer for payment failure events
     * Implements comprehensive fund recovery with idempotency
     */
    @KafkaListener(
        topics = "payment-failed-events",
        groupId = CONSUMER_GROUP,
        containerFactory = "kafkaListenerContainerFactory",
        concurrency = "3"
    )
    @Retryable(
        value = {Exception.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public void handlePaymentFailure(
            @Payload PaymentFailedEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String lockKey = LOCK_PREFIX + event.getPaymentId();
        String idempotencyKey = generateIdempotencyKey(event);
        
        log.info("Processing payment failure event: paymentId={}, walletId={}, amount={}, reason={}", 
                event.getPaymentId(), event.getSenderWalletId(), event.getAmount(), event.getFailureReason());
        
        // Metrics tracking
        metricsCollector.incrementCounter("wallet.payment_failed_events.received");
        var timer = metricsCollector.startTimer("wallet.payment_failed_processing_time");
        
        try {
            // Check idempotency first to prevent duplicate processing
            if (idempotencyService.isAlreadyProcessed(idempotencyKey)) {
                log.info("Payment failure already processed: paymentId={}, idempotencyKey={}", 
                        event.getPaymentId(), idempotencyKey);
                metricsCollector.incrementCounter("wallet.payment_failed_events.duplicate");
                acknowledgment.acknowledge();
                return;
            }
            
            // Acquire distributed lock to prevent concurrent processing
            boolean lockAcquired = lockService.tryLock(lockKey, LOCK_TIMEOUT);
            if (!lockAcquired) {
                log.warn("Unable to acquire lock for payment failure processing: paymentId={}", 
                        event.getPaymentId());
                throw new BusinessException("Lock acquisition failed - will retry");
            }
            
            try {
                // Process the payment failure with full recovery logic
                FundRecoveryResult result = processPaymentFailureRecovery(event);
                
                // Mark as processed to ensure idempotency
                idempotencyService.markAsProcessed(idempotencyKey, result);
                
                // Update metrics based on result
                if (result.isSuccessful()) {
                    metricsCollector.incrementCounter("wallet.payment_failed_events.processed.success");
                    metricsCollector.recordGauge("wallet.funds_recovered.amount", result.getAmountRecovered().doubleValue());
                    
                    log.info("Payment failure processed successfully: paymentId={}, amountRecovered={}, walletId={}", 
                            event.getPaymentId(), result.getAmountRecovered(), event.getSenderWalletId());
                } else {
                    metricsCollector.incrementCounter("wallet.payment_failed_events.processed.failed");
                    log.error("Payment failure processing failed: paymentId={}, error={}", 
                            event.getPaymentId(), result.getErrorMessage());
                }
                
                // Acknowledge successful processing
                acknowledgment.acknowledge();
                
            } finally {
                // Always release the lock
                lockService.unlock(lockKey);
            }
            
        } catch (Exception e) {
            log.error("Failed to process payment failure event: paymentId={}, partition={}, offset={}, error={}",
                    event.getPaymentId(), partition, offset, e.getMessage(), e);

            metricsCollector.incrementCounter("wallet.payment_failed_events.processing_errors");

            dlqHandler.handleFailedMessage(event, "payment-failed-events", partition, offset, e)
                .thenAccept(result -> log.info("Payment failed event sent to DLQ: paymentId={}, destination={}, category={}",
                        event.getPaymentId(), result.getDestinationTopic(), result.getFailureCategory()))
                .exceptionally(dlqError -> {
                    log.error("CRITICAL: DLQ handling failed for payment failed event - MESSAGE MAY BE LOST! " +
                            "paymentId={}, partition={}, offset={}, error={}",
                            event.getPaymentId(), partition, offset, dlqError.getMessage(), dlqError);
                    return null;
                });

            // Create fund recovery record for manual intervention
            createManualInterventionRecord(event, e);

            // Don't acknowledge - let retry mechanism handle it
            throw new RuntimeException("Payment failed event processing failed", e);

        } finally {
            timer.stop();
        }
    }

    /**
     * Comprehensive payment failure recovery logic
     * Handles all failure scenarios with proper fund restoration
     */
    @Transactional
    private FundRecoveryResult processPaymentFailureRecovery(PaymentFailedEvent event) {
        log.info("Starting fund recovery process: paymentId={}, failureCode={}", 
                event.getPaymentId(), event.getFailureCode());
        
        try {
            // 1. Determine recovery strategy based on failure type
            FundRecoveryStrategy strategy = determineRecoveryStrategy(event);
            log.debug("Selected recovery strategy: {} for paymentId={}", strategy, event.getPaymentId());
            
            // 2. Execute recovery based on strategy
            FundRecoveryResult result = switch (strategy) {
                case RELEASE_HOLD -> releasePaymentHold(event);
                case REVERSE_DEBIT -> reverseWalletDebit(event);
                case RESTORE_BALANCE -> restoreOriginalBalance(event);
                case COMPENSATE_FEES -> compensateChargedFees(event);
                case MANUAL_REVIEW -> scheduleManualReview(event);
            };
            
            // 3. Create audit trail for compliance
            auditService.logFundRecovery(event.getPaymentId(), event.getSenderWalletId(), 
                    strategy, result, "Automated recovery from payment failure");
            
            // 4. Send customer notification
            notifyCustomerOfRecovery(event, result);
            
            // 5. Update wallet metrics
            updateWalletMetrics(event.getSenderWalletId(), result);
            
            log.info("Fund recovery completed successfully: paymentId={}, strategy={}, amountRecovered={}", 
                    event.getPaymentId(), strategy, result.getAmountRecovered());
            
            return result;
            
        } catch (Exception e) {
            log.error("Fund recovery failed: paymentId={}, error={}", event.getPaymentId(), e.getMessage(), e);
            
            // Create failure record for escalation
            FundRecoveryResult failedResult = FundRecoveryResult.builder()
                    .paymentId(event.getPaymentId())
                    .walletId(event.getSenderWalletId())
                    .successful(false)
                    .amountRecovered(BigDecimal.ZERO)
                    .errorMessage(e.getMessage())
                    .requiresManualIntervention(true)
                    .recoveryStrategy(FundRecoveryStrategy.MANUAL_REVIEW)
                    .processedAt(LocalDateTime.now())
                    .build();
            
            // Always create manual intervention record on failure
            createManualInterventionRecord(event, e);
            
            return failedResult;
        }
    }

    /**
     * Determine the appropriate recovery strategy based on failure type
     */
    private FundRecoveryStrategy determineRecoveryStrategy(PaymentFailedEvent event) {
        String failureCode = event.getFailureCode();
        
        return switch (failureCode) {
            case "INSUFFICIENT_FUNDS", "DECLINED_BY_ISSUER" -> FundRecoveryStrategy.RELEASE_HOLD;
            case "NETWORK_ERROR", "TIMEOUT", "SERVICE_UNAVAILABLE" -> FundRecoveryStrategy.REVERSE_DEBIT;
            case "INVALID_ACCOUNT", "CLOSED_ACCOUNT" -> FundRecoveryStrategy.RESTORE_BALANCE;
            case "DUPLICATE_TRANSACTION", "ALREADY_PROCESSED" -> FundRecoveryStrategy.COMPENSATE_FEES;
            case "FRAUD_DETECTED", "COMPLIANCE_VIOLATION" -> FundRecoveryStrategy.MANUAL_REVIEW;
            default -> {
                log.warn("Unknown failure code '{}' for paymentId={}, defaulting to manual review", 
                        failureCode, event.getPaymentId());
                yield FundRecoveryStrategy.MANUAL_REVIEW;
            }
        };
    }

    /**
     * Release payment hold - funds were held but payment failed
     */
    private FundRecoveryResult releasePaymentHold(PaymentFailedEvent event) {
        log.info("Releasing payment hold: paymentId={}, walletId={}, amount={}", 
                event.getPaymentId(), event.getSenderWalletId(), event.getAmount());
        
        try {
            UUID walletId = UUID.fromString(event.getSenderWalletId());
            
            // Find and release the hold
            boolean holdReleased = transactionService.releasePaymentHold(
                    walletId, event.getPaymentId(), event.getAmount(), 
                    "Payment failed: " + event.getFailureReason());
            
            if (holdReleased) {
                return FundRecoveryResult.builder()
                        .paymentId(event.getPaymentId())
                        .walletId(event.getSenderWalletId())
                        .successful(true)
                        .amountRecovered(event.getAmount())
                        .recoveryStrategy(FundRecoveryStrategy.RELEASE_HOLD)
                        .processedAt(LocalDateTime.now())
                        .build();
            } else {
                throw new BusinessException("Unable to release payment hold - hold not found or already released");
            }
            
        } catch (Exception e) {
            log.error("Failed to release payment hold: paymentId={}, error={}", event.getPaymentId(), e.getMessage());
            throw e;
        }
    }

    /**
     * Reverse wallet debit - funds were already debited but payment failed
     */
    private FundRecoveryResult reverseWalletDebit(PaymentFailedEvent event) {
        log.info("Reversing wallet debit: paymentId={}, walletId={}, amount={}", 
                event.getPaymentId(), event.getSenderWalletId(), event.getAmount());
        
        try {
            UUID walletId = UUID.fromString(event.getSenderWalletId());
            
            // Create reversal transaction
            WalletTransaction reversalTxn = transactionService.createReversalTransaction(
                    walletId, 
                    event.getAmount(), 
                    event.getCurrency(),
                    event.getPaymentId(),
                    "Payment reversal due to failure: " + event.getFailureReason()
            );
            
            return FundRecoveryResult.builder()
                    .paymentId(event.getPaymentId())
                    .walletId(event.getSenderWalletId())
                    .successful(true)
                    .amountRecovered(event.getAmount())
                    .recoveryStrategy(FundRecoveryStrategy.REVERSE_DEBIT)
                    .reversalTransactionId(reversalTxn.getId().toString())
                    .processedAt(LocalDateTime.now())
                    .build();
            
        } catch (Exception e) {
            log.error("Failed to reverse wallet debit: paymentId={}, error={}", event.getPaymentId(), e.getMessage());
            throw e;
        }
    }

    /**
     * Restore original balance - complex failure requiring balance restoration
     */
    private FundRecoveryResult restoreOriginalBalance(PaymentFailedEvent event) {
        log.info("Restoring original balance: paymentId={}, walletId={}, amount={}", 
                event.getPaymentId(), event.getSenderWalletId(), event.getAmount());
        
        try {
            UUID walletId = UUID.fromString(event.getSenderWalletId());
            
            // Get wallet and restore balance
            Wallet wallet = walletRepository.findById(walletId)
                    .orElseThrow(() -> new BusinessException("Wallet not found: " + walletId));
            
            // Create balance restoration entry
            BigDecimal restoredAmount = transactionService.restoreBalance(
                    walletId, 
                    event.getAmount(),
                    event.getCurrency(),
                    "Balance restoration - Payment failed: " + event.getFailureReason()
            );
            
            return FundRecoveryResult.builder()
                    .paymentId(event.getPaymentId())
                    .walletId(event.getSenderWalletId())
                    .successful(true)
                    .amountRecovered(restoredAmount)
                    .recoveryStrategy(FundRecoveryStrategy.RESTORE_BALANCE)
                    .processedAt(LocalDateTime.now())
                    .build();
            
        } catch (Exception e) {
            log.error("Failed to restore original balance: paymentId={}, error={}", event.getPaymentId(), e.getMessage());
            throw e;
        }
    }

    /**
     * Compensate charged fees - refund fees that were charged for failed payment
     */
    private FundRecoveryResult compensateChargedFees(PaymentFailedEvent event) {
        log.info("Compensating charged fees: paymentId={}, walletId={}", 
                event.getPaymentId(), event.getSenderWalletId());
        
        try {
            UUID walletId = UUID.fromString(event.getSenderWalletId());
            
            // Calculate fees to refund (from event metadata or query fee service)
            BigDecimal feesToRefund = event.getFeesCharged() != null ? 
                    event.getFeesCharged() : BigDecimal.ZERO;
            
            if (feesToRefund.compareTo(BigDecimal.ZERO) > 0) {
                // Create fee compensation transaction
                transactionService.createFeeCompensation(
                        walletId,
                        feesToRefund,
                        event.getCurrency(),
                        event.getPaymentId(),
                        "Fee compensation for failed payment"
                );
                
                return FundRecoveryResult.builder()
                        .paymentId(event.getPaymentId())
                        .walletId(event.getSenderWalletId())
                        .successful(true)
                        .amountRecovered(feesToRefund)
                        .recoveryStrategy(FundRecoveryStrategy.COMPENSATE_FEES)
                        .processedAt(LocalDateTime.now())
                        .build();
            } else {
                log.info("No fees to compensate for paymentId={}", event.getPaymentId());
                return FundRecoveryResult.builder()
                        .paymentId(event.getPaymentId())
                        .walletId(event.getSenderWalletId())
                        .successful(true)
                        .amountRecovered(BigDecimal.ZERO)
                        .recoveryStrategy(FundRecoveryStrategy.COMPENSATE_FEES)
                        .processedAt(LocalDateTime.now())
                        .build();
            }
            
        } catch (Exception e) {
            log.error("Failed to compensate charged fees: paymentId={}, error={}", event.getPaymentId(), e.getMessage());
            throw e;
        }
    }

    /**
     * Schedule manual review for complex cases
     */
    private FundRecoveryResult scheduleManualReview(PaymentFailedEvent event) {
        log.info("Scheduling manual review: paymentId={}, walletId={}, reason={}", 
                event.getPaymentId(), event.getSenderWalletId(), event.getFailureReason());
        
        // Create manual intervention record
        createManualInterventionRecord(event, null);
        
        return FundRecoveryResult.builder()
                .paymentId(event.getPaymentId())
                .walletId(event.getSenderWalletId())
                .successful(false)
                .amountRecovered(BigDecimal.ZERO)
                .recoveryStrategy(FundRecoveryStrategy.MANUAL_REVIEW)
                .requiresManualIntervention(true)
                .processedAt(LocalDateTime.now())
                .build();
    }

    /**
     * Create manual intervention record for operations team
     */
    private void createManualInterventionRecord(PaymentFailedEvent event, Exception error) {
        try {
            FundRecoveryRecord record = FundRecoveryRecord.builder()
                    .id(UUID.randomUUID())
                    .paymentId(event.getPaymentId())
                    .walletId(event.getSenderWalletId())
                    .userId(event.getUserId())
                    .amount(event.getAmount())
                    .currency(event.getCurrency())
                    .failureReason(event.getFailureReason())
                    .failureCode(event.getFailureCode())
                    .errorDetails(error != null ? error.getMessage() : null)
                    .status(RecoveryStatus.PENDING_MANUAL_REVIEW)
                    .priority(determinePriority(event))
                    .createdAt(LocalDateTime.now())
                    .build();
            
            fundRecoveryRepository.save(record);
            
            // Send alert to operations team
            notificationService.sendOperationalAlert(
                    "FUND_RECOVERY_MANUAL_INTERVENTION",
                    String.format("Manual intervention required for payment failure: %s", event.getPaymentId()),
                    record
            );
            
            log.info("Manual intervention record created: paymentId={}, recordId={}", 
                    event.getPaymentId(), record.getId());
            
        } catch (Exception e) {
            log.error("Failed to create manual intervention record: paymentId={}, error={}", 
                    event.getPaymentId(), e.getMessage(), e);
        }
    }

    /**
     * Send customer notification about fund recovery
     */
    private void notifyCustomerOfRecovery(PaymentFailedEvent event, FundRecoveryResult result) {
        try {
            if (result.isSuccessful()) {
                notificationService.sendFundRecoveryNotification(
                        event.getUserId(),
                        event.getPaymentId(),
                        result.getAmountRecovered(),
                        event.getCurrency(),
                        "Your funds have been automatically recovered after the payment failure."
                );
            } else {
                notificationService.sendFundRecoveryPendingNotification(
                        event.getUserId(),
                        event.getPaymentId(),
                        event.getAmount(),
                        event.getCurrency(),
                        "We're working to recover your funds. You'll be notified once complete."
                );
            }
        } catch (Exception e) {
            log.error("Failed to send customer notification: paymentId={}, error={}", 
                    event.getPaymentId(), e.getMessage());
        }
    }

    /**
     * Update wallet metrics after fund recovery
     */
    private void updateWalletMetrics(String walletId, FundRecoveryResult result) {
        try {
            if (result.isSuccessful()) {
                metricsCollector.recordGauge("wallet.recovery.amount", result.getAmountRecovered().doubleValue());
                metricsCollector.incrementCounter("wallet.recovery.successful");
            } else {
                metricsCollector.incrementCounter("wallet.recovery.failed");
            }
        } catch (Exception e) {
            log.error("Failed to update wallet metrics: walletId={}, error={}", walletId, e.getMessage());
        }
    }

    /**
     * Generate unique idempotency key for event processing
     */
    private String generateIdempotencyKey(PaymentFailedEvent event) {
        return String.format("payment-failed-%s-%s", 
                event.getPaymentId(), event.getTimestamp().toString());
    }

    /**
     * Determine priority for manual intervention
     */
    private RecoveryPriority determinePriority(PaymentFailedEvent event) {
        BigDecimal amount = event.getAmount();
        
        if (amount.compareTo(BigDecimal.valueOf(10000)) >= 0) {
            return RecoveryPriority.CRITICAL;
        } else if (amount.compareTo(BigDecimal.valueOf(1000)) >= 0) {
            return RecoveryPriority.HIGH;
        } else if (amount.compareTo(BigDecimal.valueOf(100)) >= 0) {
            return RecoveryPriority.MEDIUM;
        } else {
            return RecoveryPriority.LOW;
        }
    }

    // Data classes and enums
    
    public enum FundRecoveryStrategy {
        RELEASE_HOLD,
        REVERSE_DEBIT,
        RESTORE_BALANCE,
        COMPENSATE_FEES,
        MANUAL_REVIEW
    }

    public enum RecoveryStatus {
        PENDING_MANUAL_REVIEW,
        IN_PROGRESS,
        COMPLETED,
        FAILED
    }

    public enum RecoveryPriority {
        CRITICAL,
        HIGH,
        MEDIUM,
        LOW
    }

    @lombok.Builder
    @lombok.Data
    public static class FundRecoveryResult {
        private String paymentId;
        private String walletId;
        private boolean successful;
        private BigDecimal amountRecovered;
        private String errorMessage;
        private boolean requiresManualIntervention;
        private FundRecoveryStrategy recoveryStrategy;
        private String reversalTransactionId;
        private LocalDateTime processedAt;
    }
}