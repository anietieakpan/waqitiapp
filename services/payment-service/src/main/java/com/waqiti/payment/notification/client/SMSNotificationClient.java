package com.waqiti.payment.notification.client;

import com.waqiti.payment.client.dto.UserResponse;
import com.waqiti.payment.domain.PaymentRequest;
import com.waqiti.payment.notification.model.CustomerActivationNotification;
import com.waqiti.payment.notification.model.NotificationResult;
import com.waqiti.payment.notification.model.ReconciliationNotification;
import com.waqiti.payment.notification.model.RefundNotification;

/**
 * Enterprise SMS Notification Client Interface
 * 
 * Abstracts SMS delivery for payment-related notifications with:
 * - Template-based SMS composition with character limit optimization
 * - International SMS support with proper formatting
 * - Delivery tracking and confirmation receipts
 * - Cost optimization and provider failover
 * - Integration with enterprise SMS providers (Twilio, AWS SNS, etc.)
 * 
 * @version 2.0.0
 * @since 2025-01-18
 */
public interface SMSNotificationClient {
    
    // =====================================
    // REFUND SMS NOTIFICATIONS
    // =====================================
    
    /**
     * Send refund notification SMS to customer
     * 
     * @param notification refund notification details
     * @return SMS delivery result
     */
    NotificationResult sendRefundNotification(RefundNotification notification);
    
    // =====================================
    // RECONCILIATION SMS NOTIFICATIONS
    // =====================================
    
    /**
     * Send critical reconciliation alert SMS
     * 
     * @param notification reconciliation notification details
     * @param phoneNumber recipient phone number
     * @return SMS delivery result
     */
    NotificationResult sendCriticalReconciliationAlert(ReconciliationNotification notification, String phoneNumber);
    
    // =====================================
    // CUSTOMER ACTIVATION SMS NOTIFICATIONS
    // =====================================
    
    /**
     * Send customer activation SMS notification
     * 
     * @param notification activation notification details
     * @return SMS delivery result
     */
    NotificationResult sendCustomerActivationSMS(CustomerActivationNotification notification);
    
    // =====================================
    // PAYMENT COMPLETION SMS NOTIFICATIONS
    // =====================================
    
    /**
     * Send payment completion SMS notification
     * 
     * @param paymentRequest completed payment details
     * @param customer customer information
     * @return SMS delivery result
     */
    NotificationResult sendPaymentCompletionNotification(PaymentRequest paymentRequest, UserResponse customer);
}