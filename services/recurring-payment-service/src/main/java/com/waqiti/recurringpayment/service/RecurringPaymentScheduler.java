package com.waqiti.recurringpayment.service;

import com.waqiti.recurringpayment.repository.RecurringExecutionRepository;
import com.waqiti.recurringpayment.repository.RecurringPaymentRepository;
import com.waqiti.recurringpayment.domain.ExecutionTrigger;
import com.waqiti.recurringpayment.domain.RecurringExecution;
import com.waqiti.recurringpayment.domain.RecurringPayment;
import com.waqiti.recurringpayment.domain.RecurringStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/**
 * Scheduled tasks for Recurring Payment processing
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecurringPaymentScheduler {
    
    private final RecurringPaymentRepository recurringRepository;
    private final RecurringExecutionRepository executionRepository;
    private final RecurringPaymentService recurringPaymentService;
    private final Executor recurringPaymentExecutor;
    
    @Value("${recurring.payment.execution.window.minutes:10}")
    private int executionWindowMinutes;
    
    @Value("${recurring.payment.batch.size:100}")
    private int batchSize;
    
    /**
     * Process scheduled recurring payments
     * Runs every 5 minutes
     */
    @Scheduled(fixedRate = 300000)
    @SchedulerLock(name = "processScheduledRecurringPayments", 
                   lockAtMostFor = "PT4M", 
                   lockAtLeastFor = "PT1M")
    public void processScheduledRecurringPayments() {
        log.info("Starting scheduled recurring payments processing");
        
        try {
            Instant now = Instant.now();
            Instant windowEnd = now.plus(Duration.ofMinutes(executionWindowMinutes));
            
            // Process in batches to avoid memory issues
            int offset = 0;
            int totalProcessed = 0;
            
            while (true) {
                List<RecurringPayment> batch = recurringRepository
                    .findDueForExecutionWithPagination(now, windowEnd, RecurringStatus.ACTIVE,
                                                       batchSize, offset);
                
                if (batch.isEmpty()) {
                    break;
                }
                
                // Process batch asynchronously
                List<CompletableFuture<Void>> futures = batch.stream()
                    .map(recurring -> CompletableFuture.runAsync(() -> {
                        try {
                            recurringPaymentService.processRecurringExecution(
                                recurring, ExecutionTrigger.SCHEDULED);
                        } catch (Exception e) {
                            log.error("Failed to process recurring payment {}: {}", 
                                    recurring.getId(), e.getMessage(), e);
                        }
                    }, recurringPaymentExecutor))
                    .collect(Collectors.toList());
                
                // Wait for batch to complete with timeout
                try {
                    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                        .get(5, java.util.concurrent.TimeUnit.MINUTES);
                } catch (java.util.concurrent.TimeoutException e) {
                    log.error("Recurring payment batch processing timed out after 5 minutes", e);
                    futures.forEach(f -> f.cancel(true));
                } catch (Exception e) {
                    log.error("Recurring payment batch processing failed", e);
                }

                totalProcessed += batch.size();
                offset += batchSize;
                
                log.debug("Processed batch of {} recurring payments", batch.size());
            }
            
            log.info("Completed processing {} scheduled recurring payments", totalProcessed);
            
        } catch (Exception e) {
            log.error("Error in scheduled recurring payments processing", e);
        }
    }
    
    /**
     * Process payment reminders
     * Runs daily at 9 AM
     */
    @Scheduled(cron = "0 0 9 * * ?")
    @SchedulerLock(name = "sendPaymentReminders", 
                   lockAtMostFor = "PT30M", 
                   lockAtLeastFor = "PT5M")
    public void sendPaymentReminders() {
        log.info("Starting payment reminders processing");
        
        try {
            LocalDate today = LocalDate.now();
            int totalReminders = 0;
            
            // Get all active recurring payments with reminders enabled
            List<RecurringPayment> paymentsWithReminders = recurringRepository
                .findActiveWithRemindersEnabled();
            
            for (RecurringPayment recurring : paymentsWithReminders) {
                try {
                    if (recurring.getNextExecutionDate() == null) {
                        continue;
                    }
                    
                    LocalDate nextPaymentDate = recurring.getNextExecutionDate()
                        .atZone(ZoneId.systemDefault()).toLocalDate();
                    
                    long daysUntilPayment = ChronoUnit.DAYS.between(today, nextPaymentDate);
                    
                    // Check if reminder should be sent for this number of days
                    if (recurring.getReminderDays() != null && 
                        recurring.getReminderDays().contains((int) daysUntilPayment)) {
                        
                        recurringPaymentService.sendPaymentReminder(
                            recurring, (int) daysUntilPayment);
                        totalReminders++;
                        
                        log.debug("Sent reminder for recurring payment {} ({} days until payment)", 
                                recurring.getId(), daysUntilPayment);
                    }
                    
                } catch (Exception e) {
                    log.error("Failed to process reminder for recurring payment {}: {}", 
                            recurring.getId(), e.getMessage());
                }
            }
            
            log.info("Completed sending {} payment reminders", totalReminders);
            
        } catch (Exception e) {
            log.error("Error in payment reminders processing", e);
        }
    }
    
    /**
     * Process retry attempts for failed executions
     * Runs every 15 minutes
     */
    @Scheduled(fixedRate = 900000)
    @SchedulerLock(name = "processRetryAttempts", 
                   lockAtMostFor = "PT10M", 
                   lockAtLeastFor = "PT2M")
    @Transactional
    public void processRetryAttempts() {
        log.info("Starting retry attempts processing");
        
        try {
            Instant now = Instant.now();
            
            // Find executions due for retry
            List<RecurringExecution> executionsToRetry = executionRepository
                .findFailedExecutionsDueForRetry(now);
            
            int totalRetries = 0;
            
            for (RecurringExecution execution : executionsToRetry) {
                try {
                    RecurringPayment recurring = recurringRepository
                        .findById(execution.getRecurringPaymentId())
                        .orElse(null);
                    
                    if (recurring == null || recurring.getStatus() != RecurringStatus.ACTIVE) {
                        continue;
                    }
                    
                    // Increment attempt count
                    execution.setAttemptCount(execution.getAttemptCount() + 1);
                    execution.setRetryAt(null);
                    executionRepository.save(execution);
                    
                    // Process retry
                    recurringPaymentService.retryExecution(recurring, execution);
                    totalRetries++;
                    
                    log.debug("Retried execution {} for recurring payment {} (attempt {})", 
                            execution.getId(), recurring.getId(), execution.getAttemptCount());
                    
                } catch (Exception e) {
                    log.error("Failed to retry execution {}: {}", 
                            execution.getId(), e.getMessage());
                }
            }
            
            log.info("Completed processing {} retry attempts", totalRetries);
            
        } catch (Exception e) {
            log.error("Error in retry attempts processing", e);
        }
    }
    
    /**
     * Clean up old execution records
     * Runs daily at 2 AM
     */
    @Scheduled(cron = "0 0 2 * * ?")
    @SchedulerLock(name = "cleanupOldExecutions", 
                   lockAtMostFor = "PT1H", 
                   lockAtLeastFor = "PT10M")
    @Transactional
    public void cleanupOldExecutions() {
        log.info("Starting cleanup of old execution records");
        
        try {
            // Keep executions for 90 days
            Instant cutoffDate = Instant.now().minus(Duration.ofDays(90));
            
            int deletedCount = executionRepository.deleteOldExecutions(cutoffDate);
            
            log.info("Deleted {} old execution records", deletedCount);
            
        } catch (Exception e) {
            log.error("Error in execution cleanup", e);
        }
    }
    
    /**
     * Check and update recurring payment statuses
     * Runs hourly
     */
    @Scheduled(fixedRate = 3600000)
    @SchedulerLock(name = "updateRecurringStatuses", 
                   lockAtMostFor = "PT30M", 
                   lockAtLeastFor = "PT5M")
    @Transactional
    public void updateRecurringStatuses() {
        log.info("Starting recurring payment status updates");
        
        try {
            Instant now = Instant.now();
            
            // Find recurring payments that should be completed
            List<RecurringPayment> paymentsToComplete = recurringRepository
                .findPaymentsToComplete(now);
            
            int completedCount = 0;
            
            for (RecurringPayment recurring : paymentsToComplete) {
                try {
                    recurring.setStatus(RecurringStatus.COMPLETED);
                    recurring.setCompletedAt(now);
                    recurring.setUpdatedAt(now);
                    recurringRepository.save(recurring);
                    
                    // Send completion notification
                    recurringPaymentService.sendCompletionNotification(recurring);
                    
                    completedCount++;
                    
                    log.debug("Marked recurring payment {} as completed", recurring.getId());
                    
                } catch (Exception e) {
                    log.error("Failed to update status for recurring payment {}: {}", 
                            recurring.getId(), e.getMessage());
                }
            }
            
            log.info("Completed status updates for {} recurring payments", completedCount);
            
        } catch (Exception e) {
            log.error("Error in recurring status updates", e);
        }
    }
    
    /**
     * Send upcoming payment notifications
     * Runs hourly
     */
    @Scheduled(fixedRate = 3600000)
    @SchedulerLock(name = "sendUpcomingPaymentNotifications", 
                   lockAtMostFor = "PT30M", 
                   lockAtLeastFor = "PT5M")
    public void sendUpcomingPaymentNotifications() {
        log.info("Starting upcoming payment notifications");
        
        try {
            Instant now = Instant.now();
            Instant notificationWindow = now.plus(Duration.ofHours(24));
            
            // Find payments due in the next 24 hours
            List<RecurringPayment> upcomingPayments = recurringRepository
                .findUpcomingPayments(now, notificationWindow);
            
            int notificationsSent = 0;
            
            for (RecurringPayment recurring : upcomingPayments) {
                try {
                    long hoursUntilPayment = ChronoUnit.HOURS.between(
                        now, recurring.getNextExecutionDate());
                    
                    recurringPaymentService.sendUpcomingPaymentNotification(
                        recurring, (int) hoursUntilPayment);
                    
                    notificationsSent++;
                    
                    log.debug("Sent upcoming payment notification for {} ({} hours)", 
                            recurring.getId(), hoursUntilPayment);
                    
                } catch (Exception e) {
                    log.error("Failed to send upcoming notification for {}: {}", 
                            recurring.getId(), e.getMessage());
                }
            }
            
            log.info("Sent {} upcoming payment notifications", notificationsSent);
            
        } catch (Exception e) {
            log.error("Error in upcoming payment notifications", e);
        }
    }
}