/**
 * Notification Service Interface
 * Interface for sending notifications related to payments
 */
package com.waqiti.payment.service;

import com.waqiti.payment.entity.ScheduledPayment;
import com.waqiti.payment.dto.PaymentResponse;
import com.waqiti.payment.businessprofile.BusinessProfile;

/**
 * Interface for notification service
 * Actual implementation would integrate with the notification microservice
 */
public interface NotificationService {
    
    /**
     * Send notification when scheduled payment is created
     */
    void sendScheduledPaymentCreated(ScheduledPayment payment);
    
    /**
     * Send notification when scheduled payment is paused
     */
    void sendScheduledPaymentPaused(ScheduledPayment payment);
    
    /**
     * Send notification when scheduled payment is resumed
     */
    void sendScheduledPaymentResumed(ScheduledPayment payment);
    
    /**
     * Send notification when scheduled payment is cancelled
     */
    void sendScheduledPaymentCancelled(ScheduledPayment payment);
    
    /**
     * Send reminder for upcoming scheduled payment
     */
    void sendScheduledPaymentReminder(ScheduledPayment payment);
    
    /**
     * Send notification when scheduled payment is processed successfully
     */
    void sendScheduledPaymentProcessed(ScheduledPayment payment, PaymentResponse paymentResponse);
    
    /**
     * Send notification when scheduled payment fails
     */
    void sendScheduledPaymentFailed(ScheduledPayment payment, String error);
    
    // Business Profile notifications
    
    /**
     * Send welcome email to new business
     */
    void sendBusinessWelcomeEmail(BusinessProfile profile);
    
    /**
     * Send verification success email
     */
    void sendVerificationSuccessEmail(BusinessProfile profile);
    
    /**
     * Send verification failure email
     */
    void sendVerificationFailureEmail(BusinessProfile profile, String reason);
    
    /**
     * Send business suspension email
     */
    void sendSuspensionEmail(BusinessProfile profile, String reason);
    
    /**
     * Send business reactivation email
     */
    void sendReactivationEmail(BusinessProfile profile);
}