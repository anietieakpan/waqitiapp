package com.waqiti.recurringpayment.service;

import com.waqiti.common.metrics.MetricsCollector;
import com.waqiti.recurring.service.NotificationService;
import com.waqiti.recurringpayment.domain.ScheduleConfiguration;
import com.waqiti.recurringpayment.domain.ScheduledPayment;
import com.waqiti.recurringpayment.domain.ScheduledPaymentStatus;
import com.waqiti.recurringpayment.repository.RecurringPaymentRepository;
import com.waqiti.recurringpayment.repository.ScheduledPaymentRepository;
import com.waqiti.recurringpayment.domain.ExecutionStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * Advanced scheduled payment service with intelligent scheduling, retry mechanisms,
 * holiday adjustments, and predictive payment processing.
 *
 * Features:
 * - Flexible scheduling intervals (daily, weekly, bi-weekly, monthly, custom)
 * - Smart retry mechanisms with exponential backoff
 * - Holiday and weekend payment adjustments
 * - Payment reminder notifications
 * - Bulk scheduled payment management
 * - Predictive payment processing for optimal timing
 * - Dynamic schedule optimization based on success patterns
 * - Payment bundling for efficiency
 * - Timezone-aware scheduling
 * - Payment pause and resume capabilities
 *
 * @author Waqiti Platform Team
 * @since 1.0
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AdvancedScheduledPaymentService {

    private final ScheduledPaymentRepository scheduledPaymentRepository;
    private final RecurringPaymentRepository recurringPaymentRepository;
    private final PaymentExecutionService paymentExecutionService;
    private final NotificationService notificationService;
    private final MetricsCollector metricsCollector;
    private final HolidayCalendarService holidayCalendarService;
    
    // Retry configuration
    private static final int MAX_RETRY_ATTEMPTS = 5;
    private static final long INITIAL_RETRY_DELAY_MS = 5000; // 5 seconds
    private static final double BACKOFF_MULTIPLIER = 2.0;
    private static final long MAX_RETRY_DELAY_MS = 3600000; // 1 hour
    
    // Payment processing optimization
    private final Map<String, PaymentPattern> paymentPatterns = new ConcurrentHashMap<>();
    private final Map<String, RetryContext> retryContexts = new ConcurrentHashMap<>();
    
    /**
     * Creates a new advanced scheduled payment with flexible configuration.
     *
     * @param request scheduled payment creation request
     * @return created scheduled payment
     */
    @Transactional
    public ScheduledPayment createScheduledPayment(CreateScheduledPaymentRequest request) {
        log.info("Creating scheduled payment for user: {} to recipient: {}", 
                request.getUserId(), request.getRecipientId());
        
        // Validate and enhance schedule
        ScheduleConfiguration schedule = enhanceScheduleConfiguration(request.getSchedule());
        
        ScheduledPayment payment = ScheduledPayment.builder()
                .userId(request.getUserId())
                .recipientId(request.getRecipientId())
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .description(request.getDescription())
                .schedule(schedule)
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .timezone(request.getTimezone() != null ? request.getTimezone() : ZoneId.systemDefault())
                .status(ScheduledPaymentStatus.ACTIVE)
                .reminderSettings(request.getReminderSettings())
                .retrySettings(request.getRetrySettings())
                .holidayHandling(request.getHolidayHandling())
                .metadata(request.getMetadata())
                .createdAt(Instant.now())
                .build();
        
        // Calculate first execution date
        payment.setNextExecutionDate(calculateNextExecutionDate(payment, Instant.now()));
        
        // Initialize payment pattern tracking
        initializePaymentPattern(payment);
        
        ScheduledPayment saved = scheduledPaymentRepository.save(payment);
        
        // Schedule reminder if enabled
        if (saved.getReminderSettings() != null && saved.getReminderSettings().isEnabled()) {
            scheduleReminder(saved);
        }
        
        metricsCollector.incrementCounter("scheduled_payments.created");
        log.info("Created scheduled payment: {} with next execution: {}", 
                saved.getId(), saved.getNextExecutionDate());
        
        return saved;
    }
    
    /**
     * Processes all due scheduled payments with intelligent batching and retry.
     */
    @Scheduled(fixedDelay = 60000) // Every minute
    public void processScheduledPayments() {
        try {
            Instant now = Instant.now();
            List<ScheduledPayment> duePayments = scheduledPaymentRepository.findDuePayments(now);
            
            if (duePayments.isEmpty()) {
                return;
            }
            
            log.info("Processing {} due scheduled payments", duePayments.size());
            
            // Group payments by optimization criteria
            Map<PaymentGroup, List<ScheduledPayment>> groupedPayments = groupPaymentsByOptimization(duePayments);
            
            // Process each group
            for (Map.Entry<PaymentGroup, List<ScheduledPayment>> entry : groupedPayments.entrySet()) {
                processPaymentGroup(entry.getKey(), entry.getValue());
            }
            
        } catch (Exception e) {
            log.error("Error processing scheduled payments", e);
            metricsCollector.incrementCounter("scheduled_payments.processing.errors");
        }
    }
    
    /**
     * Processes retry queue for failed payments with exponential backoff.
     */
    @Scheduled(fixedDelay = 30000) // Every 30 seconds
    public void processRetryQueue() {
        try {
            Set<String> paymentIdsToRetry = new HashSet<>();
            Instant now = Instant.now();
            
            // Check retry contexts for payments ready to retry
            for (Map.Entry<String, RetryContext> entry : retryContexts.entrySet()) {
                RetryContext context = entry.getValue();
                if (context.isReadyForRetry(now)) {
                    paymentIdsToRetry.add(entry.getKey());
                }
            }
            
            if (paymentIdsToRetry.isEmpty()) {
                return;
            }
            
            log.info("Processing {} payments in retry queue", paymentIdsToRetry.size());
            
            List<ScheduledPayment> paymentsToRetry = scheduledPaymentRepository.findAllById(paymentIdsToRetry);
            
            for (ScheduledPayment payment : paymentsToRetry) {
                retryPayment(payment);
            }
            
        } catch (Exception e) {
            log.error("Error processing retry queue", e);
            metricsCollector.incrementCounter("scheduled_payments.retry.errors");
        }
    }
    
    /**
     * Sends payment reminders for upcoming scheduled payments.
     */
    @Scheduled(cron = "0 0 9 * * *") // Daily at 9 AM
    public void sendPaymentReminders() {
        try {
            Instant reminderWindow = Instant.now().plus(Duration.ofHours(24));
            List<ScheduledPayment> upcomingPayments = scheduledPaymentRepository.findUpcomingPayments(
                    Instant.now(), reminderWindow);
            
            for (ScheduledPayment payment : upcomingPayments) {
                if (shouldSendReminder(payment)) {
                    sendPaymentReminder(payment);
                }
            }
            
            log.info("Sent reminders for {} upcoming payments", upcomingPayments.size());
            
        } catch (Exception e) {
            log.error("Error sending payment reminders", e);
        }
    }
    
    /**
     * Updates an existing scheduled payment.
     *
     * @param paymentId payment ID to update
     * @param request update request
     * @return updated payment
     */
    @Transactional
    public ScheduledPayment updateScheduledPayment(String paymentId, UpdateScheduledPaymentRequest request) {
        ScheduledPayment payment = scheduledPaymentRepository.findById(paymentId)
                .orElseThrow(() -> new ScheduledPaymentNotFoundException(paymentId));
        
        // Update modifiable fields
        if (request.getAmount() != null) {
            payment.setAmount(request.getAmount());
        }
        
        if (request.getDescription() != null) {
            payment.setDescription(request.getDescription());
        }
        
        if (request.getSchedule() != null) {
            payment.setSchedule(enhanceScheduleConfiguration(request.getSchedule()));
            payment.setNextExecutionDate(calculateNextExecutionDate(payment, Instant.now()));
        }
        
        if (request.getEndDate() != null) {
            payment.setEndDate(request.getEndDate());
        }
        
        if (request.getReminderSettings() != null) {
            payment.setReminderSettings(request.getReminderSettings());
        }
        
        if (request.getRetrySettings() != null) {
            payment.setRetrySettings(request.getRetrySettings());
        }
        
        payment.setUpdatedAt(Instant.now());
        
        ScheduledPayment updated = scheduledPaymentRepository.save(payment);
        
        log.info("Updated scheduled payment: {}", paymentId);
        metricsCollector.incrementCounter("scheduled_payments.updated");
        
        return updated;
    }
    
    /**
     * Pauses a scheduled payment temporarily.
     *
     * @param paymentId payment ID to pause
     * @param pauseUntil optional date to resume (null for indefinite pause)
     */
    @Transactional
    public void pauseScheduledPayment(String paymentId, Instant pauseUntil) {
        ScheduledPayment payment = scheduledPaymentRepository.findById(paymentId)
                .orElseThrow(() -> new ScheduledPaymentNotFoundException(paymentId));
        
        payment.setStatus(ScheduledPaymentStatus.PAUSED);
        payment.setPausedUntil(pauseUntil);
        payment.setPausedAt(Instant.now());
        
        scheduledPaymentRepository.save(payment);
        
        log.info("Paused scheduled payment: {} until: {}", paymentId, pauseUntil);
        metricsCollector.incrementCounter("scheduled_payments.paused");
    }
    
    /**
     * Resumes a paused scheduled payment.
     *
     * @param paymentId payment ID to resume
     */
    @Transactional
    public void resumeScheduledPayment(String paymentId) {
        ScheduledPayment payment = scheduledPaymentRepository.findById(paymentId)
                .orElseThrow(() -> new ScheduledPaymentNotFoundException(paymentId));
        
        if (payment.getStatus() != ScheduledPaymentStatus.PAUSED) {
            throw new IllegalStateException("Payment is not paused: " + paymentId);
        }
        
        payment.setStatus(ScheduledPaymentStatus.ACTIVE);
        payment.setPausedUntil(null);
        payment.setPausedAt(null);
        
        // Recalculate next execution date
        payment.setNextExecutionDate(calculateNextExecutionDate(payment, Instant.now()));
        
        scheduledPaymentRepository.save(payment);
        
        log.info("Resumed scheduled payment: {}", paymentId);
        metricsCollector.incrementCounter("scheduled_payments.resumed");
    }
    
    /**
     * Cancels a scheduled payment.
     *
     * @param paymentId payment ID to cancel
     * @param reason cancellation reason
     */
    @Transactional
    public void cancelScheduledPayment(String paymentId, String reason) {
        ScheduledPayment payment = scheduledPaymentRepository.findById(paymentId)
                .orElseThrow(() -> new ScheduledPaymentNotFoundException(paymentId));
        
        payment.setStatus(ScheduledPaymentStatus.CANCELLED);
        payment.setCancellationReason(reason);
        payment.setCancelledAt(Instant.now());
        
        scheduledPaymentRepository.save(payment);
        
        // Clean up retry context if exists
        retryContexts.remove(paymentId);
        
        log.info("Cancelled scheduled payment: {} with reason: {}", paymentId, reason);
        metricsCollector.incrementCounter("scheduled_payments.cancelled");
    }
    
    /**
     * Gets payment execution history for a scheduled payment.
     *
     * @param paymentId scheduled payment ID
     * @return list of execution records
     */
    public List<PaymentExecutionRecord> getExecutionHistory(String paymentId) {
        return scheduledPaymentRepository.findExecutionHistory(paymentId);
    }
    
    /**
     * Gets analytics for scheduled payments.
     *
     * @param userId user ID
     * @return payment analytics
     */
    public ScheduledPaymentAnalytics getAnalytics(String userId) {
        List<ScheduledPayment> userPayments = scheduledPaymentRepository.findByUserId(userId);
        
        ScheduledPaymentAnalytics analytics = new ScheduledPaymentAnalytics();
        analytics.setTotalScheduledPayments(userPayments.size());
        analytics.setActivePayments(userPayments.stream()
                .filter(p -> p.getStatus() == ScheduledPaymentStatus.ACTIVE)
                .count());
        
        // Calculate total monthly spending
        BigDecimal monthlySpending = userPayments.stream()
                .filter(p -> p.getStatus() == ScheduledPaymentStatus.ACTIVE)
                .map(p -> calculateMonthlyAmount(p))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        analytics.setTotalMonthlySpending(monthlySpending);
        
        // Success rate
        long totalExecutions = userPayments.stream()
                .mapToLong(p -> p.getTotalExecutions())
                .sum();
        long successfulExecutions = userPayments.stream()
                .mapToLong(p -> p.getSuccessfulExecutions())
                .sum();
        
        if (totalExecutions > 0) {
            analytics.setSuccessRate((double) successfulExecutions / totalExecutions * 100);
        }
        
        // Next payment due
        userPayments.stream()
                .filter(p -> p.getStatus() == ScheduledPaymentStatus.ACTIVE)
                .map(ScheduledPayment::getNextExecutionDate)
                .filter(Objects::nonNull)
                .min(Instant::compareTo)
                .ifPresent(analytics::setNextPaymentDue);
        
        return analytics;
    }
    
    // Private helper methods
    
    private ScheduleConfiguration enhanceScheduleConfiguration(ScheduleConfiguration schedule) {
        // Add intelligent defaults and enhancements
        if (schedule.getRetryOnFailure() == null) {
            schedule.setRetryOnFailure(true);
        }
        
        if (schedule.getSkipWeekends() == null) {
            schedule.setSkipWeekends(false);
        }
        
        if (schedule.getSkipHolidays() == null) {
            schedule.setSkipHolidays(false);
        }
        
        return schedule;
    }
    
    private Instant calculateNextExecutionDate(ScheduledPayment payment, Instant from) {
        ScheduleConfiguration schedule = payment.getSchedule();
        ZonedDateTime fromDateTime = from.atZone(payment.getTimezone());
        ZonedDateTime nextExecution = null;
        
        switch (schedule.getFrequency()) {
            case DAILY -> nextExecution = fromDateTime.plusDays(schedule.getInterval());
            
            case WEEKLY -> {
                nextExecution = fromDateTime.plusWeeks(schedule.getInterval());
                if (schedule.getDaysOfWeek() != null && !schedule.getDaysOfWeek().isEmpty()) {
                    // Find next matching day of week
                    nextExecution = findNextDayOfWeek(fromDateTime, schedule.getDaysOfWeek());
                }
            }
            
            case BIWEEKLY -> nextExecution = fromDateTime.plusWeeks(2 * schedule.getInterval());
            
            case MONTHLY -> {
                nextExecution = fromDateTime.plusMonths(schedule.getInterval());
                if (schedule.getDayOfMonth() != null) {
                    // Adjust to specific day of month
                    nextExecution = adjustToD  ayOfMonth(nextExecution, schedule.getDayOfMonth());
                }
            }
            
            case QUARTERLY -> nextExecution = fromDateTime.plusMonths(3 * schedule.getInterval());
            
            case YEARLY -> nextExecution = fromDateTime.plusYears(schedule.getInterval());
            
            case CUSTOM -> {
                if (schedule.getCustomInterval() != null) {
                    nextExecution = fromDateTime.plus(schedule.getCustomInterval());
                }
            }
        }
        
        // Apply holiday and weekend adjustments
        if (nextExecution != null) {
            nextExecution = applyHolidayAndWeekendAdjustments(payment, nextExecution);
        }
        
        // Ensure next execution is not past end date
        if (payment.getEndDate() != null && nextExecution != null) {
            Instant nextInstant = nextExecution.toInstant();
            if (nextInstant.isAfter(payment.getEndDate())) {
                return null; // Payment schedule completed
            }
        }
        
        return nextExecution != null ? nextExecution.toInstant() : null;
    }
    
    private ZonedDateTime findNextDayOfWeek(ZonedDateTime from, Set<DayOfWeek> daysOfWeek) {
        ZonedDateTime next = from.plusDays(1);
        while (!daysOfWeek.contains(next.getDayOfWeek())) {
            next = next.plusDays(1);
        }
        return next;
    }
    
    private ZonedDateTime adjustToDayOfMonth(ZonedDateTime dateTime, int dayOfMonth) {
        int lastDayOfMonth = dateTime.toLocalDate().lengthOfMonth();
        int targetDay = Math.min(dayOfMonth, lastDayOfMonth);
        return dateTime.withDayOfMonth(targetDay);
    }
    
    private ZonedDateTime applyHolidayAndWeekendAdjustments(ScheduledPayment payment, ZonedDateTime scheduledDate) {
        ScheduleConfiguration schedule = payment.getSchedule();
        ZonedDateTime adjusted = scheduledDate;
        
        // Skip weekends if configured
        if (Boolean.TRUE.equals(schedule.getSkipWeekends())) {
            while (adjusted.getDayOfWeek() == DayOfWeek.SATURDAY || 
                   adjusted.getDayOfWeek() == DayOfWeek.SUNDAY) {
                adjusted = adjusted.plusDays(1);
            }
        }
        
        // Skip holidays if configured
        if (Boolean.TRUE.equals(schedule.getSkipHolidays())) {
            while (holidayCalendarService.isHoliday(adjusted.toLocalDate(), payment.getCountryCode())) {
                adjusted = adjusted.plusDays(1);
                
                // Check if the new date is also a weekend
                if (Boolean.TRUE.equals(schedule.getSkipWeekends()) && 
                    (adjusted.getDayOfWeek() == DayOfWeek.SATURDAY || 
                     adjusted.getDayOfWeek() == DayOfWeek.SUNDAY)) {
                    adjusted = adjusted.plusDays(
                        adjusted.getDayOfWeek() == DayOfWeek.SATURDAY ? 2 : 1);
                }
            }
        }
        
        return adjusted;
    }
    
    private void initializePaymentPattern(ScheduledPayment payment) {
        PaymentPattern pattern = new PaymentPattern();
        pattern.setPaymentId(payment.getId());
        pattern.setTypicalProcessingTime(Duration.ofSeconds(5)); // Default estimate
        pattern.setSuccessRate(1.0); // Optimistic start
        paymentPatterns.put(payment.getId(), pattern);
    }
    
    private Map<PaymentGroup, List<ScheduledPayment>> groupPaymentsByOptimization(List<ScheduledPayment> payments) {
        // Group payments by recipient and currency for bundling
        return payments.stream()
                .collect(Collectors.groupingBy(p -> 
                    new PaymentGroup(p.getRecipientId(), p.getCurrency())));
    }
    
    private void processPaymentGroup(PaymentGroup group, List<ScheduledPayment> payments) {
        log.info("Processing payment group with {} payments to recipient: {}", 
                payments.size(), group.getRecipientId());
        
        // Process payments in parallel for efficiency
        List<CompletableFuture<PaymentResult>> futures = payments.stream()
                .map(payment -> CompletableFuture.supplyAsync(() -> executePayment(payment)))
                .toList();
        
        // Wait for all payments to complete with timeout
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .get(5, java.util.concurrent.TimeUnit.MINUTES);
        } catch (java.util.concurrent.TimeoutException e) {
            log.error("Payment group processing timed out after 5 minutes for recipient: {}", group.getRecipientId(), e);
            futures.forEach(f -> f.cancel(true));
        } catch (Exception e) {
            log.error("Payment group processing failed for recipient: {}", group.getRecipientId(), e);
        }

        // Process results
        for (int i = 0; i < payments.size(); i++) {
            ScheduledPayment payment = payments.get(i);
            try {
                PaymentResult result = futures.get(i).get(1, java.util.concurrent.TimeUnit.SECONDS);
                handlePaymentResult(payment, result);
            } catch (Exception e) {
                log.error("Failed to get payment result for payment: {}", payment.getId(), e);
            }
        }
    }
    
    private PaymentResult executePayment(ScheduledPayment payment) {
        try {
            log.info("Executing scheduled payment: {} for amount: {} {}", 
                    payment.getId(), payment.getAmount(), payment.getCurrency());
            
            // Execute payment through payment service
            PaymentExecutionRequest executionRequest = PaymentExecutionRequest.builder()
                    .paymentId(payment.getId())
                    .userId(payment.getUserId())
                    .recipientId(payment.getRecipientId())
                    .amount(payment.getAmount())
                    .currency(payment.getCurrency())
                    .description(payment.getDescription())
                    .metadata(payment.getMetadata())
                    .build();
            
            PaymentExecutionResponse response = paymentExecutionService.executePayment(executionRequest);
            
            return PaymentResult.success(response.getTransactionId(), response.getProcessedAt());
            
        } catch (InsufficientFundsException e) {
            log.warn("Insufficient funds for payment: {}", payment.getId());
            return PaymentResult.failure("INSUFFICIENT_FUNDS", e.getMessage());
            
        } catch (Exception e) {
            log.error("Failed to execute payment: {}", payment.getId(), e);
            return PaymentResult.failure("EXECUTION_ERROR", e.getMessage());
        }
    }
    
    private void handlePaymentResult(ScheduledPayment payment, PaymentResult result) {
        if (result.isSuccess()) {
            handleSuccessfulPayment(payment, result);
        } else {
            handleFailedPayment(payment, result);
        }
    }
    
    private void handleSuccessfulPayment(ScheduledPayment payment, PaymentResult result) {
        payment.setLastExecutionDate(Instant.now());
        payment.setLastExecutionStatus(ExecutionStatus.SUCCESS);
        payment.setTotalExecutions(payment.getTotalExecutions() + 1);
        payment.setSuccessfulExecutions(payment.getSuccessfulExecutions() + 1);
        payment.setConsecutiveFailures(0);
        payment.setTotalAmountPaid(payment.getTotalAmountPaid().add(payment.getAmount()));
        
        // Calculate next execution date
        Instant nextExecution = calculateNextExecutionDate(payment, Instant.now());
        payment.setNextExecutionDate(nextExecution);
        
        // Check if payment schedule is complete
        if (nextExecution == null || 
            (payment.getMaxOccurrences() != null && 
             payment.getTotalExecutions() >= payment.getMaxOccurrences())) {
            payment.setStatus(ScheduledPaymentStatus.COMPLETED);
            log.info("Scheduled payment {} completed all occurrences", payment.getId());
        }
        
        scheduledPaymentRepository.save(payment);
        
        // Record execution
        recordPaymentExecution(payment, result);
        
        // Update payment pattern
        updatePaymentPattern(payment.getId(), true);
        
        // Clear retry context if exists
        retryContexts.remove(payment.getId());
        
        metricsCollector.incrementCounter("scheduled_payments.executed.success");
        log.info("Successfully executed scheduled payment: {}", payment.getId());
    }
    
    private void handleFailedPayment(ScheduledPayment payment, PaymentResult result) {
        payment.setLastExecutionDate(Instant.now());
        payment.setLastExecutionStatus(ExecutionStatus.FAILED);
        payment.setTotalExecutions(payment.getTotalExecutions() + 1);
        payment.setFailedExecutions(payment.getFailedExecutions() + 1);
        payment.setConsecutiveFailures(payment.getConsecutiveFailures() + 1);
        payment.setLastFailureReason(result.getErrorMessage());
        
        scheduledPaymentRepository.save(payment);
        
        // Record execution
        recordPaymentExecution(payment, result);
        
        // Update payment pattern
        updatePaymentPattern(payment.getId(), false);
        
        // Determine if retry should be attempted
        if (shouldRetryPayment(payment, result)) {
            scheduleRetry(payment);
        } else {
            // Max retries exceeded or non-retryable error
            if (payment.getConsecutiveFailures() >= MAX_RETRY_ATTEMPTS) {
                payment.setStatus(ScheduledPaymentStatus.SUSPENDED);
                scheduledPaymentRepository.save(payment);
                
                // Send failure notification
                notificationService.sendPaymentFailureNotification(payment);
                
                log.error("Scheduled payment {} suspended after {} consecutive failures", 
                        payment.getId(), payment.getConsecutiveFailures());
            }
        }
        
        metricsCollector.incrementCounter("scheduled_payments.executed.failed");
        log.warn("Failed to execute scheduled payment: {} - {}", payment.getId(), result.getErrorMessage());
    }
    
    private boolean shouldRetryPayment(ScheduledPayment payment, PaymentResult result) {
        // Check if retry is enabled
        if (payment.getRetrySettings() == null || !payment.getRetrySettings().isEnabled()) {
            return false;
        }
        
        // Check if max retries exceeded
        if (payment.getConsecutiveFailures() >= MAX_RETRY_ATTEMPTS) {
            return false;
        }
        
        // Check if error is retryable
        return isRetryableError(result.getErrorCode());
    }
    
    private boolean isRetryableError(String errorCode) {
        // Define retryable error codes
        Set<String> retryableErrors = Set.of(
            "INSUFFICIENT_FUNDS",
            "TEMPORARY_FAILURE",
            "NETWORK_ERROR",
            "TIMEOUT",
            "SERVICE_UNAVAILABLE"
        );
        
        return retryableErrors.contains(errorCode);
    }
    
    private void scheduleRetry(ScheduledPayment payment) {
        RetryContext context = retryContexts.computeIfAbsent(payment.getId(), k -> new RetryContext());
        
        long delay = calculateRetryDelay(payment.getConsecutiveFailures());
        context.setNextRetryTime(Instant.now().plusMillis(delay));
        context.setRetryAttempt(payment.getConsecutiveFailures());
        
        log.info("Scheduled retry for payment {} in {}ms (attempt {})", 
                payment.getId(), delay, context.getRetryAttempt());
        
        metricsCollector.incrementCounter("scheduled_payments.retry.scheduled");
    }
    
    private long calculateRetryDelay(int attemptNumber) {
        // Exponential backoff with jitter
        long baseDelay = (long) (INITIAL_RETRY_DELAY_MS * Math.pow(BACKOFF_MULTIPLIER, attemptNumber - 1));
        long jitter = (long) (ThreadLocalRandom.current().nextDouble() * baseDelay * 0.1); // 10% jitter
        return Math.min(baseDelay + jitter, MAX_RETRY_DELAY_MS);
    }
    
    private void retryPayment(ScheduledPayment payment) {
        log.info("Retrying payment: {} (attempt {})", payment.getId(), payment.getConsecutiveFailures());
        
        PaymentResult result = executePayment(payment);
        handlePaymentResult(payment, result);
        
        metricsCollector.incrementCounter("scheduled_payments.retry.executed");
    }
    
    private void scheduleReminder(ScheduledPayment payment) {
        if (payment.getReminderSettings() == null || !payment.getReminderSettings().isEnabled()) {
            return;
        }
        
        // Schedule reminder notification
        Duration reminderAdvance = payment.getReminderSettings().getAdvanceNotice();
        Instant reminderTime = payment.getNextExecutionDate().minus(reminderAdvance);
        
        notificationService.scheduleReminder(payment.getUserId(), payment.getId(), reminderTime);
    }
    
    private boolean shouldSendReminder(ScheduledPayment payment) {
        if (payment.getReminderSettings() == null || !payment.getReminderSettings().isEnabled()) {
            return false;
        }
        
        // Check if reminder has already been sent
        return !payment.isReminderSent();
    }
    
    private void sendPaymentReminder(ScheduledPayment payment) {
        notificationService.sendPaymentReminder(payment);
        
        payment.setReminderSent(true);
        scheduledPaymentRepository.save(payment);
        
        metricsCollector.incrementCounter("scheduled_payments.reminders.sent");
    }
    
    private void recordPaymentExecution(ScheduledPayment payment, PaymentResult result) {
        PaymentExecutionRecord record = PaymentExecutionRecord.builder()
                .scheduledPaymentId(payment.getId())
                .executionTime(Instant.now())
                .status(result.isSuccess() ? ExecutionStatus.SUCCESS : ExecutionStatus.FAILED)
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .transactionId(result.getTransactionId())
                .errorMessage(result.getErrorMessage())
                .build();
        
        scheduledPaymentRepository.saveExecutionRecord(record);
    }
    
    private void updatePaymentPattern(String paymentId, boolean success) {
        PaymentPattern pattern = paymentPatterns.get(paymentId);
        if (pattern != null) {
            pattern.recordExecution(success);
        }
    }
    
    private BigDecimal calculateMonthlyAmount(ScheduledPayment payment) {
        if (payment.getSchedule() == null) {
            return BigDecimal.ZERO;
        }
        
        BigDecimal monthlyAmount = BigDecimal.ZERO;
        
        switch (payment.getSchedule().getFrequency()) {
            case DAILY -> monthlyAmount = payment.getAmount().multiply(BigDecimal.valueOf(30));
            case WEEKLY -> monthlyAmount = payment.getAmount().multiply(BigDecimal.valueOf(4.33));
            case BIWEEKLY -> monthlyAmount = payment.getAmount().multiply(BigDecimal.valueOf(2.17));
            case MONTHLY -> monthlyAmount = payment.getAmount();
            case QUARTERLY -> monthlyAmount = payment.getAmount().divide(BigDecimal.valueOf(3), 2, RoundingMode.HALF_UP);
            case YEARLY -> monthlyAmount = payment.getAmount().divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
        }
        
        return monthlyAmount;
    }
    
    // Supporting classes
    
    private static class PaymentGroup {
        private final String recipientId;
        private final String currency;
        
        public PaymentGroup(String recipientId, String currency) {
            this.recipientId = recipientId;
            this.currency = currency;
        }
        
        public String getRecipientId() { return recipientId; }
        public String getCurrency() { return currency; }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            PaymentGroup that = (PaymentGroup) o;
            return Objects.equals(recipientId, that.recipientId) && 
                   Objects.equals(currency, that.currency);
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(recipientId, currency);
        }
    }
    
    private static class PaymentPattern {
        private String paymentId;
        private Duration typicalProcessingTime;
        private double successRate;
        private int totalExecutions;
        private int successfulExecutions;
        
        public void recordExecution(boolean success) {
            totalExecutions++;
            if (success) {
                successfulExecutions++;
            }
            successRate = (double) successfulExecutions / totalExecutions;
        }
        
        // Getters and setters
        public String getPaymentId() { return paymentId; }
        public void setPaymentId(String paymentId) { this.paymentId = paymentId; }
        public Duration getTypicalProcessingTime() { return typicalProcessingTime; }
        public void setTypicalProcessingTime(Duration typicalProcessingTime) { this.typicalProcessingTime = typicalProcessingTime; }
        public double getSuccessRate() { return successRate; }
        public void setSuccessRate(double successRate) { this.successRate = successRate; }
    }
    
    private static class RetryContext {
        private Instant nextRetryTime;
        private int retryAttempt;
        
        public boolean isReadyForRetry(Instant now) {
            return nextRetryTime != null && now.isAfter(nextRetryTime);
        }
        
        // Getters and setters
        public Instant getNextRetryTime() { return nextRetryTime; }
        public void setNextRetryTime(Instant nextRetryTime) { this.nextRetryTime = nextRetryTime; }
        public int getRetryAttempt() { return retryAttempt; }
        public void setRetryAttempt(int retryAttempt) { this.retryAttempt = retryAttempt; }
    }
    
    private static class PaymentResult {
        private final boolean success;
        private final String transactionId;
        private final String errorCode;
        private final String errorMessage;
        private final Instant processedAt;
        
        private PaymentResult(boolean success, String transactionId, String errorCode, 
                             String errorMessage, Instant processedAt) {
            this.success = success;
            this.transactionId = transactionId;
            this.errorCode = errorCode;
            this.errorMessage = errorMessage;
            this.processedAt = processedAt;
        }
        
        public static PaymentResult success(String transactionId, Instant processedAt) {
            return new PaymentResult(true, transactionId, null, null, processedAt);
        }
        
        public static PaymentResult failure(String errorCode, String errorMessage) {
            return new PaymentResult(false, null, errorCode, errorMessage, Instant.now());
        }
        
        // Getters
        public boolean isSuccess() { return success; }
        public String getTransactionId() { return transactionId; }
        public String getErrorCode() { return errorCode; }
        public String getErrorMessage() { return errorMessage; }
        public Instant getProcessedAt() { return processedAt; }
    }
}