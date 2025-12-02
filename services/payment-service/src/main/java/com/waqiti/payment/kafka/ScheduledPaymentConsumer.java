package com.waqiti.payment.kafka;

import com.waqiti.common.kafka.GenericKafkaEvent;
import com.waqiti.payment.model.ScheduledPayment;
import com.waqiti.payment.model.ScheduledPaymentStatus;
import com.waqiti.payment.model.RecurrenceType;
import com.waqiti.payment.repository.ScheduledPaymentRepository;
import com.waqiti.payment.service.PaymentService;
import com.waqiti.payment.service.AccountService;
import com.waqiti.payment.service.NotificationService;
import com.waqiti.payment.service.AuditService;
import com.waqiti.payment.service.ComplianceService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Production-grade Kafka consumer for scheduled payment events
 * Handles recurring payments, bill pay, subscriptions, and scheduled transfers
 * 
 * Critical for: Customer payment automation, subscription management
 * SLA: Must execute within 60 seconds of scheduled time
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ScheduledPaymentConsumer {

    private final ScheduledPaymentRepository scheduledPaymentRepository;
    private final PaymentService paymentService;
    private final AccountService accountService;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final ComplianceService complianceService;
    private final ScheduledExecutorService scheduledExecutor;

    private static final BigDecimal MIN_BALANCE_BUFFER = new BigDecimal("10.00");
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long SCHEDULE_WINDOW_MS = 60000; // 1 minute execution window
    private static final long ADVANCE_NOTIFICATION_HOURS = 24;
    
    @KafkaListener(
        topics = "scheduled-payments",
        groupId = "scheduled-payment-processor",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    @CircuitBreaker(name = "scheduled-payment-processor", fallbackMethod = "handleScheduledPaymentFailure")
    @Retry(name = "scheduled-payment-processor")
    public void processScheduledPaymentEvent(
            @Payload @Valid GenericKafkaEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String eventId = event.getEventId();
        log.info("Processing scheduled payment event: {} from topic: {} partition: {} offset: {}", 
                eventId, topic, partition, offset);

        long startTime = System.currentTimeMillis();
        
        try {
            Map<String, Object> payload = event.getPayload();
            ScheduledPayment scheduledPayment = extractScheduledPayment(payload);
            
            // Check for duplicate processing
            if (isDuplicateSchedule(scheduledPayment)) {
                log.warn("Duplicate scheduled payment detected: {}, skipping", scheduledPayment.getPaymentId());
                acknowledgment.acknowledge();
                return;
            }
            
            // Validate the scheduled payment
            validateScheduledPayment(scheduledPayment);
            
            // Check account eligibility
            performEligibilityChecks(scheduledPayment);
            
            // Determine processing strategy
            ProcessingStrategy strategy = determineProcessingStrategy(scheduledPayment);
            
            // Process the scheduled payment
            ProcessingResult result = processScheduledPayment(scheduledPayment, strategy);
            
            // Update tracking and audit
            updatePaymentTracking(scheduledPayment, result);
            auditScheduledPayment(scheduledPayment, result, event);
            
            // Send notifications
            sendPaymentNotifications(scheduledPayment, result);
            
            // Handle recurrence if applicable
            if (scheduledPayment.isRecurring()) {
                scheduleNextPayment(scheduledPayment);
            }
            
            // Record metrics
            recordMetrics(scheduledPayment, startTime);
            
            // Acknowledge message
            acknowledgment.acknowledge();
            
            log.info("Successfully processed scheduled payment: {} in {}ms", 
                    scheduledPayment.getPaymentId(), 
                    System.currentTimeMillis() - startTime);
            
        } catch (ValidationException e) {
            log.error("Validation failed for scheduled payment event: {}", eventId, e);
            handleValidationError(event, e);
            acknowledgment.acknowledge();
            
        } catch (InsufficientFundsException e) {
            log.error("Insufficient funds for scheduled payment: {}", eventId, e);
            handleInsufficientFunds(event, e);
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process scheduled payment event: {}", eventId, e);
            handleProcessingError(event, e, acknowledgment);
        }
    }

    private ScheduledPayment extractScheduledPayment(Map<String, Object> payload) {
        return ScheduledPayment.builder()
            .paymentId(extractString(payload, "paymentId", UUID.randomUUID().toString()))
            .userId(extractString(payload, "userId", null))
            .sourceAccountId(extractString(payload, "sourceAccountId", null))
            .destinationAccountId(extractString(payload, "destinationAccountId", null))
            .amount(extractBigDecimal(payload, "amount"))
            .currency(extractString(payload, "currency", "USD"))
            .recurrenceType(RecurrenceType.fromString(extractString(payload, "recurrenceType", "ONE_TIME")))
            .scheduledTime(extractInstant(payload, "scheduledTime"))
            .endDate(extractLocalDate(payload, "endDate"))
            .paymentReference(extractString(payload, "paymentReference", null))
            .paymentType(extractString(payload, "paymentType", "TRANSFER"))
            .beneficiaryName(extractString(payload, "beneficiaryName", null))
            .beneficiaryReference(extractString(payload, "beneficiaryReference", null))
            .metadata(extractMap(payload, "metadata"))
            .retryCount(extractInteger(payload, "retryCount", 0))
            .lastAttemptTime(extractInstant(payload, "lastAttemptTime"))
            .status(ScheduledPaymentStatus.PENDING)
            .createdAt(Instant.now())
            .build();
    }

    private boolean isDuplicateSchedule(ScheduledPayment payment) {
        // Check if payment was recently processed
        return scheduledPaymentRepository.existsByPaymentIdAndStatusAndProcessedAtAfter(
            payment.getPaymentId(),
            ScheduledPaymentStatus.COMPLETED,
            Instant.now().minus(24, ChronoUnit.HOURS)
        );
    }

    private void validateScheduledPayment(ScheduledPayment payment) {
        // Validate required fields
        if (payment.getUserId() == null || payment.getUserId().isEmpty()) {
            throw new ValidationException("User ID is required for scheduled payment");
        }
        
        if (payment.getSourceAccountId() == null || payment.getSourceAccountId().isEmpty()) {
            throw new ValidationException("Source account is required");
        }
        
        if (payment.getDestinationAccountId() == null || payment.getDestinationAccountId().isEmpty()) {
            throw new ValidationException("Destination account is required");
        }
        
        if (payment.getAmount() == null || payment.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException("Invalid payment amount: " + payment.getAmount());
        }
        
        // Validate scheduled time
        if (payment.getScheduledTime() != null) {
            Instant now = Instant.now();
            Instant earliestAllowed = now.minus(SCHEDULE_WINDOW_MS, ChronoUnit.MILLIS);
            
            if (payment.getScheduledTime().isBefore(earliestAllowed)) {
                throw new ValidationException("Scheduled time is too far in the past: " + payment.getScheduledTime());
            }
        }
        
        // Validate recurrence settings
        if (payment.isRecurring()) {
            if (payment.getRecurrenceType() == null || payment.getRecurrenceType() == RecurrenceType.ONE_TIME) {
                throw new ValidationException("Invalid recurrence type for recurring payment");
            }
            
            if (payment.getEndDate() != null && payment.getEndDate().isBefore(LocalDate.now())) {
                throw new ValidationException("End date cannot be in the past");
            }
        }
        
        // Validate account ownership
        if (!accountService.isAccountOwner(payment.getUserId(), payment.getSourceAccountId())) {
            throw new ValidationException("User does not own the source account");
        }
        
        // Check for account blocks
        if (accountService.isAccountBlocked(payment.getSourceAccountId())) {
            throw new ValidationException("Source account is blocked");
        }
    }

    private void performEligibilityChecks(ScheduledPayment payment) {
        // Check account status
        String accountStatus = accountService.getAccountStatus(payment.getSourceAccountId());
        if (!"ACTIVE".equals(accountStatus)) {
            throw new ValidationException("Source account is not active: " + accountStatus);
        }
        
        // Check available balance
        BigDecimal availableBalance = accountService.getAvailableBalance(payment.getSourceAccountId());
        BigDecimal requiredBalance = payment.getAmount().add(MIN_BALANCE_BUFFER);
        
        if (availableBalance.compareTo(requiredBalance) < 0) {
            throw new InsufficientFundsException(String.format(
                "Insufficient funds. Required: %s, Available: %s",
                requiredBalance, availableBalance
            ));
        }
        
        // Check daily limits
        BigDecimal dailyLimit = accountService.getDailyTransferLimit(payment.getUserId());
        BigDecimal dailySpent = paymentService.getDailyTransferAmount(payment.getUserId(), LocalDate.now());
        
        if (dailySpent.add(payment.getAmount()).compareTo(dailyLimit) > 0) {
            throw new ValidationException(String.format(
                "Daily transfer limit exceeded. Limit: %s, Already spent: %s, Requested: %s",
                dailyLimit, dailySpent, payment.getAmount()
            ));
        }
        
        // Compliance checks for high-value payments
        if (payment.getAmount().compareTo(new BigDecimal("10000.00")) > 0) {
            if (!complianceService.isApprovedForHighValueTransfer(payment.getUserId())) {
                throw new ValidationException("User not approved for high-value transfers");
            }
        }
        
        // Check destination account validity
        if (!paymentService.isValidDestination(payment.getDestinationAccountId())) {
            throw new ValidationException("Invalid destination account");
        }
        
        // Check for sanctions
        if (complianceService.isSanctioned(payment.getDestinationAccountId())) {
            throw new ValidationException("Destination account is sanctioned");
        }
    }

    private ProcessingStrategy determineProcessingStrategy(ScheduledPayment payment) {
        Instant now = Instant.now();
        
        // Immediate execution for past due payments
        if (payment.getScheduledTime() == null || 
            payment.getScheduledTime().isBefore(now.plus(SCHEDULE_WINDOW_MS, ChronoUnit.MILLIS))) {
            return ProcessingStrategy.IMMEDIATE;
        }
        
        // Future scheduling
        long delayMs = ChronoUnit.MILLIS.between(now, payment.getScheduledTime());
        
        if (delayMs <= 3600000) { // Within 1 hour
            return ProcessingStrategy.SHORT_DELAY;
        } else if (delayMs <= 86400000) { // Within 24 hours
            return ProcessingStrategy.MEDIUM_DELAY;
        } else {
            return ProcessingStrategy.LONG_DELAY;
        }
    }

    private ProcessingResult processScheduledPayment(ScheduledPayment payment, ProcessingStrategy strategy) {
        log.info("Processing scheduled payment {} with strategy: {}", payment.getPaymentId(), strategy);
        
        ProcessingResult result = new ProcessingResult();
        result.setStartTime(Instant.now());
        result.setStrategy(strategy);
        
        try {
            switch (strategy) {
                case IMMEDIATE:
                    result = executeImmediatePayment(payment);
                    break;
                    
                case SHORT_DELAY:
                    result = scheduleShortDelayPayment(payment);
                    break;
                    
                case MEDIUM_DELAY:
                    result = scheduleMediumDelayPayment(payment);
                    break;
                    
                case LONG_DELAY:
                    result = scheduleLongDelayPayment(payment);
                    break;
                    
                default:
                    throw new IllegalStateException("Unknown processing strategy: " + strategy);
            }
            
            result.setEndTime(Instant.now());
            result.setProcessingTimeMs(
                ChronoUnit.MILLIS.between(result.getStartTime(), result.getEndTime())
            );
            
        } catch (Exception e) {
            log.error("Failed to process scheduled payment with strategy {}: {}", strategy, e.getMessage());
            result.setStatus(ProcessingStatus.FAILED);
            result.setErrorMessage(e.getMessage());
            throw new ProcessingException("Payment processing failed", e);
        }
        
        return result;
    }

    private ProcessingResult executeImmediatePayment(ScheduledPayment payment) {
        // Pre-execution validations
        performFinalValidations(payment);
        
        // Execute the payment
        String transactionId = paymentService.executeTransfer(
            payment.getSourceAccountId(),
            payment.getDestinationAccountId(),
            payment.getAmount(),
            payment.getCurrency(),
            "SCHEDULED_" + payment.getPaymentId(),
            payment.getMetadata()
        );
        
        // Update payment status
        payment.setStatus(ScheduledPaymentStatus.COMPLETED);
        payment.setTransactionId(transactionId);
        payment.setProcessedAt(Instant.now());
        payment.setExecutionAttempts(payment.getExecutionAttempts() + 1);
        scheduledPaymentRepository.save(payment);
        
        // Create audit record
        auditService.auditScheduledPaymentExecution(
            payment.getPaymentId(),
            payment.getUserId(),
            payment.getAmount(),
            transactionId,
            "SUCCESS"
        );
        
        return ProcessingResult.success(transactionId);
    }

    private ProcessingResult scheduleShortDelayPayment(ScheduledPayment payment) {
        long delayMs = ChronoUnit.MILLIS.between(Instant.now(), payment.getScheduledTime());
        
        // Schedule for execution
        scheduledExecutor.schedule(() -> {
            try {
                executeImmediatePayment(payment);
                sendExecutionNotification(payment, true);
            } catch (Exception e) {
                log.error("Failed to execute scheduled payment: {}", payment.getPaymentId(), e);
                handleExecutionFailure(payment, e);
                sendExecutionNotification(payment, false);
            }
        }, delayMs, TimeUnit.MILLISECONDS);
        
        // Update status to scheduled
        payment.setStatus(ScheduledPaymentStatus.SCHEDULED);
        payment.setNextExecutionTime(payment.getScheduledTime());
        scheduledPaymentRepository.save(payment);
        
        return ProcessingResult.scheduled(payment.getScheduledTime());
    }

    private ProcessingResult scheduleMediumDelayPayment(ScheduledPayment payment) {
        // Send advance notification
        sendAdvanceNotification(payment);
        
        // Calculate execution time
        long delayMs = ChronoUnit.MILLIS.between(Instant.now(), payment.getScheduledTime());
        
        // Schedule reminder notification
        long reminderDelayMs = delayMs - TimeUnit.HOURS.toMillis(1); // 1 hour before
        if (reminderDelayMs > 0) {
            scheduledExecutor.schedule(() -> {
                sendReminderNotification(payment);
            }, reminderDelayMs, TimeUnit.MILLISECONDS);
        }
        
        // Schedule execution
        scheduledExecutor.schedule(() -> {
            try {
                executeImmediatePayment(payment);
                sendExecutionNotification(payment, true);
            } catch (Exception e) {
                log.error("Failed to execute scheduled payment: {}", payment.getPaymentId(), e);
                handleExecutionFailure(payment, e);
                sendExecutionNotification(payment, false);
            }
        }, delayMs, TimeUnit.MILLISECONDS);
        
        payment.setStatus(ScheduledPaymentStatus.SCHEDULED);
        payment.setNextExecutionTime(payment.getScheduledTime());
        scheduledPaymentRepository.save(payment);
        
        return ProcessingResult.scheduled(payment.getScheduledTime());
    }

    private ProcessingResult scheduleLongDelayPayment(ScheduledPayment payment) {
        // Store in database for later processing
        payment.setStatus(ScheduledPaymentStatus.PENDING_SCHEDULE);
        payment.setNextExecutionTime(payment.getScheduledTime());
        scheduledPaymentRepository.save(payment);
        
        // Send confirmation notification
        notificationService.sendScheduleConfirmation(
            payment.getUserId(),
            payment.getPaymentId(),
            payment.getScheduledTime()
        );
        
        // Create calendar event (optional)
        createCalendarReminder(payment);
        
        return ProcessingResult.pendingSchedule(payment.getScheduledTime());
    }

    private void performFinalValidations(ScheduledPayment payment) {
        // Re-check balance immediately before execution
        BigDecimal currentBalance = accountService.getAvailableBalance(payment.getSourceAccountId());
        if (currentBalance.compareTo(payment.getAmount()) < 0) {
            throw new InsufficientFundsException("Insufficient funds at execution time");
        }
        
        // Verify accounts are still active
        if (!accountService.isAccountActive(payment.getSourceAccountId())) {
            throw new ValidationException("Source account is no longer active");
        }
        
        if (!accountService.isAccountActive(payment.getDestinationAccountId())) {
            throw new ValidationException("Destination account is no longer active");
        }
        
        // Check for any holds or blocks placed since scheduling
        if (accountService.hasHolds(payment.getSourceAccountId())) {
            throw new ValidationException("Account has active holds");
        }
    }

    private void scheduleNextPayment(ScheduledPayment payment) {
        if (payment.getEndDate() != null && LocalDate.now().isAfter(payment.getEndDate())) {
            log.info("Recurring payment {} has reached end date", payment.getPaymentId());
            payment.setStatus(ScheduledPaymentStatus.COMPLETED_SERIES);
            scheduledPaymentRepository.save(payment);
            return;
        }
        
        Instant nextScheduledTime = calculateNextScheduledTime(payment);
        
        // Create new scheduled payment event
        ScheduledPayment nextPayment = payment.toBuilder()
            .paymentId(UUID.randomUUID().toString())
            .scheduledTime(nextScheduledTime)
            .retryCount(0)
            .status(ScheduledPaymentStatus.PENDING)
            .parentPaymentId(payment.getPaymentId())
            .build();
        
        scheduledPaymentRepository.save(nextPayment);
        
        // Publish event for next payment
        publishScheduledPaymentEvent(nextPayment);
        
        log.info("Scheduled next recurring payment {} for {}", 
                nextPayment.getPaymentId(), nextScheduledTime);
    }

    private Instant calculateNextScheduledTime(ScheduledPayment payment) {
        Instant baseTime = payment.getProcessedAt() != null ? 
            payment.getProcessedAt() : payment.getScheduledTime();
        
        switch (payment.getRecurrenceType()) {
            case DAILY:
                return baseTime.plus(1, ChronoUnit.DAYS);
            case WEEKLY:
                return baseTime.plus(7, ChronoUnit.DAYS);
            case BIWEEKLY:
                return baseTime.plus(14, ChronoUnit.DAYS);
            case MONTHLY:
                return baseTime.atZone(ZoneId.systemDefault())
                    .plusMonths(1)
                    .toInstant();
            case QUARTERLY:
                return baseTime.atZone(ZoneId.systemDefault())
                    .plusMonths(3)
                    .toInstant();
            case YEARLY:
                return baseTime.atZone(ZoneId.systemDefault())
                    .plusYears(1)
                    .toInstant();
            default:
                throw new IllegalArgumentException("Invalid recurrence type: " + payment.getRecurrenceType());
        }
    }

    private void updatePaymentTracking(ScheduledPayment payment, ProcessingResult result) {
        payment.setLastUpdated(Instant.now());
        payment.setProcessingResult(result.toJson());
        
        if (result.getStatus() == ProcessingStatus.COMPLETED) {
            payment.setSuccessfulExecutions(payment.getSuccessfulExecutions() + 1);
        } else if (result.getStatus() == ProcessingStatus.FAILED) {
            payment.setFailedExecutions(payment.getFailedExecutions() + 1);
        }
        
        scheduledPaymentRepository.save(payment);
    }

    private void auditScheduledPayment(ScheduledPayment payment, ProcessingResult result, GenericKafkaEvent event) {
        auditService.auditScheduledPayment(
            payment.getPaymentId(),
            payment.getUserId(),
            payment.getAmount(),
            result.getStatus().toString(),
            event.getEventId(),
            payment.getRecurrenceType().toString()
        );
    }

    private void sendPaymentNotifications(ScheduledPayment payment, ProcessingResult result) {
        Map<String, Object> notificationData = Map.of(
            "paymentId", payment.getPaymentId(),
            "amount", payment.getAmount(),
            "currency", payment.getCurrency(),
            "beneficiary", payment.getBeneficiaryName(),
            "status", result.getStatus().toString(),
            "scheduledTime", payment.getScheduledTime(),
            "transactionId", result.getTransactionId() != null ? result.getTransactionId() : ""
        );
        
        // Send user notification
        CompletableFuture.runAsync(() -> {
            notificationService.sendPaymentNotification(
                payment.getUserId(),
                "SCHEDULED_PAYMENT_" + result.getStatus(),
                notificationData
            );
        });
        
        // Send beneficiary notification if configured
        if (payment.shouldNotifyBeneficiary()) {
            CompletableFuture.runAsync(() -> {
                notificationService.sendBeneficiaryNotification(
                    payment.getDestinationAccountId(),
                    "INCOMING_PAYMENT",
                    notificationData
                );
            });
        }
    }

    private void sendAdvanceNotification(ScheduledPayment payment) {
        notificationService.sendAdvancePaymentNotification(
            payment.getUserId(),
            payment.getPaymentId(),
            payment.getScheduledTime(),
            payment.getAmount()
        );
    }

    private void sendReminderNotification(ScheduledPayment payment) {
        notificationService.sendPaymentReminder(
            payment.getUserId(),
            payment.getPaymentId(),
            payment.getScheduledTime(),
            payment.getAmount()
        );
    }

    private void sendExecutionNotification(ScheduledPayment payment, boolean success) {
        if (success) {
            notificationService.sendPaymentSuccessNotification(
                payment.getUserId(),
                payment.getPaymentId(),
                payment.getAmount()
            );
        } else {
            notificationService.sendPaymentFailureNotification(
                payment.getUserId(),
                payment.getPaymentId(),
                payment.getAmount(),
                "Payment execution failed"
            );
        }
    }

    private void createCalendarReminder(ScheduledPayment payment) {
        // Integration with calendar services
        log.debug("Creating calendar reminder for payment: {}", payment.getPaymentId());
    }

    private void publishScheduledPaymentEvent(ScheduledPayment payment) {
        GenericKafkaEvent event = GenericKafkaEvent.builder()
            .eventId(UUID.randomUUID().toString())
            .eventType("SCHEDULED_PAYMENT_CREATED")
            .timestamp(Instant.now())
            .payload(payment.toMap())
            .build();
        
        kafkaTemplate.send("scheduled-payments", event);
    }

    private void handleExecutionFailure(ScheduledPayment payment, Exception e) {
        payment.setStatus(ScheduledPaymentStatus.FAILED);
        payment.setLastErrorMessage(e.getMessage());
        payment.setRetryCount(payment.getRetryCount() + 1);
        
        if (payment.getRetryCount() < MAX_RETRY_ATTEMPTS) {
            // Schedule retry
            Instant retryTime = Instant.now().plus((long) Math.pow(2, payment.getRetryCount()), ChronoUnit.MINUTES);
            payment.setNextExecutionTime(retryTime);
            payment.setStatus(ScheduledPaymentStatus.PENDING_RETRY);
            
            // Re-publish for retry
            publishScheduledPaymentEvent(payment);
        } else {
            payment.setStatus(ScheduledPaymentStatus.FAILED_PERMANENT);
        }
        
        scheduledPaymentRepository.save(payment);
    }

    private void recordMetrics(ScheduledPayment payment, long startTime) {
        long processingTime = System.currentTimeMillis() - startTime;
        
        metricsService.recordScheduledPaymentMetrics(
            payment.getRecurrenceType().toString(),
            payment.getAmount(),
            processingTime,
            payment.getStatus().toString()
        );
    }

    private void handleValidationError(GenericKafkaEvent event, ValidationException e) {
        auditService.logValidationError(event.getEventId(), e.getMessage());
        kafkaTemplate.send("scheduled-payment-validation-errors", event);
    }

    private void handleInsufficientFunds(GenericKafkaEvent event, InsufficientFundsException e) {
        String userId = event.getPayloadValue("userId", String.class);
        String paymentId = event.getPayloadValue("paymentId", String.class);
        
        // Notify user
        notificationService.sendInsufficientFundsNotification(userId, paymentId);
        
        // Create retry event for later
        GenericKafkaEvent retryEvent = event.toBuilder()
            .metadata(Map.of(
                "retryReason", "INSUFFICIENT_FUNDS",
                "retryAttempt", event.getMetadataValue("retryAttempt", Integer.class, 0) + 1,
                "originalError", e.getMessage()
            ))
            .build();
        
        // Schedule retry in 4 hours
        scheduledExecutor.schedule(() -> {
            kafkaTemplate.send("scheduled-payments-retry", retryEvent);
        }, 4, TimeUnit.HOURS);
        
        auditService.logInsufficientFunds(paymentId, userId, e.getMessage());
    }

    private void handleProcessingError(GenericKafkaEvent event, Exception e, Acknowledgment acknowledgment) {
        String eventId = event.getEventId();
        Integer retryCount = event.getMetadataValue("retryCount", Integer.class, 0);
        
        if (retryCount < MAX_RETRY_ATTEMPTS) {
            long retryDelay = (long) Math.pow(2, retryCount) * 1000;
            
            log.warn("Retrying scheduled payment event {} after {}ms (attempt {})", 
                    eventId, retryDelay, retryCount + 1);
            
            event.setMetadataValue("retryCount", retryCount + 1);
            event.setMetadataValue("lastError", e.getMessage());
            
            scheduledExecutor.schedule(() -> {
                kafkaTemplate.send("scheduled-payments-retry", event);
            }, retryDelay, TimeUnit.MILLISECONDS);
            
            acknowledgment.acknowledge();
        } else {
            log.error("Max retries exceeded for scheduled payment event {}, sending to DLQ", eventId);
            sendToDLQ(event, e);
            acknowledgment.acknowledge();
        }
    }

    private void sendToDLQ(GenericKafkaEvent event, Exception e) {
        event.setMetadataValue("dlqReason", e.getMessage());
        event.setMetadataValue("dlqTimestamp", Instant.now());
        event.setMetadataValue("originalTopic", "scheduled-payments");
        
        kafkaTemplate.send("scheduled-payments.DLQ", event);
        
        alertingService.createDLQAlert(
            "scheduled-payments",
            event.getEventId(),
            e.getMessage()
        );
    }

    // Fallback method for circuit breaker
    public void handleScheduledPaymentFailure(GenericKafkaEvent event, String topic, int partition, 
                                              long offset, Acknowledgment acknowledgment, Exception e) {
        log.error("Circuit breaker activated for scheduled payment processing: {}", e.getMessage());
        
        failedEventRepository.save(
            FailedEvent.builder()
                .eventId(event.getEventId())
                .topic(topic)
                .payload(event)
                .errorMessage(e.getMessage())
                .createdAt(Instant.now())
                .build()
        );
        
        alertingService.sendCriticalAlert(
            "Scheduled Payment Circuit Breaker Open",
            "Scheduled payment processing is failing. Manual intervention required."
        );
        
        acknowledgment.acknowledge();
    }

    // Helper methods
    private String extractString(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    private BigDecimal extractBigDecimal(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return BigDecimal.ZERO;
        if (value instanceof BigDecimal) return (BigDecimal) value;
        if (value instanceof Number) return new BigDecimal(value.toString());
        return new BigDecimal(value.toString());
    }

    private Integer extractInteger(Map<String, Object> map, String key, Integer defaultValue) {
        Object value = map.get(key);
        if (value == null) return defaultValue;
        if (value instanceof Integer) return (Integer) value;
        if (value instanceof Number) return ((Number) value).intValue();
        return Integer.parseInt(value.toString());
    }

    private Instant extractInstant(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof Instant) return (Instant) value;
        if (value instanceof Long) return Instant.ofEpochMilli((Long) value);
        return Instant.parse(value.toString());
    }

    private LocalDate extractLocalDate(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof LocalDate) return (LocalDate) value;
        return LocalDate.parse(value.toString());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractMap(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return new HashMap<>();
    }

    // Enums and inner classes
    public enum ProcessingStrategy {
        IMMEDIATE, SHORT_DELAY, MEDIUM_DELAY, LONG_DELAY
    }

    public enum ProcessingStatus {
        COMPLETED, SCHEDULED, PENDING_SCHEDULE, FAILED
    }

    @Data
    @Builder
    public static class ProcessingResult {
        private ProcessingStatus status;
        private String transactionId;
        private Instant startTime;
        private Instant endTime;
        private long processingTimeMs;
        private Instant scheduledExecutionTime;
        private ProcessingStrategy strategy;
        private String errorMessage;
        
        public static ProcessingResult success(String transactionId) {
            return ProcessingResult.builder()
                .status(ProcessingStatus.COMPLETED)
                .transactionId(transactionId)
                .build();
        }
        
        public static ProcessingResult scheduled(Instant executionTime) {
            return ProcessingResult.builder()
                .status(ProcessingStatus.SCHEDULED)
                .scheduledExecutionTime(executionTime)
                .build();
        }
        
        public static ProcessingResult pendingSchedule(Instant executionTime) {
            return ProcessingResult.builder()
                .status(ProcessingStatus.PENDING_SCHEDULE)
                .scheduledExecutionTime(executionTime)
                .build();
        }
        
        public String toJson() {
            try {
                return new ObjectMapper().writeValueAsString(this);
            } catch (Exception e) {
                return "{}";
            }
        }
    }

    // Custom exceptions
    public static class ValidationException extends RuntimeException {
        public ValidationException(String message) {
            super(message);
        }
    }

    public static class InsufficientFundsException extends RuntimeException {
        public InsufficientFundsException(String message) {
            super(message);
        }
    }

    public static class ProcessingException extends RuntimeException {
        public ProcessingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}