package com.waqiti.payment.notification.client;

import com.waqiti.payment.client.dto.UserResponse;
import com.waqiti.payment.domain.PaymentRequest;
import com.waqiti.payment.notification.model.CustomerActivationNotification;
import com.waqiti.payment.notification.model.NotificationResult;
import com.waqiti.payment.notification.model.ReconciliationNotification;
import com.waqiti.payment.notification.model.RefundNotification;

/**
 * Enterprise Email Notification Client Interface
 * 
 * Abstracts email delivery for payment-related notifications with:
 * - Template-based email composition
 * - Multi-recipient support and personalization
 * - Delivery tracking and confirmation
 * - HTML/plain text support with responsive design
 * - Integration with enterprise email providers (SendGrid, AWS SES, etc.)
 * 
 * @version 2.0.0
 * @since 2025-01-18
 */
public interface EmailNotificationClient {
    
    // =====================================
    // REFUND EMAIL NOTIFICATIONS
    // =====================================
    
    /**
     * Send refund notification email to customer
     * 
     * @param notification refund notification details
     * @return email delivery result
     */
    NotificationResult sendRefundNotification(RefundNotification notification);
    
    /**
     * Send refund notification email to merchant
     * 
     * @param notification refund notification details
     * @return email delivery result
     */
    NotificationResult sendMerchantRefundNotification(RefundNotification notification);
    
    /**
     * Send refund notification email to operations team
     * 
     * @param notification refund notification details
     * @return email delivery result
     */
    NotificationResult sendOperationsRefundNotification(RefundNotification notification);
    
    // =====================================
    // RECONCILIATION EMAIL NOTIFICATIONS
    // =====================================
    
    /**
     * Send reconciliation notification email to accounting team
     * 
     * @param notification reconciliation notification details
     * @return email delivery result
     */
    NotificationResult sendAccountingReconciliationNotification(ReconciliationNotification notification);
    
    /**
     * Send reconciliation notification email to operations team
     * 
     * @param notification reconciliation notification details
     * @return email delivery result
     */
    NotificationResult sendOperationsReconciliationNotification(ReconciliationNotification notification);
    
    /**
     * Send critical reconciliation alert email
     * 
     * @param notification reconciliation notification details
     * @return email delivery result
     */
    NotificationResult sendCriticalReconciliationAlert(ReconciliationNotification notification);
    
    // =====================================
    // CUSTOMER ACTIVATION EMAIL NOTIFICATIONS
    // =====================================
    
    /**
     * Send customer activation welcome email
     * 
     * @param notification activation notification details
     * @return email delivery result
     */
    NotificationResult sendCustomerActivationEmail(CustomerActivationNotification notification);
    
    // =====================================
    // PAYMENT COMPLETION EMAIL NOTIFICATIONS
    // =====================================
    
    /**
     * Send payment completion confirmation email
     * 
     * @param paymentRequest completed payment details
     * @param customer customer information
     * @return email delivery result
     */
    NotificationResult sendPaymentCompletionNotification(PaymentRequest paymentRequest, UserResponse customer);
}