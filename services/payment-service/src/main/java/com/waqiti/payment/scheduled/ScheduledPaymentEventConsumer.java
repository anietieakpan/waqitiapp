package com.waqiti.payment.scheduled;

import com.waqiti.common.audit.AuditService;
import com.waqiti.common.events.ScheduledPaymentEvent;
import com.waqiti.common.kafka.KafkaDlqHandler;
import com.waqiti.payment.domain.PaymentStatus;
import com.waqiti.payment.domain.ScheduledPayment;
import com.waqiti.payment.service.PaymentService;
import com.waqiti.payment.service.ScheduledPaymentService;
import com.waqiti.wallet.client.WalletServiceClient;
import io.micrometer.core.annotation.Counted;
import io.micrometer.core.annotation.Timed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

/**
 * CRITICAL: Processes scheduled payment events to prevent recurring payment failures
 * IMPACT: Prevents subscription service disruptions and customer dissatisfaction
 * COMPLIANCE: Required for PCI DSS recurring payment processing
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ScheduledPaymentEventConsumer {

    private final ScheduledPaymentService scheduledPaymentService;
    private final PaymentService paymentService;
    private final WalletServiceClient walletServiceClient;
    private final AuditService auditService;
    private final KafkaDlqHandler kafkaDlqHandler;

    @KafkaListener(
        topics = "scheduled-payments",
        groupId = "payment-service-scheduled",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Timed(name = "scheduled.payment.processing.time", description = "Time taken to process scheduled payment")
    @Counted(name = "scheduled.payment.processed", description = "Number of scheduled payments processed")
    @Transactional(rollbackFor = Exception.class)
    @Retryable(
        value = {Exception.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 5000, multiplier = 2, maxDelay = 30000)
    )
    public void processScheduledPayment(
            @Payload ScheduledPaymentEvent event,
            @Header(KafkaHeaders.RECEIVED_MESSAGE_KEY) String messageKey,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp,
            Acknowledgment acknowledgment) {

        String correlationId = UUID.randomUUID().toString();
        
        log.info("Processing scheduled payment event - ScheduledPaymentId: {}, Amount: {}, Currency: {}, CorrelationId: {}",
                event.getScheduledPaymentId(), event.getAmount(), event.getCurrency(), correlationId);

        try {
            // CRITICAL: Idempotency check to prevent duplicate payments
            if (scheduledPaymentService.isPaymentExecuted(event.getScheduledPaymentId(), event.getExecutionTime())) {
                log.info("Scheduled payment already executed - ScheduledPaymentId: {}, ExecutionTime: {}",
                        event.getScheduledPaymentId(), event.getExecutionTime());
                acknowledgment.acknowledge();
                return;
            }

            // AUDIT: Record scheduled payment attempt
            auditService.logScheduledPaymentAttempt(event.getScheduledPaymentId(), event.getAmount(),
                    event.getCurrency(), correlationId, LocalDateTime.now());

            // VALIDATION: Get scheduled payment details
            ScheduledPayment scheduledPayment = scheduledPaymentService
                    .findById(event.getScheduledPaymentId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Scheduled payment not found: " + event.getScheduledPaymentId()));

            if (!scheduledPayment.isActive()) {
                log.info("Scheduled payment is inactive - ScheduledPaymentId: {}", event.getScheduledPaymentId());
                acknowledgment.acknowledge();
                return;
            }

            // TIMEZONE: Handle timezone-specific execution
            LocalDateTime localExecutionTime = event.getExecutionTime()
                    .atZone(ZoneId.systemDefault())
                    .withZoneSameInstant(ZoneId.of(scheduledPayment.getTimezone()))
                    .toLocalDateTime();

            // BALANCE CHECK: Verify sufficient funds before processing
            var payerWallet = walletServiceClient.getWalletByUserId(scheduledPayment.getPayerId());
            if (payerWallet == null || !payerWallet.isActive()) {
                log.error("Payer wallet not found or inactive - PayerId: {}", scheduledPayment.getPayerId());
                scheduledPaymentService.markExecutionFailed(event.getScheduledPaymentId(),
                        event.getExecutionTime(), "Payer wallet not available");
                acknowledgment.acknowledge();
                return;
            }

            BigDecimal availableBalance = walletServiceClient.getAvailableBalance(
                    scheduledPayment.getPayerId(), event.getCurrency());

            if (availableBalance.compareTo(event.getAmount()) < 0) {
                log.warn("Insufficient funds for scheduled payment - ScheduledPaymentId: {}, Required: {}, Available: {}",
                        event.getScheduledPaymentId(), event.getAmount(), availableBalance);

                // RETRY LOGIC: Schedule retry for insufficient funds
                if (scheduledPayment.getRetryCount() < scheduledPayment.getMaxRetries()) {
                    LocalDateTime nextRetry = localExecutionTime.plusHours(scheduledPayment.getRetryIntervalHours());
                    scheduledPaymentService.scheduleRetry(event.getScheduledPaymentId(), nextRetry,
                            "Insufficient funds - retry scheduled");

                    auditService.logScheduledPaymentRetry(event.getScheduledPaymentId(),
                            scheduledPayment.getRetryCount() + 1, nextRetry, "Insufficient funds",
                            correlationId, LocalDateTime.now());

                    log.info("Scheduled payment retry scheduled - ScheduledPaymentId: {}, NextRetry: {}",
                            event.getScheduledPaymentId(), nextRetry);
                } else {
                    // MAX RETRIES: Mark as failed and notify customer
                    scheduledPaymentService.markExecutionFailed(event.getScheduledPaymentId(),
                            event.getExecutionTime(), "Insufficient funds after maximum retries");

                    scheduledPaymentService.sendPaymentFailureNotification(scheduledPayment,
                            "Payment failed due to insufficient funds after " + scheduledPayment.getMaxRetries() + " retries");

                    auditService.logScheduledPaymentMaxRetriesExceeded(event.getScheduledPaymentId(),
                            "Insufficient funds", correlationId, LocalDateTime.now());

                    log.error("Scheduled payment failed after max retries - ScheduledPaymentId: {}",
                            event.getScheduledPaymentId());
                }

                acknowledgment.acknowledge();
                return;
            }

            // PAYEE VALIDATION: Verify payee wallet exists and is active
            var payeeWallet = walletServiceClient.getWalletByUserId(scheduledPayment.getPayeeId());
            if (payeeWallet == null || !payeeWallet.isActive()) {
                log.error("Payee wallet not found or inactive - PayeeId: {}", scheduledPayment.getPayeeId());
                scheduledPaymentService.markExecutionFailed(event.getScheduledPaymentId(),
                        event.getExecutionTime(), "Payee wallet not available");
                
                // NOTIFICATION: Notify both parties of the failure
                scheduledPaymentService.sendPaymentFailureNotification(scheduledPayment,
                        "Payment failed - recipient account not available");
                
                acknowledgment.acknowledge();
                return;
            }

            // FRAUD CHECK: Verify payment against fraud rules
            var fraudCheckResult = paymentService.performFraudCheck(
                    scheduledPayment.getPayerId(),
                    scheduledPayment.getPayeeId(),
                    event.getAmount(),
                    event.getCurrency(),
                    "SCHEDULED_PAYMENT"
            );

            if (fraudCheckResult.isBlocked()) {
                log.error("Scheduled payment blocked by fraud detection - ScheduledPaymentId: {}, Reason: {}",
                        event.getScheduledPaymentId(), fraudCheckResult.getReason());

                scheduledPaymentService.markExecutionBlocked(event.getScheduledPaymentId(),
                        event.getExecutionTime(), "Fraud detection: " + fraudCheckResult.getReason());

                // COMPLIANCE: Log fraud block for regulatory reporting
                auditService.logFraudBlock(event.getScheduledPaymentId(), scheduledPayment.getPayerId(),
                        fraudCheckResult.getReason(), correlationId, LocalDateTime.now());

                acknowledgment.acknowledge();
                return;
            }

            // EXECUTION: Process the scheduled payment
            log.info("Executing scheduled payment - ScheduledPaymentId: {}, Amount: {} {}",
                    event.getScheduledPaymentId(), event.getAmount(), event.getCurrency());

            var paymentResult = paymentService.processScheduledPayment(
                    scheduledPayment,
                    event.getAmount(),
                    event.getCurrency(),
                    localExecutionTime,
                    correlationId
            );

            if (paymentResult.isSuccess()) {
                // SUCCESS: Mark execution as completed
                scheduledPaymentService.markExecutionCompleted(
                        event.getScheduledPaymentId(),
                        event.getExecutionTime(),
                        paymentResult.getPaymentId(),
                        correlationId
                );

                // NEXT PAYMENT: Schedule next payment if recurring
                if (scheduledPayment.isRecurring()) {
                    LocalDateTime nextExecution = scheduledPaymentService.calculateNextExecution(
                            scheduledPayment, localExecutionTime);
                    
                    if (nextExecution.isBefore(scheduledPayment.getEndDate())) {
                        scheduledPaymentService.scheduleNextPayment(event.getScheduledPaymentId(), nextExecution);
                        
                        log.info("Next scheduled payment scheduled - ScheduledPaymentId: {}, NextExecution: {}",
                                event.getScheduledPaymentId(), nextExecution);
                    } else {
                        // COMPLETION: Recurring series completed
                        scheduledPaymentService.markSeriesCompleted(event.getScheduledPaymentId());
                        log.info("Recurring payment series completed - ScheduledPaymentId: {}",
                                event.getScheduledPaymentId());
                    }
                }

                // NOTIFICATION: Send success notification
                scheduledPaymentService.sendPaymentSuccessNotification(scheduledPayment, paymentResult.getPaymentId());

                // AUDIT: Log successful execution
                auditService.logScheduledPaymentSuccess(event.getScheduledPaymentId(),
                        paymentResult.getPaymentId(), event.getAmount(), event.getCurrency(),
                        correlationId, LocalDateTime.now());

                log.info("Scheduled payment executed successfully - ScheduledPaymentId: {}, PaymentId: {}",
                        event.getScheduledPaymentId(), paymentResult.getPaymentId());

            } else {
                // FAILURE: Handle payment execution failure
                log.error("Scheduled payment execution failed - ScheduledPaymentId: {}, Error: {}",
                        event.getScheduledPaymentId(), paymentResult.getErrorMessage());

                scheduledPaymentService.markExecutionFailed(event.getScheduledPaymentId(),
                        event.getExecutionTime(), paymentResult.getErrorMessage());

                // RETRY: Schedule retry if applicable
                if (scheduledPayment.getRetryCount() < scheduledPayment.getMaxRetries() &&
                    paymentResult.isRetryable()) {
                    
                    LocalDateTime nextRetry = localExecutionTime.plusHours(scheduledPayment.getRetryIntervalHours());
                    scheduledPaymentService.scheduleRetry(event.getScheduledPaymentId(), nextRetry,
                            paymentResult.getErrorMessage());

                    log.info("Scheduled payment retry due to failure - ScheduledPaymentId: {}, NextRetry: {}",
                            event.getScheduledPaymentId(), nextRetry);
                } else {
                    // FINAL FAILURE: Notify customer and mark as failed
                    scheduledPaymentService.sendPaymentFailureNotification(scheduledPayment,
                            "Payment failed: " + paymentResult.getErrorMessage());
                }

                // AUDIT: Log execution failure
                auditService.logScheduledPaymentFailure(event.getScheduledPaymentId(),
                        paymentResult.getErrorMessage(), correlationId, LocalDateTime.now());
            }

            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("Critical error processing scheduled payment - ScheduledPaymentId: {}, Error: {}",
                    event.getScheduledPaymentId(), e.getMessage(), e);

            // AUDIT: Log the error
            auditService.logScheduledPaymentError(event.getScheduledPaymentId(), correlationId,
                    e.getMessage(), LocalDateTime.now());

            // CRITICAL: Send to DLQ for manual review - recurring payments are critical
            kafkaDlqHandler.sendToDlq(topic, messageKey, event, e.getMessage(),
                    "CRITICAL: Manual intervention required for scheduled payment");

            acknowledgment.acknowledge(); // Acknowledge to prevent infinite retries

            // ALERT: Notify operations team
            scheduledPaymentService.sendCriticalAlert("SCHEDULED_PAYMENT_ERROR",
                    event.getScheduledPaymentId(), e.getMessage());
        }
    }

    /**
     * CRITICAL: Manual intervention for failed scheduled payments
     */
    public void manualScheduledPaymentIntervention(UUID scheduledPaymentId, String action, String operatorId) {
        log.warn("Manual scheduled payment intervention - ScheduledPaymentId: {}, Action: {}, OperatorId: {}",
                scheduledPaymentId, action, operatorId);

        auditService.logManualIntervention(scheduledPaymentId, null, operatorId,
                "MANUAL_SCHEDULED_PAYMENT", LocalDateTime.now());

        // Implementation for manual intervention (retry, cancel, modify)
    }
}