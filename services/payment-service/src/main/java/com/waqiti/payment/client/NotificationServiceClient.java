package com.waqiti.payment.client;

import com.waqiti.payment.dto.*;
import com.waqiti.payment.dto.notification.GroupPaymentNotificationRequest;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

/**
 * Feign client for Notification Service integration
 */
@FeignClient(
    name = "notification-service",
    url = "${services.notification-service.url:http://notification-service:8080}",
    fallback = NotificationServiceClientFallback.class
)
public interface NotificationServiceClient {

    /**
     * Send payment notification
     */
    @PostMapping("/api/notifications/payment")
    @CircuitBreaker(name = "notification-service", fallbackMethod = "sendPaymentNotificationFallback")
    @Retry(name = "notification-service")
    @TimeLimiter(name = "notification-service")
    ResponseEntity<NotificationResponse> sendPaymentNotification(@Valid @RequestBody PaymentNotificationRequest request);

    /**
     * Send P2P notification
     */
    @PostMapping("/api/notifications/p2p")
    @CircuitBreaker(name = "notification-service", fallbackMethod = "sendP2PNotificationFallback")
    @Retry(name = "notification-service")
    @TimeLimiter(name = "notification-service")
    ResponseEntity<NotificationResponse> sendP2PNotification(@Valid @RequestBody P2PNotificationRequest request);

    /**
     * Send bulk notifications
     */
    @PostMapping("/api/notifications/bulk")
    ResponseEntity<BulkNotificationResponse> sendBulkNotifications(@Valid @RequestBody BulkNotificationRequest request);

    /**
     * Send SMS notification
     */
    @PostMapping("/api/notifications/sms")
    @CircuitBreaker(name = "notification-service", fallbackMethod = "sendSmsNotificationFallback")
    @Retry(name = "notification-service")
    @TimeLimiter(name = "notification-service")
    ResponseEntity<NotificationResponse> sendSmsNotification(@Valid @RequestBody SmsNotificationRequest request);

    /**
     * Send email notification
     */
    @PostMapping("/api/notifications/email")
    ResponseEntity<NotificationResponse> sendEmailNotification(@Valid @RequestBody EmailNotificationRequest request);

    /**
     * Send push notification
     */
    @PostMapping("/api/notifications/push")
    ResponseEntity<NotificationResponse> sendPushNotification(@Valid @RequestBody PushNotificationRequest request);

    /**
     * Get notification preferences
     */
    @GetMapping("/api/notifications/preferences/{userId}")
    ResponseEntity<NotificationPreferences> getNotificationPreferences(@PathVariable String userId);

    /**
     * Update notification preferences
     */
    @PutMapping("/api/notifications/preferences/{userId}")
    ResponseEntity<Void> updateNotificationPreferences(@PathVariable String userId, 
                                                       @Valid @RequestBody NotificationPreferences preferences);

    /**
     * Get notification history
     */
    @GetMapping("/api/notifications/history/{userId}")
    ResponseEntity<List<NotificationHistory>> getNotificationHistory(@PathVariable String userId,
                                                                     @RequestParam(defaultValue = "0") int page,
                                                                     @RequestParam(defaultValue = "20") int size);

    /**
     * Mark notification as read
     */
    @PostMapping("/api/notifications/{notificationId}/read")
    ResponseEntity<Void> markAsRead(@PathVariable String notificationId);

    /**
     * Send group payment notification
     * 
     * Handles all types of group payment notifications including:
     * - Group payment creation alerts
     * - Payment status updates
     * - Participant payment confirmations
     * - Settlement notifications
     * - Reminder notifications
     * - Cancellation alerts
     * 
     * NOTIFICATION RESILIENCE:
     * - Circuit breaker protection
     * - Automatic retries
     * - Timeout handling
     * - Fallback to batch processing
     * 
     * @param request Group payment notification request
     * @return Notification response with delivery status
     */
    @PostMapping("/api/notifications/group-payment")
    @CircuitBreaker(name = "notification-service", fallbackMethod = "sendGroupPaymentNotificationFallback")
    @Retry(name = "notification-service")
    @TimeLimiter(name = "notification-service")
    ResponseEntity<NotificationResponse> sendGroupPaymentNotification(@Valid @RequestBody GroupPaymentNotificationRequest request);
    
    /**
     * Send payment routing change notification
     * 
     * Notifies customers of payment routing changes including:
     * - Gateway changes for cost optimization
     * - Cost savings achieved
     * - Routing strategy explanations
     * 
     * @param request Routing change notification request
     * @return Notification response with delivery status
     */
    @PostMapping("/api/notifications/routing-change")
    @CircuitBreaker(name = "notification-service", fallbackMethod = "sendRoutingChangeNotificationFallback")
    @Retry(name = "notification-service")
    @TimeLimiter(name = "notification-service")
    ResponseEntity<NotificationResponse> sendRoutingChangeNotification(@Valid @RequestBody RoutingChangeNotificationRequest request);
    
    /**
     * Send payment cancellation approval notification
     * 
     * Notifies customers and operations team of cancellation approval status:
     * - Approved cancellations with refund details
     * - Rejected cancellations with rejection reasons
     * - Pending approvals awaiting decision
     * 
     * @param request Cancellation approval notification request
     * @return Notification response with delivery status
     */
    @PostMapping("/api/notifications/cancellation-approval")
    @CircuitBreaker(name = "notification-service", fallbackMethod = "sendCancellationApprovalNotificationFallback")
    @Retry(name = "notification-service")
    @TimeLimiter(name = "notification-service")
    ResponseEntity<NotificationResponse> sendCancellationApprovalNotification(@Valid @RequestBody CancellationApprovalNotificationRequest request);
    
    /**
     * Send payment dispute resolution notification
     * 
     * Notifies customers and merchants of dispute outcomes:
     * - Customer favor: Refund details and timelines
     * - Merchant favor: Payment stands, dispute rejected
     * - Investigation results and decisions
     * 
     * @param request Dispute resolution notification request
     * @return Notification response with delivery status
     */
    @PostMapping("/api/notifications/dispute-resolution")
    @CircuitBreaker(name = "notification-service", fallbackMethod = "sendDisputeResolutionNotificationFallback")
    @Retry(name = "notification-service")
    @TimeLimiter(name = "notification-service")
    ResponseEntity<NotificationResponse> sendDisputeResolutionNotification(@Valid @RequestBody DisputeResolutionNotificationRequest request);
    
    /**
     * Send payment reversal failure notification
     * 
     * Sends critical alerts for failed reversals to:
     * - Operations team: Immediate action required
     * - Customers: Refund delay notifications (when appropriate)
     * - Support team: Manual intervention routing
     * 
     * @param request Reversal failure notification request
     * @return Notification response with delivery status
     */
    @PostMapping("/api/notifications/reversal-failure")
    @CircuitBreaker(name = "notification-service", fallbackMethod = "sendReversalFailureNotificationFallback")
    @Retry(name = "notification-service")
    @TimeLimiter(name = "notification-service")
    ResponseEntity<NotificationResponse> sendReversalFailureNotification(@Valid @RequestBody ReversalFailureNotificationRequest request);
    
    /**
     * Send payment refund status notification
     * 
     * Notifies customers of refund progress:
     * - Refund initiated and processing
     * - Refund completed with expected arrival
     * - Refund failed with next steps
     * - Refund cancelled
     * 
     * @param request Refund status notification request
     * @return Notification response with delivery status
     */
    @PostMapping("/api/notifications/refund-status")
    @CircuitBreaker(name = "notification-service", fallbackMethod = "sendRefundStatusNotificationFallback")
    @Retry(name = "notification-service")
    @TimeLimiter(name = "notification-service")
    ResponseEntity<NotificationResponse> sendRefundStatusNotification(@Valid @RequestBody RefundStatusNotificationRequest request);
    
    /**
     * Send payment reconciliation status notification
     * 
     * Sends alerts for reconciliation outcomes:
     * - Finance team: Completion and discrepancy alerts
     * - Operations: Failed reconciliation notifications
     * - Management: High-value discrepancy escalations
     * 
     * @param request Reconciliation status notification request
     * @return Notification response with delivery status
     */
    @PostMapping("/api/notifications/reconciliation-status")
    @CircuitBreaker(name = "notification-service", fallbackMethod = "sendReconciliationStatusNotificationFallback")
    @Retry(name = "notification-service")
    @TimeLimiter(name = "notification-service")
    ResponseEntity<NotificationResponse> sendReconciliationStatusNotification(@Valid @RequestBody ReconciliationStatusNotificationRequest request);
}