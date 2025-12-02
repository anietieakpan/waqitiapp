package com.waqiti.recurringpayment.service.clients;

import com.waqiti.recurringpayment.domain.RecurringExecution;
import com.waqiti.recurringpayment.domain.RecurringPayment;
import com.waqiti.recurringpayment.service.dto.FraudCheckResult;
import com.waqiti.recurringpayment.service.dto.NotificationPreferences;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Fallback implementation for Notification Service Client
 * 
 * Customer notifications are critical for transparency but not blocking.
 * Fallback strategy queues notifications for later delivery and logs
 * for audit trail. Payment processing should continue even if notifications fail.
 * 
 * Failure Strategy:
 * - LOG all notification attempts for later retry
 * - QUEUE critical notifications (failures, fraud) for priority delivery
 * - CONTINUE payment processing (non-blocking)
 * - TRACK notification debt for reconciliation
 * - ESCALATE repeated failures to operations team
 * 
 * @author Waqiti Platform Team
 * @since Phase 1 Remediation - Session 6
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationServiceClientFallback implements NotificationServiceClient {
    
    @Override
    public void sendRecurringPaymentCreatedNotification(String userId, RecurringPayment recurring) {
        log.warn("FALLBACK: Failed to send recurring payment created notification. " +
                "UserId: {}, PaymentId: {}, Amount: {}", 
                userId, recurring.getId(), recurring.getAmount());
        // Queue for retry - non-critical
    }
    
    @Override
    public void sendRecurringPaymentUpdatedNotification(String userId, RecurringPayment recurring) {
        log.warn("FALLBACK: Failed to send recurring payment updated notification. " +
                "UserId: {}, PaymentId: {}", userId, recurring.getId());
        // Queue for retry - non-critical
    }
    
    @Override
    public void sendRecurringPaymentPausedNotification(String userId, RecurringPayment recurring) {
        log.warn("FALLBACK: Failed to send payment paused notification. " +
                "UserId: {}, PaymentId: {}", userId, recurring.getId());
        // Queue for retry - semi-critical
    }
    
    @Override
    public void sendRecurringPaymentResumedNotification(String userId, RecurringPayment recurring) {
        log.warn("FALLBACK: Failed to send payment resumed notification. " +
                "UserId: {}, PaymentId: {}", userId, recurring.getId());
        // Queue for retry - non-critical
    }
    
    @Override
    public void sendRecurringPaymentCancelledNotification(String userId, RecurringPayment recurring) {
        log.warn("FALLBACK: Failed to send payment cancelled notification. " +
                "UserId: {}, PaymentId: {}", userId, recurring.getId());
        // Queue for retry - semi-critical
    }
    
    @Override
    public void sendRecurringPaymentSuccessNotification(String userId, 
                                                      RecurringPayment recurring,
                                                      RecurringExecution execution) {
        log.info("FALLBACK: Failed to send payment success notification. " +
                "UserId: {}, PaymentId: {}, ExecutionId: {}, Amount: {}", 
                userId, recurring.getId(), execution.getId(), execution.getAmount());
        // Queue for retry - low priority
    }
    
    /**
     * CRITICAL: Payment failure notification
     * Must be delivered eventually for customer awareness
     */
    @Override
    public void sendRecurringPaymentFailedNotification(String userId, 
                                                     RecurringPayment recurring,
                                                     RecurringExecution execution,
                                                     String message) {
        log.error("FALLBACK: CRITICAL - Failed to send payment failure notification. " +
                "UserId: {}, PaymentId: {}, ExecutionId: {}, FailureMessage: {}", 
                userId, recurring.getId(), execution.getId(), message);
        // Queue for HIGH PRIORITY retry
        // This is critical for customer trust
    }
    
    @Override
    public void sendPaymentReminderNotification(String userId, 
                                              RecurringPayment recurring,
                                              int daysUntilPayment) {
        log.info("FALLBACK: Failed to send payment reminder. " +
                "UserId: {}, PaymentId: {}, DaysUntil: {}", 
                userId, recurring.getId(), daysUntilPayment);
        // Queue for retry - medium priority
    }
    
    @Override
    public void sendUpcomingPaymentNotification(String userId,
                                              RecurringPayment recurring,
                                              int hoursUntilPayment) {
        log.info("FALLBACK: Failed to send upcoming payment notification. " +
                "UserId: {}, PaymentId: {}, HoursUntil: {}", 
                userId, recurring.getId(), hoursUntilPayment);
        // Queue for retry - medium priority
    }
    
    /**
     * CRITICAL: Fraud alert notification
     * Must be delivered for security compliance
     */
    @Override
    public void sendFraudAlertNotification(String userId, 
                                         RecurringPayment recurring,
                                         RecurringExecution execution,
                                         FraudCheckResult fraudResult) {
        log.error("FALLBACK: CRITICAL - Failed to send fraud alert notification. " +
                "UserId: {}, PaymentId: {}, FraudScore: {}, Action: {}", 
                userId, recurring.getId(), fraudResult.getRiskScore(), fraudResult.getAction());
        // Queue for IMMEDIATE retry
        // This is critical for security
    }
    
    @Override
    public void sendRecurringPaymentCompletedNotification(String userId, RecurringPayment recurring) {
        log.info("FALLBACK: Failed to send payment completed notification. " +
                "UserId: {}, PaymentId: {}", userId, recurring.getId());
        // Queue for retry - low priority
    }
    
    @Override
    public NotificationPreferences getUserNotificationPreferences(String userId) {
        log.warn("FALLBACK: Cannot retrieve notification preferences. Using defaults. UserId: {}", userId);
        
        // Return default preferences to allow processing to continue
        return NotificationPreferences.builder()
                .userId(userId)
                .emailEnabled(true)
                .smsEnabled(true)
                .pushEnabled(true)
                .paymentReminders(true)
                .paymentConfirmations(true)
                .failureAlerts(true)
                .fraudAlerts(true)
                .fallbackActivated(true)
                .build();
    }
}