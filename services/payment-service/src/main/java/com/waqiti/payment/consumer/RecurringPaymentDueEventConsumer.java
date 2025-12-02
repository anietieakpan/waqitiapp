package com.waqiti.payment.consumer;

import com.waqiti.common.events.RecurringPaymentDueEvent;
import com.waqiti.payment.service.RecurringPaymentService;
import com.waqiti.payment.service.PaymentProcessingService;
import com.waqiti.payment.service.WalletService;
import com.waqiti.payment.service.NotificationService;
import com.waqiti.payment.repository.ProcessedEventRepository;
import com.waqiti.payment.repository.RecurringPaymentRepository;
import com.waqiti.payment.model.ProcessedEvent;
import com.waqiti.payment.model.RecurringPayment;
import com.waqiti.payment.model.PaymentStatus;
import com.waqiti.payment.model.RetryStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Consumer for RecurringPaymentDueEvent - Critical for subscription and recurring payments
 * Processes recurring payments with retry logic and failure handling
 * ZERO TOLERANCE: All recurring payments must be attempted on schedule
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class RecurringPaymentDueEventConsumer {
    
    private final RecurringPaymentService recurringPaymentService;
    private final PaymentProcessingService paymentProcessingService;
    private final WalletService walletService;
    private final NotificationService notificationService;
    private final ProcessedEventRepository processedEventRepository;
    private final RecurringPaymentRepository recurringPaymentRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final BigDecimal NOTIFICATION_THRESHOLD = new BigDecimal("50");
    
    @KafkaListener(
        topics = "payment.recurring.due",
        groupId = "payment-service",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional(isolation = Isolation.SERIALIZABLE) // Highest isolation for payment processing
    public void handleRecurringPaymentDue(RecurringPaymentDueEvent event) {
        log.info("Processing recurring payment: Subscription {} due for user {} - Amount: ${}", 
            event.getSubscriptionId(), event.getUserId(), event.getAmount());
        
        // IDEMPOTENCY CHECK - Prevent duplicate payment processing
        String idempotencyKey = generateIdempotencyKey(event);
        if (processedEventRepository.existsByIdempotencyKey(idempotencyKey)) {
            log.info("Recurring payment already processed for period: {}", event.getBillingPeriod());
            return;
        }
        
        try {
            // Get recurring payment configuration
            RecurringPayment recurringPayment = recurringPaymentRepository.findById(event.getSubscriptionId())
                .orElseThrow(() -> new RuntimeException("Recurring payment not found: " + event.getSubscriptionId()));
            
            // STEP 1: Validate recurring payment is still active
            validateRecurringPayment(recurringPayment, event);
            
            // STEP 2: Check wallet balance and available payment methods
            boolean hasSufficientFunds = checkPaymentFeasibility(event);
            
            // STEP 3: Send pre-payment notification if configured
            if (recurringPayment.isSendPreNotification()) {
                sendPrePaymentNotification(event);
            }
            
            // STEP 4: Process the recurring payment
            String paymentId = processRecurringPayment(recurringPayment, event, hasSufficientFunds);
            
            // STEP 5: Handle payment result
            if (paymentId != null) {
                handleSuccessfulPayment(recurringPayment, event, paymentId);
            } else {
                handleFailedPayment(recurringPayment, event);
            }
            
            // STEP 6: Update recurring payment statistics
            updateRecurringPaymentStats(recurringPayment, event, paymentId != null);
            
            // STEP 7: Schedule next payment if applicable
            if (recurringPayment.isActive() && !recurringPayment.isExpired()) {
                scheduleNextPayment(recurringPayment, event);
            }
            
            // STEP 8: Check and update subscription status
            updateSubscriptionStatus(recurringPayment, event);
            
            // STEP 9: Handle merchant settlement if successful
            if (paymentId != null && event.getMerchantId() != null) {
                initiateMerchantSettlement(event, paymentId);
            }
            
            // STEP 10: Record successful processing
            ProcessedEvent processedEvent = ProcessedEvent.builder()
                .eventId(event.getEventId())
                .eventType("RecurringPaymentDueEvent")
                .processedAt(Instant.now())
                .idempotencyKey(idempotencyKey)
                .subscriptionId(event.getSubscriptionId())
                .paymentId(paymentId)
                .amount(event.getAmount())
                .success(paymentId != null)
                .build();
                
            processedEventRepository.save(processedEvent);
            
            log.info("Successfully processed recurring payment: {} - Payment ID: {}", 
                event.getSubscriptionId(), paymentId);
                
        } catch (Exception e) {
            log.error("CRITICAL: Failed to process recurring payment: {}", 
                event.getSubscriptionId(), e);
                
            // Handle critical failure
            handleCriticalFailure(event, e);
            
            throw new RuntimeException("Recurring payment processing failed", e);
        }
    }
    
    private void validateRecurringPayment(RecurringPayment recurringPayment, RecurringPaymentDueEvent event) {
        // Validate recurring payment is active and not expired
        if (!recurringPayment.isActive()) {
            throw new RuntimeException("Recurring payment is not active: " + event.getSubscriptionId());
        }
        
        if (recurringPayment.getEndDate() != null && 
            recurringPayment.getEndDate().isBefore(LocalDateTime.now())) {
            recurringPayment.setActive(false);
            recurringPayment.setStatus("EXPIRED");
            recurringPaymentRepository.save(recurringPayment);
            throw new RuntimeException("Recurring payment has expired: " + event.getSubscriptionId());
        }
        
        // Check if within retry window for failed payments
        if (recurringPayment.getConsecutiveFailures() >= MAX_RETRY_ATTEMPTS) {
            log.warn("Recurring payment {} has exceeded max retry attempts: {}", 
                event.getSubscriptionId(), recurringPayment.getConsecutiveFailures());
        }
    }
    
    private boolean checkPaymentFeasibility(RecurringPaymentDueEvent event) {
        // Check wallet balance
        BigDecimal availableBalance = walletService.getAvailableBalance(
            event.getUserId(),
            event.getWalletId()
        );
        
        boolean hasSufficientFunds = availableBalance.compareTo(event.getAmount()) >= 0;
        
        if (!hasSufficientFunds) {
            log.warn("Insufficient funds for recurring payment: {} - Required: {}, Available: {}", 
                event.getSubscriptionId(), event.getAmount(), availableBalance);
            
            // Check for backup payment methods
            boolean hasBackupMethod = paymentProcessingService.hasBackupPaymentMethod(
                event.getUserId(),
                event.getAmount()
            );
            
            if (hasBackupMethod) {
                log.info("Backup payment method available for recurring payment: {}", 
                    event.getSubscriptionId());
                return true;
            }
        }
        
        return hasSufficientFunds;
    }
    
    private void sendPrePaymentNotification(RecurringPaymentDueEvent event) {
        // Send notification before processing payment
        notificationService.sendRecurringPaymentReminder(
            event.getUserId(),
            event.getSubscriptionId(),
            event.getAmount(),
            event.getMerchantName(),
            event.getDueDate()
        );
        
        log.info("Pre-payment notification sent for recurring payment: {}", event.getSubscriptionId());
    }
    
    private String processRecurringPayment(RecurringPayment recurringPayment, 
                                          RecurringPaymentDueEvent event, 
                                          boolean hasSufficientFunds) {
        try {
            String paymentId = null;
            
            if (hasSufficientFunds || recurringPayment.isAllowPartialPayment()) {
                // Process the payment
                paymentId = paymentProcessingService.processRecurringPayment(
                    event.getUserId(),
                    event.getWalletId(),
                    event.getMerchantId(),
                    event.getAmount(),
                    event.getCurrency(),
                    event.getSubscriptionId(),
                    event.getDescription(),
                    recurringPayment.getPaymentMethodId()
                );
                
                // Update recurring payment record
                recurringPayment.setLastPaymentId(paymentId);
                recurringPayment.setLastPaymentDate(LocalDateTime.now());
                recurringPayment.setLastPaymentAmount(event.getAmount());
                recurringPayment.setLastPaymentStatus(PaymentStatus.COMPLETED);
                recurringPayment.setConsecutiveFailures(0);
                recurringPayment.incrementSuccessfulPayments();
                
                log.info("Recurring payment processed successfully: {} - Payment ID: {}", 
                    event.getSubscriptionId(), paymentId);
            } else {
                // Attempt with retry strategy
                paymentId = attemptPaymentWithRetry(recurringPayment, event);
            }
            
            return paymentId;
            
        } catch (Exception e) {
            log.error("Failed to process recurring payment: {}", event.getSubscriptionId(), e);
            
            // Update failure count
            recurringPayment.incrementConsecutiveFailures();
            recurringPayment.setLastFailureReason(e.getMessage());
            recurringPayment.setLastPaymentStatus(PaymentStatus.FAILED);
            recurringPaymentRepository.save(recurringPayment);
            
            return null;
        }
    }
    
    private String attemptPaymentWithRetry(RecurringPayment recurringPayment, RecurringPaymentDueEvent event) {
        RetryStrategy retryStrategy = recurringPayment.getRetryStrategy();
        int currentAttempt = recurringPayment.getConsecutiveFailures();
        
        if (currentAttempt >= retryStrategy.getMaxAttempts()) {
            log.warn("Max retry attempts reached for recurring payment: {}", event.getSubscriptionId());
            return null;
        }
        
        // Calculate next retry time based on strategy
        LocalDateTime nextRetryTime = calculateNextRetryTime(retryStrategy, currentAttempt);
        
        // Schedule retry
        schedulePaymentRetry(recurringPayment, event, nextRetryTime);
        
        log.info("Recurring payment retry scheduled for: {} at {}", 
            event.getSubscriptionId(), nextRetryTime);
        
        return null;
    }
    
    private void handleSuccessfulPayment(RecurringPayment recurringPayment, 
                                        RecurringPaymentDueEvent event, 
                                        String paymentId) {
        // Send success notification
        notificationService.sendRecurringPaymentSuccess(
            event.getUserId(),
            paymentId,
            event.getAmount(),
            event.getMerchantName(),
            event.getSubscriptionId()
        );
        
        // Update subscription service
        updateSubscriptionService(event, paymentId, true);
        
        // Publish success event
        publishRecurringPaymentSuccessEvent(recurringPayment, event, paymentId);
        
        log.info("Recurring payment success handled for: {}", event.getSubscriptionId());
    }
    
    private void handleFailedPayment(RecurringPayment recurringPayment, RecurringPaymentDueEvent event) {
        // Send failure notification with retry information
        notificationService.sendRecurringPaymentFailure(
            event.getUserId(),
            event.getSubscriptionId(),
            event.getAmount(),
            event.getMerchantName(),
            recurringPayment.getLastFailureReason(),
            recurringPayment.getConsecutiveFailures() < MAX_RETRY_ATTEMPTS
        );
        
        // Update subscription service
        updateSubscriptionService(event, null, false);
        
        // Check if suspension needed
        if (recurringPayment.getConsecutiveFailures() >= recurringPayment.getSuspensionThreshold()) {
            suspendSubscription(recurringPayment, event);
        }
        
        // Publish failure event
        publishRecurringPaymentFailureEvent(recurringPayment, event);
        
        log.info("Recurring payment failure handled for: {} - Consecutive failures: {}", 
            event.getSubscriptionId(), recurringPayment.getConsecutiveFailures());
    }
    
    private void updateRecurringPaymentStats(RecurringPayment recurringPayment, 
                                            RecurringPaymentDueEvent event, 
                                            boolean success) {
        // Update statistics
        recurringPayment.incrementTotalAttempts();
        
        if (success) {
            recurringPayment.addToTotalPaid(event.getAmount());
            recurringPayment.updateSuccessRate();
        } else {
            recurringPayment.incrementTotalFailures();
        }
        
        recurringPayment.setLastUpdated(Instant.now());
        recurringPaymentRepository.save(recurringPayment);
        
        log.info("Recurring payment stats updated for: {} - Success rate: {}%", 
            event.getSubscriptionId(), recurringPayment.getSuccessRate());
    }
    
    private void scheduleNextPayment(RecurringPayment recurringPayment, RecurringPaymentDueEvent event) {
        LocalDateTime nextPaymentDate = calculateNextPaymentDate(
            recurringPayment.getFrequency(),
            event.getDueDate()
        );
        
        recurringPayment.setNextPaymentDate(nextPaymentDate);
        recurringPaymentRepository.save(recurringPayment);
        
        // Publish next payment due event
        Map<String, Object> nextPaymentEvent = Map.of(
            "eventId", UUID.randomUUID().toString(),
            "subscriptionId", event.getSubscriptionId(),
            "userId", event.getUserId(),
            "amount", event.getAmount(),
            "dueDate", nextPaymentDate,
            "billingPeriod", calculateBillingPeriod(nextPaymentDate)
        );
        
        kafkaTemplate.send("payment.recurring.scheduled", nextPaymentEvent);
        
        log.info("Next recurring payment scheduled for: {} on {}", 
            event.getSubscriptionId(), nextPaymentDate);
    }
    
    private void updateSubscriptionStatus(RecurringPayment recurringPayment, RecurringPaymentDueEvent event) {
        // Check if subscription should be canceled due to failures
        if (recurringPayment.getConsecutiveFailures() >= recurringPayment.getCancellationThreshold()) {
            recurringPayment.setActive(false);
            recurringPayment.setStatus("CANCELED_DUE_TO_PAYMENT_FAILURE");
            recurringPayment.setCanceledAt(LocalDateTime.now());
            recurringPaymentRepository.save(recurringPayment);
            
            // Notify user of cancellation
            notificationService.sendSubscriptionCancellation(
                event.getUserId(),
                event.getSubscriptionId(),
                event.getMerchantName(),
                "Payment failures exceeded threshold"
            );
            
            log.warn("Subscription canceled due to payment failures: {}", event.getSubscriptionId());
        }
    }
    
    private void initiateMerchantSettlement(RecurringPaymentDueEvent event, String paymentId) {
        // Initiate settlement to merchant
        Map<String, Object> settlementEvent = Map.of(
            "eventId", UUID.randomUUID().toString(),
            "paymentId", paymentId,
            "merchantId", event.getMerchantId(),
            "amount", event.getAmount(),
            "currency", event.getCurrency(),
            "subscriptionId", event.getSubscriptionId(),
            "settlementDate", LocalDateTime.now().plusDays(2) // T+2 settlement
        );
        
        kafkaTemplate.send("merchant.settlement.initiated", settlementEvent);
        
        log.info("Merchant settlement initiated for payment: {}", paymentId);
    }
    
    private String generateIdempotencyKey(RecurringPaymentDueEvent event) {
        return String.format("%s_%s_%s", 
            event.getSubscriptionId(), 
            event.getBillingPeriod(),
            event.getDueDate().toLocalDate());
    }
    
    private LocalDateTime calculateNextRetryTime(RetryStrategy strategy, int attemptNumber) {
        int delayMinutes = switch (strategy) {
            case EXPONENTIAL -> (int) Math.pow(2, attemptNumber) * 60; // 1hr, 2hr, 4hr...
            case LINEAR -> (attemptNumber + 1) * 360; // 6hr, 12hr, 18hr...
            case IMMEDIATE -> 5; // 5 minutes
            default -> 1440; // 24 hours default
        };
        
        return LocalDateTime.now().plusMinutes(delayMinutes);
    }
    
    private void schedulePaymentRetry(RecurringPayment recurringPayment, 
                                     RecurringPaymentDueEvent event, 
                                     LocalDateTime retryTime) {
        Map<String, Object> retryEvent = Map.of(
            "eventId", UUID.randomUUID().toString(),
            "originalEventId", event.getEventId(),
            "subscriptionId", event.getSubscriptionId(),
            "retryAttempt", recurringPayment.getConsecutiveFailures() + 1,
            "scheduledTime", retryTime
        );
        
        kafkaTemplate.send("payment.recurring.retry.scheduled", retryEvent);
    }
    
    private void suspendSubscription(RecurringPayment recurringPayment, RecurringPaymentDueEvent event) {
        recurringPayment.setSuspended(true);
        recurringPayment.setSuspendedAt(LocalDateTime.now());
        recurringPayment.setSuspensionReason("PAYMENT_FAILURES");
        recurringPaymentRepository.save(recurringPayment);
        
        // Notify user of suspension
        notificationService.sendSubscriptionSuspension(
            event.getUserId(),
            event.getSubscriptionId(),
            event.getMerchantName(),
            recurringPayment.getConsecutiveFailures()
        );
        
        log.warn("Subscription suspended: {} due to {} consecutive failures", 
            event.getSubscriptionId(), recurringPayment.getConsecutiveFailures());
    }
    
    private void updateSubscriptionService(RecurringPaymentDueEvent event, String paymentId, boolean success) {
        Map<String, Object> updateEvent = Map.of(
            "subscriptionId", event.getSubscriptionId(),
            "paymentId", paymentId != null ? paymentId : "",
            "success", success,
            "billingPeriod", event.getBillingPeriod()
        );
        
        kafkaTemplate.send("subscription.payment.status", updateEvent);
    }
    
    private LocalDateTime calculateNextPaymentDate(String frequency, LocalDateTime currentDue) {
        return switch (frequency.toUpperCase()) {
            case "DAILY" -> currentDue.plusDays(1);
            case "WEEKLY" -> currentDue.plusWeeks(1);
            case "BIWEEKLY" -> currentDue.plusWeeks(2);
            case "MONTHLY" -> currentDue.plusMonths(1);
            case "QUARTERLY" -> currentDue.plusMonths(3);
            case "SEMI_ANNUALLY" -> currentDue.plusMonths(6);
            case "ANNUALLY" -> currentDue.plusYears(1);
            default -> currentDue.plusMonths(1);
        };
    }
    
    private String calculateBillingPeriod(LocalDateTime paymentDate) {
        return String.format("%d-%02d", paymentDate.getYear(), paymentDate.getMonthValue());
    }
    
    private void publishRecurringPaymentSuccessEvent(RecurringPayment recurringPayment, 
                                                    RecurringPaymentDueEvent event, 
                                                    String paymentId) {
        Map<String, Object> successEvent = Map.of(
            "eventId", UUID.randomUUID().toString(),
            "subscriptionId", event.getSubscriptionId(),
            "paymentId", paymentId,
            "userId", event.getUserId(),
            "amount", event.getAmount(),
            "merchantId", event.getMerchantId(),
            "processedAt", Instant.now()
        );
        
        kafkaTemplate.send("payment.recurring.success", successEvent);
    }
    
    private void publishRecurringPaymentFailureEvent(RecurringPayment recurringPayment, 
                                                    RecurringPaymentDueEvent event) {
        Map<String, Object> failureEvent = Map.of(
            "eventId", UUID.randomUUID().toString(),
            "subscriptionId", event.getSubscriptionId(),
            "userId", event.getUserId(),
            "amount", event.getAmount(),
            "failureReason", recurringPayment.getLastFailureReason(),
            "consecutiveFailures", recurringPayment.getConsecutiveFailures(),
            "willRetry", recurringPayment.getConsecutiveFailures() < MAX_RETRY_ATTEMPTS
        );
        
        kafkaTemplate.send("payment.recurring.failed", failureEvent);
    }
    
    private void handleCriticalFailure(RecurringPaymentDueEvent event, Exception exception) {
        // Create manual intervention record
        manualInterventionService.createCriticalTask(
            "RECURRING_PAYMENT_PROCESSING_FAILED",
            String.format(
                "CRITICAL: Failed to process recurring payment. " +
                "Subscription ID: %s, User ID: %s, Amount: $%.2f. " +
                "Customer may experience service disruption. " +
                "Exception: %s. IMMEDIATE MANUAL INTERVENTION REQUIRED.",
                event.getSubscriptionId(),
                event.getUserId(),
                event.getAmount(),
                exception.getMessage()
            ),
            "CRITICAL",
            event,
            exception
        );
        
        // Send emergency notification
        try {
            notificationService.sendEmergencyAlert(
                "RECURRING_PAYMENT_SYSTEM_FAILURE",
                String.format("Critical failure processing recurring payment %s for user %s",
                    event.getSubscriptionId(), event.getUserId()),
                event
            );
        } catch (Exception e) {
            log.error("Failed to send emergency notification", e);
        }
    }
}