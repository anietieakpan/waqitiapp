package com.waqiti.payment.notification;

import com.waqiti.payment.core.model.RefundRecord;
import com.waqiti.payment.domain.PaymentRequest;
import com.waqiti.payment.dto.ReconciliationRecord;
import com.waqiti.payment.dto.ReconciliationDiscrepancy;
import com.waqiti.payment.notification.model.CustomerActivationNotification;
import com.waqiti.payment.notification.model.NotificationResult;
import com.waqiti.payment.notification.model.ReconciliationNotification;
import com.waqiti.payment.notification.model.RefundNotification;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Enterprise Payment Notification Service Interface
 * 
 * Comprehensive notification service for all payment-related events including:
 * - Refund notifications (customer, merchant, internal stakeholders)
 * - Reconciliation notifications (accounting, operations teams)
 * - Customer account activation notifications  
 * - Payment completion notifications
 * - Multi-channel delivery (email, SMS, push, webhook, Slack)
 * - Notification tracking and retry mechanisms
 * 
 * @version 2.0.0
 * @since 2025-01-18
 */
public interface PaymentNotificationServiceInterface {
    
    // =====================================
    // REFUND NOTIFICATIONS
    // =====================================
    
    /**
     * Send comprehensive refund notifications to all stakeholders
     * 
     * @param refundRecord the refund record
     * @param originalPayment the original payment
     * @return async notification result
     */
    CompletableFuture<NotificationResult> sendRefundNotifications(RefundRecord refundRecord, PaymentRequest originalPayment);
    
    /**
     * Send refund notification to customer
     * 
     * @param notification the refund notification details
     * @return notification result
     */
    NotificationResult sendRefundNotificationToCustomer(RefundNotification notification);
    
    /**
     * Send refund notification to merchant
     * 
     * @param notification the refund notification details
     * @return notification result
     */
    NotificationResult sendRefundNotificationToMerchant(RefundNotification notification);
    
    /**
     * Send refund notification to internal operations
     * 
     * @param notification the refund notification details
     * @return notification result
     */
    NotificationResult sendRefundNotificationToOperations(RefundNotification notification);
    
    // =====================================
    // RECONCILIATION NOTIFICATIONS
    // =====================================
    
    /**
     * Send reconciliation notifications to stakeholders
     * 
     * @param record the reconciliation record
     * @param discrepancies list of discrepancies found
     * @return async notification result
     */
    CompletableFuture<NotificationResult> sendReconciliationNotifications(ReconciliationRecord record, 
                                                                          List<ReconciliationDiscrepancy> discrepancies);
    
    /**
     * Send reconciliation notification to accounting team
     * 
     * @param notification the reconciliation notification details
     * @return notification result
     */
    NotificationResult sendReconciliationNotificationToAccounting(ReconciliationNotification notification);
    
    /**
     * Send reconciliation notification to operations team
     * 
     * @param notification the reconciliation notification details
     * @return notification result
     */
    NotificationResult sendReconciliationNotificationToOperations(ReconciliationNotification notification);
    
    /**
     * Send critical reconciliation alert (for major discrepancies)
     * 
     * @param notification the reconciliation notification details
     * @return notification result
     */
    NotificationResult sendCriticalReconciliationAlert(ReconciliationNotification notification);
    
    // =====================================
    // CUSTOMER ACTIVATION NOTIFICATIONS
    // =====================================
    
    /**
     * Send customer account activation notifications
     * 
     * @param customerId the customer ID
     * @return async notification result
     */
    CompletableFuture<NotificationResult> sendCustomerActivationNotifications(String customerId);
    
    /**
     * Send customer activation notification via email
     * 
     * @param notification the activation notification details
     * @return notification result
     */
    NotificationResult sendCustomerActivationEmail(CustomerActivationNotification notification);
    
    /**
     * Send customer activation notification via SMS
     * 
     * @param notification the activation notification details
     * @return notification result
     */
    NotificationResult sendCustomerActivationSMS(CustomerActivationNotification notification);
    
    /**
     * Send customer activation webhook notification
     * 
     * @param notification the activation notification details
     * @return notification result
     */
    NotificationResult sendCustomerActivationWebhook(CustomerActivationNotification notification);
    
    // =====================================
    // PAYMENT COMPLETION NOTIFICATIONS
    // =====================================
    
    /**
     * Send payment completion notifications to all stakeholders
     * 
     * @param paymentRequest the completed payment
     * @return async notification result
     */
    CompletableFuture<NotificationResult> sendPaymentCompletionNotifications(PaymentRequest paymentRequest);
    
    // =====================================
    // NOTIFICATION MANAGEMENT
    // =====================================
    
    /**
     * Retry failed notifications
     * 
     * @param notificationId the notification ID to retry
     * @return notification result
     */
    NotificationResult retryNotification(String notificationId);
    
    /**
     * Get notification delivery status
     * 
     * @param notificationId the notification ID
     * @return notification result with delivery status
     */
    NotificationResult getNotificationStatus(String notificationId);
    
    /**
     * Cancel pending notification
     * 
     * @param notificationId the notification ID to cancel
     * @return true if cancelled successfully
     */
    boolean cancelNotification(String notificationId);
    
    /**
     * Bulk send notifications (for batch processing)
     * 
     * @param notifications list of notifications to send
     * @return list of notification results
     */
    List<NotificationResult> bulkSendNotifications(List<Object> notifications);
}