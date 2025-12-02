package com.waqiti.payment.notification.client;

import com.waqiti.payment.domain.PaymentRequest;
import com.waqiti.payment.notification.model.CustomerActivationNotification;
import com.waqiti.payment.notification.model.NotificationResult;
import com.waqiti.payment.notification.model.RefundNotification;

/**
 * Enterprise Webhook Notification Client Interface
 * 
 * Abstracts webhook delivery for payment-related notifications with:
 * - HTTP webhook delivery with retry mechanisms
 * - Signature verification and authentication
 * - Payload customization and formatting
 * - Delivery confirmation and tracking
 * - Integration with merchant and partner systems
 * 
 * @version 2.0.0
 * @since 2025-01-18
 */
public interface WebhookNotificationClient {
    
    // =====================================
    // REFUND WEBHOOK NOTIFICATIONS
    // =====================================
    
    /**
     * Send refund notification webhook
     * 
     * @param notification refund notification details
     * @return webhook delivery result
     */
    NotificationResult sendRefundNotification(RefundNotification notification);
    
    // =====================================
    // CUSTOMER ACTIVATION WEBHOOK NOTIFICATIONS
    // =====================================
    
    /**
     * Send customer activation webhook notification
     * 
     * @param notification activation notification details
     * @return webhook delivery result
     */
    NotificationResult sendCustomerActivationWebhook(CustomerActivationNotification notification);
    
    // =====================================
    // PAYMENT COMPLETION WEBHOOK NOTIFICATIONS
    // =====================================
    
    /**
     * Send payment completion webhook notification
     * 
     * @param paymentRequest completed payment details
     * @param webhookUrl target webhook URL
     * @return webhook delivery result
     */
    NotificationResult sendPaymentCompletionNotification(PaymentRequest paymentRequest, String webhookUrl);
}