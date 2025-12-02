package com.waqiti.payment.notification.client;

import com.waqiti.payment.notification.model.NotificationResult;
import com.waqiti.payment.notification.model.ReconciliationNotification;
import com.waqiti.payment.notification.model.RefundNotification;

/**
 * Enterprise Slack Notification Client Interface
 * 
 * Abstracts Slack delivery for payment-related notifications with:
 * - Rich message formatting with blocks and attachments
 * - Channel and direct message support
 * - Interactive buttons and workflows
 * - Thread management for related notifications
 * - Integration with Slack Bot API and Webhooks
 * 
 * @version 2.0.0
 * @since 2025-01-18
 */
public interface SlackNotificationClient {
    
    // =====================================
    // REFUND SLACK NOTIFICATIONS
    // =====================================
    
    /**
     * Send refund notification to Slack channel
     * 
     * @param notification refund notification details
     * @return Slack delivery result
     */
    NotificationResult sendRefundNotification(RefundNotification notification);
    
    // =====================================
    // RECONCILIATION SLACK NOTIFICATIONS
    // =====================================
    
    /**
     * Send reconciliation notification to Slack channel
     * 
     * @param notification reconciliation notification details
     * @return Slack delivery result
     */
    NotificationResult sendReconciliationNotification(ReconciliationNotification notification);
    
    /**
     * Send critical reconciliation alert to Slack
     * 
     * @param notification reconciliation notification details
     * @return Slack delivery result
     */
    NotificationResult sendCriticalReconciliationAlert(ReconciliationNotification notification);
}