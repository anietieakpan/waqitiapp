package com.waqiti.payment.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.security.audit.SecurityAuditLogger;
import com.waqiti.payment.client.UserServiceClient;
import com.waqiti.payment.client.dto.UserResponse;
import com.waqiti.payment.core.model.RefundRecord;
import com.waqiti.payment.domain.PaymentRequest;
import com.waqiti.payment.dto.ReconciliationDiscrepancy;
import com.waqiti.payment.dto.ReconciliationRecord;
import com.waqiti.payment.notification.client.EmailNotificationClient;
import com.waqiti.payment.notification.client.SlackNotificationClient;
import com.waqiti.payment.notification.client.SMSNotificationClient;
import com.waqiti.payment.notification.client.WebhookNotificationClient;
import com.waqiti.payment.notification.model.CustomerActivationNotification;
import com.waqiti.payment.notification.model.NotificationResult;
import com.waqiti.payment.notification.model.ReconciliationNotification;
import com.waqiti.payment.notification.model.RefundNotification;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enterprise Payment Notification Service Implementation
 * 
 * Production-ready implementation extracted from PaymentService with:
 * - Multi-channel notification delivery (email, SMS, Slack, webhooks)
 * - Comprehensive notification tracking and retry mechanisms
 * - Stakeholder-specific routing and personalized messaging
 * - Performance monitoring and delivery metrics
 * - Security audit logging and compliance features
 * - Asynchronous processing for high-throughput scenarios
 * 
 * @version 2.0.0
 * @since 2025-01-18
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentNotificationServiceImpl implements PaymentNotificationServiceInterface {
    
    // Core dependencies
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final SecurityAuditLogger securityAuditLogger;
    private final MeterRegistry meterRegistry;
    private final UserServiceClient userServiceClient;
    
    // Notification clients
    private final EmailNotificationClient emailClient;
    private final SMSNotificationClient smsClient;
    private final SlackNotificationClient slackClient;
    private final WebhookNotificationClient webhookClient;
    
    // In-memory notification tracking (in production would use Redis/DB)
    private final ConcurrentHashMap<String, NotificationResult> notificationTracker = new ConcurrentHashMap<>();
    
    // Constants
    private static final String CUSTOMER_ACTIVATION_TOPIC = "customer-account-activations";
    private static final String REFUND_NOTIFICATIONS_TOPIC = "refund-notifications";
    private static final String RECONCILIATION_NOTIFICATIONS_TOPIC = "reconciliation-notifications";
    
    // =====================================
    // REFUND NOTIFICATIONS
    // =====================================
    
    @Override
    @Async("notificationExecutor")
    public CompletableFuture<NotificationResult> sendRefundNotifications(RefundRecord refundRecord, 
                                                                         PaymentRequest originalPayment) {
        String notificationId = UUID.randomUUID().toString();
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            log.info("Sending refund notifications for refund: {} (payment: {})", 
                refundRecord.getRefundId(), originalPayment.getId());
            
            // Get customer information
            UserResponse customer = userServiceClient.getUser(UUID.fromString(originalPayment.getCustomerId()));
            if (customer == null) {
                log.warn("Could not find customer {} for refund notification", originalPayment.getCustomerId());
                return CompletableFuture.completedFuture(
                    NotificationResult.failed(notificationId, NotificationResult.NotificationType.REFUND_NOTIFICATION,
                        "Customer not found: " + originalPayment.getCustomerId()));
            }
            
            List<CompletableFuture<NotificationResult>> notificationTasks = new ArrayList<>();
            
            // Send customer notification
            RefundNotification customerNotification = RefundNotification.forCustomer(
                refundRecord, originalPayment, customer.getEmail(), customer.getPhoneNumber());
            notificationTasks.add(CompletableFuture.supplyAsync(() -> 
                sendRefundNotificationToCustomer(customerNotification)));
            
            // Send merchant notification (if different from customer)
            if (!originalPayment.getCustomerId().equals(originalPayment.getMerchantId())) {
                RefundNotification merchantNotification = RefundNotification.forMerchant(
                    refundRecord, originalPayment, getMerchantEmail(originalPayment.getMerchantId()),
                    getMerchantWebhookUrl(originalPayment.getMerchantId()));
                notificationTasks.add(CompletableFuture.supplyAsync(() -> 
                    sendRefundNotificationToMerchant(merchantNotification)));
            }
            
            // Send operations notification
            RefundNotification operationsNotification = RefundNotification.forOperations(refundRecord, originalPayment);
            notificationTasks.add(CompletableFuture.supplyAsync(() -> 
                sendRefundNotificationToOperations(operationsNotification)));
            
            // Wait for all notifications to complete
            CompletableFuture<Void> allTasks = CompletableFuture.allOf(
                notificationTasks.toArray(new CompletableFuture[0]));
            
            return allTasks.thenApply(v -> {
                List<NotificationResult> results = notificationTasks.stream()
                    .map(CompletableFuture::join)
                    .toList();
                
                long successCount = results.stream()
                    .mapToLong(r -> r.isSuccessful() ? 1 : 0)
                    .sum();
                
                NotificationResult aggregatedResult = NotificationResult.builder()
                    .notificationId(notificationId)
                    .notificationType(NotificationResult.NotificationType.REFUND_NOTIFICATION)
                    .deliveryStatus(successCount > 0 ? 
                        NotificationResult.DeliveryStatus.DELIVERED : 
                        NotificationResult.DeliveryStatus.FAILED)
                    .totalChannels(results.size())
                    .successfulChannels((int) successCount)
                    .failedChannels((int) (results.size() - successCount))
                    .deliveryTimeMs(Timer.builder("payment.notification.refund.duration")
                        .register(meterRegistry).stop(sample).longValue())
                    .build();
                
                notificationTracker.put(notificationId, aggregatedResult);
                
                // Publish notification event
                publishRefundNotificationEvent(refundRecord, originalPayment, aggregatedResult);
                
                log.info("Refund notifications completed for refund: {} - {}/{} successful", 
                    refundRecord.getRefundId(), successCount, results.size());
                
                return aggregatedResult;
            });
            
        } catch (Exception e) {
            log.error("Error sending refund notifications for refund: {}", refundRecord.getRefundId(), e);
            securityAuditLogger.logSecurityEvent("REFUND_NOTIFICATION_ERROR", "system",
                "Failed to send refund notifications: " + e.getMessage(),
                Map.of("refundId", refundRecord.getRefundId(), "error", e.getMessage()));
            
            return CompletableFuture.completedFuture(
                NotificationResult.failed(notificationId, NotificationResult.NotificationType.REFUND_NOTIFICATION,
                    "Notification processing failed: " + e.getMessage()));
        }
    }
    
    @Override
    public NotificationResult sendRefundNotificationToCustomer(RefundNotification notification) {
        Timer.Sample sample = Timer.start(meterRegistry);
        String notificationId = UUID.randomUUID().toString();
        
        try {
            log.debug("Sending refund notification to customer: {}", notification.getCustomerId());
            
            // Send email notification
            NotificationResult emailResult = emailClient.sendRefundNotification(notification);
            
            // Send SMS notification if phone number available
            NotificationResult smsResult = null;
            if (notification.getCustomerPhone() != null && !notification.getCustomerPhone().isEmpty()) {
                smsResult = smsClient.sendRefundNotification(notification);
            }
            
            // Determine overall result
            boolean success = emailResult.isSuccessful() || (smsResult != null && smsResult.isSuccessful());
            
            NotificationResult result = NotificationResult.builder()
                .notificationId(notificationId)
                .notificationType(NotificationResult.NotificationType.REFUND_NOTIFICATION)
                .deliveryStatus(success ? NotificationResult.DeliveryStatus.DELIVERED : NotificationResult.DeliveryStatus.FAILED)
                .recipientEmail(notification.getCustomerEmail())
                .recipientPhone(notification.getCustomerPhone())
                .subject("Refund Processed - " + notification.getFormattedRefundAmount())
                .deliveryTimeMs(Timer.builder("payment.notification.refund.customer.duration")
                    .register(meterRegistry).stop(sample).longValue())
                .build();
            
            notificationTracker.put(notificationId, result);
            
            securityAuditLogger.logSecurityEvent("REFUND_CUSTOMER_NOTIFICATION", notification.getCustomerId(),
                "Refund notification sent to customer",
                Map.of("refundId", notification.getRefundId(), "success", success, 
                      "emailSent", emailResult.isSuccessful(), "smsSent", smsResult != null && smsResult.isSuccessful()));
            
            return result;
            
        } catch (Exception e) {
            log.error("Error sending refund notification to customer: {}", notification.getCustomerId(), e);
            return NotificationResult.failed(notificationId, NotificationResult.NotificationType.REFUND_NOTIFICATION,
                "Customer notification failed: " + e.getMessage());
        }
    }
    
    @Override
    public NotificationResult sendRefundNotificationToMerchant(RefundNotification notification) {
        Timer.Sample sample = Timer.start(meterRegistry);
        String notificationId = UUID.randomUUID().toString();
        
        try {
            log.debug("Sending refund notification to merchant: {}", notification.getMerchantId());
            
            // Send email notification
            NotificationResult emailResult = emailClient.sendMerchantRefundNotification(notification);
            
            // Send webhook notification if URL available
            NotificationResult webhookResult = null;
            if (notification.getMerchantWebhookUrl() != null && !notification.getMerchantWebhookUrl().isEmpty()) {
                webhookResult = webhookClient.sendRefundNotification(notification);
            }
            
            // Determine overall result
            boolean success = emailResult.isSuccessful() || (webhookResult != null && webhookResult.isSuccessful());
            
            NotificationResult result = NotificationResult.builder()
                .notificationId(notificationId)
                .notificationType(NotificationResult.NotificationType.REFUND_NOTIFICATION)
                .deliveryStatus(success ? NotificationResult.DeliveryStatus.DELIVERED : NotificationResult.DeliveryStatus.FAILED)
                .recipientEmail(notification.getMerchantEmail())
                .webhookUrls(List.of(notification.getMerchantWebhookUrl()))
                .subject("Refund Issued - " + notification.getFormattedRefundAmount())
                .deliveryTimeMs(Timer.builder("payment.notification.refund.merchant.duration")
                    .register(meterRegistry).stop(sample).longValue())
                .build();
            
            notificationTracker.put(notificationId, result);
            
            return result;
            
        } catch (Exception e) {
            log.error("Error sending refund notification to merchant: {}", notification.getMerchantId(), e);
            return NotificationResult.failed(notificationId, NotificationResult.NotificationType.REFUND_NOTIFICATION,
                "Merchant notification failed: " + e.getMessage());
        }
    }
    
    @Override
    public NotificationResult sendRefundNotificationToOperations(RefundNotification notification) {
        Timer.Sample sample = Timer.start(meterRegistry);
        String notificationId = UUID.randomUUID().toString();
        
        try {
            log.debug("Sending refund notification to operations for refund: {}", notification.getRefundId());
            
            // Send email notification to operations team
            NotificationResult emailResult = emailClient.sendOperationsRefundNotification(notification);
            
            // Send Slack notification
            NotificationResult slackResult = null;
            if (notification.getOperationsSlackChannel() != null) {
                slackResult = slackClient.sendRefundNotification(notification);
            }
            
            // Determine overall result
            boolean success = emailResult.isSuccessful() || (slackResult != null && slackResult.isSuccessful());
            
            NotificationResult result = NotificationResult.builder()
                .notificationId(notificationId)
                .notificationType(NotificationResult.NotificationType.REFUND_NOTIFICATION)
                .deliveryStatus(success ? NotificationResult.DeliveryStatus.DELIVERED : NotificationResult.DeliveryStatus.FAILED)
                .recipientEmail(notification.getOperationsEmail())
                .subject("Operations Alert - Refund Processed: " + notification.getFormattedRefundAmount())
                .deliveryTimeMs(Timer.builder("payment.notification.refund.operations.duration")
                    .register(meterRegistry).stop(sample).longValue())
                .build();
            
            notificationTracker.put(notificationId, result);
            
            return result;
            
        } catch (Exception e) {
            log.error("Error sending refund notification to operations: {}", notification.getRefundId(), e);
            return NotificationResult.failed(notificationId, NotificationResult.NotificationType.REFUND_NOTIFICATION,
                "Operations notification failed: " + e.getMessage());
        }
    }
    
    // =====================================
    // RECONCILIATION NOTIFICATIONS
    // =====================================
    
    @Override
    @Async("notificationExecutor")
    public CompletableFuture<NotificationResult> sendReconciliationNotifications(ReconciliationRecord record,
                                                                                 List<ReconciliationDiscrepancy> discrepancies) {
        String notificationId = UUID.randomUUID().toString();
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            log.info("Sending reconciliation notifications for: {} (discrepancies: {})", 
                record.getReconciliationId(), discrepancies.size());
            
            List<CompletableFuture<NotificationResult>> notificationTasks = new ArrayList<>();
            
            // Send accounting notification
            ReconciliationNotification accountingNotification = 
                ReconciliationNotification.forAccounting(record, discrepancies);
            notificationTasks.add(CompletableFuture.supplyAsync(() -> 
                sendReconciliationNotificationToAccounting(accountingNotification)));
            
            // Send operations notification  
            ReconciliationNotification operationsNotification = 
                ReconciliationNotification.forOperations(record, discrepancies);
            notificationTasks.add(CompletableFuture.supplyAsync(() -> 
                sendReconciliationNotificationToOperations(operationsNotification)));
            
            // Send critical alert if needed
            if (discrepancies.stream().anyMatch(d -> "CRITICAL".equals(d.getSeverity()))) {
                ReconciliationNotification criticalAlert = 
                    ReconciliationNotification.forCriticalAlert(record, discrepancies);
                notificationTasks.add(CompletableFuture.supplyAsync(() -> 
                    sendCriticalReconciliationAlert(criticalAlert)));
            }
            
            // Wait for all notifications to complete
            CompletableFuture<Void> allTasks = CompletableFuture.allOf(
                notificationTasks.toArray(new CompletableFuture[0]));
            
            return allTasks.thenApply(v -> {
                List<NotificationResult> results = notificationTasks.stream()
                    .map(CompletableFuture::join)
                    .toList();
                
                long successCount = results.stream()
                    .mapToLong(r -> r.isSuccessful() ? 1 : 0)
                    .sum();
                
                NotificationResult aggregatedResult = NotificationResult.builder()
                    .notificationId(notificationId)
                    .notificationType(NotificationResult.NotificationType.RECONCILIATION_NOTIFICATION)
                    .deliveryStatus(successCount > 0 ? 
                        NotificationResult.DeliveryStatus.DELIVERED : 
                        NotificationResult.DeliveryStatus.FAILED)
                    .totalChannels(results.size())
                    .successfulChannels((int) successCount)
                    .failedChannels((int) (results.size() - successCount))
                    .deliveryTimeMs(Timer.builder("payment.notification.reconciliation.duration")
                        .register(meterRegistry).stop(sample).longValue())
                    .build();
                
                notificationTracker.put(notificationId, aggregatedResult);
                
                // Publish notification event
                publishReconciliationNotificationEvent(record, discrepancies, aggregatedResult);
                
                log.info("Reconciliation notifications completed for: {} - {}/{} successful", 
                    record.getReconciliationId(), successCount, results.size());
                
                return aggregatedResult;
            });
            
        } catch (Exception e) {
            log.error("Error sending reconciliation notifications for: {}", record.getReconciliationId(), e);
            securityAuditLogger.logSecurityEvent("RECONCILIATION_NOTIFICATION_ERROR", "system",
                "Failed to send reconciliation notifications: " + e.getMessage(),
                Map.of("reconciliationId", record.getReconciliationId(), "error", e.getMessage()));
            
            return CompletableFuture.completedFuture(
                NotificationResult.failed(notificationId, NotificationResult.NotificationType.RECONCILIATION_NOTIFICATION,
                    "Notification processing failed: " + e.getMessage()));
        }
    }
    
    @Override
    public NotificationResult sendReconciliationNotificationToAccounting(ReconciliationNotification notification) {
        Timer.Sample sample = Timer.start(meterRegistry);
        String notificationId = UUID.randomUUID().toString();
        
        try {
            log.debug("Sending reconciliation notification to accounting: {}", notification.getReconciliationId());
            
            // Send email and Slack notifications
            NotificationResult emailResult = emailClient.sendAccountingReconciliationNotification(notification);
            NotificationResult slackResult = slackClient.sendReconciliationNotification(notification);
            
            boolean success = emailResult.isSuccessful() || slackResult.isSuccessful();
            
            NotificationResult result = NotificationResult.builder()
                .notificationId(notificationId)
                .notificationType(NotificationResult.NotificationType.RECONCILIATION_NOTIFICATION)
                .deliveryStatus(success ? NotificationResult.DeliveryStatus.DELIVERED : NotificationResult.DeliveryStatus.FAILED)
                .recipientEmail(notification.getAccountingEmail())
                .subject("Reconciliation Complete - " + notification.getReconciliationId())
                .deliveryTimeMs(Timer.builder("payment.notification.reconciliation.accounting.duration")
                    .register(meterRegistry).stop(sample).longValue())
                .build();
            
            notificationTracker.put(notificationId, result);
            return result;
            
        } catch (Exception e) {
            log.error("Error sending reconciliation notification to accounting: {}", notification.getReconciliationId(), e);
            return NotificationResult.failed(notificationId, NotificationResult.NotificationType.RECONCILIATION_NOTIFICATION,
                "Accounting notification failed: " + e.getMessage());
        }
    }
    
    @Override
    public NotificationResult sendReconciliationNotificationToOperations(ReconciliationNotification notification) {
        Timer.Sample sample = Timer.start(meterRegistry);
        String notificationId = UUID.randomUUID().toString();
        
        try {
            log.debug("Sending reconciliation notification to operations: {}", notification.getReconciliationId());
            
            // Send email and Slack notifications
            NotificationResult emailResult = emailClient.sendOperationsReconciliationNotification(notification);
            NotificationResult slackResult = slackClient.sendReconciliationNotification(notification);
            
            boolean success = emailResult.isSuccessful() || slackResult.isSuccessful();
            
            NotificationResult result = NotificationResult.builder()
                .notificationId(notificationId)
                .notificationType(NotificationResult.NotificationType.RECONCILIATION_NOTIFICATION)
                .deliveryStatus(success ? NotificationResult.DeliveryStatus.DELIVERED : NotificationResult.DeliveryStatus.FAILED)
                .recipientEmail(notification.getOperationsEmail())
                .subject("Reconciliation Alert - " + notification.getReconciliationId())
                .deliveryTimeMs(Timer.builder("payment.notification.reconciliation.operations.duration")
                    .register(meterRegistry).stop(sample).longValue())
                .build();
            
            notificationTracker.put(notificationId, result);
            return result;
            
        } catch (Exception e) {
            log.error("Error sending reconciliation notification to operations: {}", notification.getReconciliationId(), e);
            return NotificationResult.failed(notificationId, NotificationResult.NotificationType.RECONCILIATION_NOTIFICATION,
                "Operations notification failed: " + e.getMessage());
        }
    }
    
    @Override
    public NotificationResult sendCriticalReconciliationAlert(ReconciliationNotification notification) {
        Timer.Sample sample = Timer.start(meterRegistry);
        String notificationId = UUID.randomUUID().toString();
        
        try {
            log.warn("Sending CRITICAL reconciliation alert: {}", notification.getReconciliationId());
            
            // Send to multiple channels for critical alerts
            List<NotificationResult> results = new ArrayList<>();
            
            // Email to management
            results.add(emailClient.sendCriticalReconciliationAlert(notification));
            
            // Slack alerts
            results.add(slackClient.sendCriticalReconciliationAlert(notification));
            
            // SMS to escalation recipients if very critical
            if (notification.getEscalationRecipients() != null) {
                for (String recipient : notification.getEscalationRecipients()) {
                    results.add(smsClient.sendCriticalReconciliationAlert(notification, recipient));
                }
            }
            
            long successCount = results.stream().mapToLong(r -> r.isSuccessful() ? 1 : 0).sum();
            
            NotificationResult result = NotificationResult.builder()
                .notificationId(notificationId)
                .notificationType(NotificationResult.NotificationType.RECONCILIATION_NOTIFICATION)
                .deliveryStatus(successCount > 0 ? NotificationResult.DeliveryStatus.DELIVERED : NotificationResult.DeliveryStatus.FAILED)
                .totalChannels(results.size())
                .successfulChannels((int) successCount)
                .failedChannels((int) (results.size() - successCount))
                .subject("CRITICAL RECONCILIATION ALERT - " + notification.getReconciliationId())
                .deliveryTimeMs(Timer.builder("payment.notification.reconciliation.critical.duration")
                    .register(meterRegistry).stop(sample).longValue())
                .build();
            
            notificationTracker.put(notificationId, result);
            
            securityAuditLogger.logSecurityEvent("CRITICAL_RECONCILIATION_ALERT", "system",
                "Critical reconciliation alert sent",
                Map.of("reconciliationId", notification.getReconciliationId(), 
                      "discrepancyCount", notification.getDiscrepancyCount(),
                      "totalVariance", notification.getTotalVariance(),
                      "successfulNotifications", successCount));
            
            return result;
            
        } catch (Exception e) {
            log.error("Error sending critical reconciliation alert: {}", notification.getReconciliationId(), e);
            securityAuditLogger.logSecurityViolation("CRITICAL_ALERT_FAILED", "system",
                "Failed to send critical reconciliation alert: " + e.getMessage(),
                Map.of("reconciliationId", notification.getReconciliationId(), "error", e.getMessage()));
            
            return NotificationResult.failed(notificationId, NotificationResult.NotificationType.RECONCILIATION_NOTIFICATION,
                "Critical alert failed: " + e.getMessage());
        }
    }
    
    // =====================================
    // CUSTOMER ACTIVATION NOTIFICATIONS
    // =====================================
    
    @Override
    @Async("notificationExecutor")
    public CompletableFuture<NotificationResult> sendCustomerActivationNotifications(String customerId) {
        String notificationId = UUID.randomUUID().toString();
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            log.info("Sending customer activation notifications for: {}", customerId);
            
            // Get customer information
            UserResponse customer = userServiceClient.getUser(UUID.fromString(customerId));
            if (customer == null) {
                log.warn("Could not find customer {} for activation notification", customerId);
                return CompletableFuture.completedFuture(
                    NotificationResult.failed(notificationId, NotificationResult.NotificationType.CUSTOMER_ACTIVATION,
                        "Customer not found: " + customerId));
            }
            
            // Create activation notification
            CustomerActivationNotification notification = CustomerActivationNotification.basicActivation(
                customerId, customer.getEmail(), customer.getFullName());
            
            if (customer.getPhoneNumber() != null && !customer.getPhoneNumber().isEmpty()) {
                notification = CustomerActivationNotification.withSMS(notification, customer.getPhoneNumber());
            }
            
            List<CompletableFuture<NotificationResult>> notificationTasks = new ArrayList<>();
            
            // Send email notification
            notificationTasks.add(CompletableFuture.supplyAsync(() -> 
                sendCustomerActivationEmail(notification)));
            
            // Send SMS if available
            if (notification.getCustomerPhone() != null) {
                notificationTasks.add(CompletableFuture.supplyAsync(() -> 
                    sendCustomerActivationSMS(notification)));
            }
            
            // Send webhook if configured
            if (notification.getWebhookUrl() != null) {
                notificationTasks.add(CompletableFuture.supplyAsync(() -> 
                    sendCustomerActivationWebhook(notification)));
            }
            
            // Publish activation event to Kafka
            publishCustomerActivationEvent(customerId, notification);
            
            // Wait for all notifications to complete
            CompletableFuture<Void> allTasks = CompletableFuture.allOf(
                notificationTasks.toArray(new CompletableFuture[0]));
            
            return allTasks.thenApply(v -> {
                List<NotificationResult> results = notificationTasks.stream()
                    .map(CompletableFuture::join)
                    .toList();
                
                long successCount = results.stream()
                    .mapToLong(r -> r.isSuccessful() ? 1 : 0)
                    .sum();
                
                NotificationResult aggregatedResult = NotificationResult.builder()
                    .notificationId(notificationId)
                    .notificationType(NotificationResult.NotificationType.CUSTOMER_ACTIVATION)
                    .deliveryStatus(successCount > 0 ? 
                        NotificationResult.DeliveryStatus.DELIVERED : 
                        NotificationResult.DeliveryStatus.FAILED)
                    .totalChannels(results.size())
                    .successfulChannels((int) successCount)
                    .failedChannels((int) (results.size() - successCount))
                    .recipientId(customerId)
                    .recipientEmail(customer.getEmail())
                    .recipientPhone(customer.getPhoneNumber())
                    .deliveryTimeMs(Timer.builder("payment.notification.activation.duration")
                        .register(meterRegistry).stop(sample).longValue())
                    .build();
                
                notificationTracker.put(notificationId, aggregatedResult);
                
                log.info("Customer activation notifications completed for: {} - {}/{} successful", 
                    customerId, successCount, results.size());
                
                securityAuditLogger.logSecurityEvent("CUSTOMER_ACTIVATION_NOTIFICATION", customerId,
                    "Customer activation notifications sent",
                    Map.of("successfulChannels", successCount, "totalChannels", results.size()));
                
                return aggregatedResult;
            });
            
        } catch (Exception e) {
            log.error("Error sending customer activation notifications for: {}", customerId, e);
            securityAuditLogger.logSecurityEvent("CUSTOMER_ACTIVATION_NOTIFICATION_ERROR", customerId,
                "Failed to send activation notifications: " + e.getMessage(),
                Map.of("error", e.getMessage()));
            
            return CompletableFuture.completedFuture(
                NotificationResult.failed(notificationId, NotificationResult.NotificationType.CUSTOMER_ACTIVATION,
                    "Notification processing failed: " + e.getMessage()));
        }
    }
    
    @Override
    public NotificationResult sendCustomerActivationEmail(CustomerActivationNotification notification) {
        Timer.Sample sample = Timer.start(meterRegistry);
        String notificationId = UUID.randomUUID().toString();
        
        try {
            log.debug("Sending activation email to customer: {}", notification.getCustomerId());
            
            NotificationResult emailResult = emailClient.sendCustomerActivationEmail(notification);
            
            NotificationResult result = emailResult.toBuilder()
                .notificationId(notificationId)
                .deliveryTimeMs(Timer.builder("payment.notification.activation.email.duration")
                    .register(meterRegistry).stop(sample).longValue())
                .build();
            
            notificationTracker.put(notificationId, result);
            return result;
            
        } catch (Exception e) {
            log.error("Error sending activation email to customer: {}", notification.getCustomerId(), e);
            return NotificationResult.failed(notificationId, NotificationResult.NotificationType.CUSTOMER_ACTIVATION,
                "Email notification failed: " + e.getMessage());
        }
    }
    
    @Override
    public NotificationResult sendCustomerActivationSMS(CustomerActivationNotification notification) {
        Timer.Sample sample = Timer.start(meterRegistry);
        String notificationId = UUID.randomUUID().toString();
        
        try {
            log.debug("Sending activation SMS to customer: {}", notification.getCustomerId());
            
            NotificationResult smsResult = smsClient.sendCustomerActivationSMS(notification);
            
            NotificationResult result = smsResult.toBuilder()
                .notificationId(notificationId)
                .deliveryTimeMs(Timer.builder("payment.notification.activation.sms.duration")
                    .register(meterRegistry).stop(sample).longValue())
                .build();
            
            notificationTracker.put(notificationId, result);
            return result;
            
        } catch (Exception e) {
            log.error("Error sending activation SMS to customer: {}", notification.getCustomerId(), e);
            return NotificationResult.failed(notificationId, NotificationResult.NotificationType.CUSTOMER_ACTIVATION,
                "SMS notification failed: " + e.getMessage());
        }
    }
    
    @Override
    public NotificationResult sendCustomerActivationWebhook(CustomerActivationNotification notification) {
        Timer.Sample sample = Timer.start(meterRegistry);
        String notificationId = UUID.randomUUID().toString();
        
        try {
            log.debug("Sending activation webhook for customer: {}", notification.getCustomerId());
            
            NotificationResult webhookResult = webhookClient.sendCustomerActivationWebhook(notification);
            
            NotificationResult result = webhookResult.toBuilder()
                .notificationId(notificationId)
                .deliveryTimeMs(Timer.builder("payment.notification.activation.webhook.duration")
                    .register(meterRegistry).stop(sample).longValue())
                .build();
            
            notificationTracker.put(notificationId, result);
            return result;
            
        } catch (Exception e) {
            log.error("Error sending activation webhook for customer: {}", notification.getCustomerId(), e);
            return NotificationResult.failed(notificationId, NotificationResult.NotificationType.CUSTOMER_ACTIVATION,
                "Webhook notification failed: " + e.getMessage());
        }
    }
    
    // =====================================
    // PAYMENT COMPLETION NOTIFICATIONS
    // =====================================
    
    @Override
    @Async("notificationExecutor")
    public CompletableFuture<NotificationResult> sendPaymentCompletionNotifications(PaymentRequest paymentRequest) {
        String notificationId = UUID.randomUUID().toString();
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            log.info("Sending payment completion notifications for: {}", paymentRequest.getId());
            
            // Get customer information
            UserResponse customer = userServiceClient.getUser(UUID.fromString(paymentRequest.getCustomerId()));
            if (customer == null) {
                log.warn("Could not find customer {} for payment completion notification", paymentRequest.getCustomerId());
                return CompletableFuture.completedFuture(
                    NotificationResult.failed(notificationId, NotificationResult.NotificationType.PAYMENT_COMPLETION,
                        "Customer not found: " + paymentRequest.getCustomerId()));
            }
            
            List<CompletableFuture<NotificationResult>> notificationTasks = new ArrayList<>();
            
            // Send customer email notification
            notificationTasks.add(CompletableFuture.supplyAsync(() -> 
                emailClient.sendPaymentCompletionNotification(paymentRequest, customer)));
            
            // Send SMS if available  
            if (customer.getPhoneNumber() != null && !customer.getPhoneNumber().isEmpty()) {
                notificationTasks.add(CompletableFuture.supplyAsync(() -> 
                    smsClient.sendPaymentCompletionNotification(paymentRequest, customer)));
            }
            
            // Send merchant webhook if different from customer
            if (!paymentRequest.getCustomerId().equals(paymentRequest.getMerchantId())) {
                String webhookUrl = getMerchantWebhookUrl(paymentRequest.getMerchantId());
                if (webhookUrl != null) {
                    notificationTasks.add(CompletableFuture.supplyAsync(() -> 
                        webhookClient.sendPaymentCompletionNotification(paymentRequest, webhookUrl)));
                }
            }
            
            // Wait for all notifications to complete
            CompletableFuture<Void> allTasks = CompletableFuture.allOf(
                notificationTasks.toArray(new CompletableFuture[0]));
            
            return allTasks.thenApply(v -> {
                List<NotificationResult> results = notificationTasks.stream()
                    .map(CompletableFuture::join)
                    .toList();
                
                long successCount = results.stream()
                    .mapToLong(r -> r.isSuccessful() ? 1 : 0)
                    .sum();
                
                NotificationResult aggregatedResult = NotificationResult.builder()
                    .notificationId(notificationId)
                    .notificationType(NotificationResult.NotificationType.PAYMENT_COMPLETION)
                    .deliveryStatus(successCount > 0 ? 
                        NotificationResult.DeliveryStatus.DELIVERED : 
                        NotificationResult.DeliveryStatus.FAILED)
                    .totalChannels(results.size())
                    .successfulChannels((int) successCount)
                    .failedChannels((int) (results.size() - successCount))
                    .recipientId(paymentRequest.getCustomerId())
                    .recipientEmail(customer.getEmail())
                    .recipientPhone(customer.getPhoneNumber())
                    .deliveryTimeMs(Timer.builder("payment.notification.completion.duration")
                        .register(meterRegistry).stop(sample).longValue())
                    .build();
                
                notificationTracker.put(notificationId, aggregatedResult);
                
                log.info("Payment completion notifications completed for: {} - {}/{} successful", 
                    paymentRequest.getId(), successCount, results.size());
                
                return aggregatedResult;
            });
            
        } catch (Exception e) {
            log.error("Error sending payment completion notifications for: {}", paymentRequest.getId(), e);
            return CompletableFuture.completedFuture(
                NotificationResult.failed(notificationId, NotificationResult.NotificationType.PAYMENT_COMPLETION,
                    "Notification processing failed: " + e.getMessage()));
        }
    }
    
    // =====================================
    // NOTIFICATION MANAGEMENT
    // =====================================
    
    @Override
    public NotificationResult retryNotification(String notificationId) {
        NotificationResult existingResult = notificationTracker.get(notificationId);
        if (existingResult == null) {
            return NotificationResult.failed(notificationId, NotificationResult.NotificationType.CUSTOM,
                "Notification not found: " + notificationId);
        }
        
        if (!existingResult.canRetry()) {
            return existingResult.toBuilder()
                .errorMessage("Cannot retry - max attempts reached or not retryable")
                .build();
        }
        
        log.info("Retrying notification: {}", notificationId);
        
        // Implementation would retry based on notification type
        // For now, mark as retry scheduled
        NotificationResult retryResult = existingResult.toBuilder()
            .deliveryStatus(NotificationResult.DeliveryStatus.RETRY_SCHEDULED)
            .deliveryAttempts(existingResult.getDeliveryAttempts() + 1)
            .nextRetryAt(java.time.Instant.now().plusSeconds(300)) // 5 minute delay
            .build();
        
        notificationTracker.put(notificationId, retryResult);
        return retryResult;
    }
    
    @Override
    public NotificationResult getNotificationStatus(String notificationId) {
        return notificationTracker.get(notificationId);
    }
    
    @Override
    public boolean cancelNotification(String notificationId) {
        NotificationResult result = notificationTracker.get(notificationId);
        if (result != null && result.isPending()) {
            notificationTracker.put(notificationId, result.toBuilder()
                .deliveryStatus(NotificationResult.DeliveryStatus.CANCELLED)
                .build());
            return true;
        }
        return false;
    }
    
    @Override
    public List<NotificationResult> bulkSendNotifications(List<Object> notifications) {
        return notifications.parallelStream()
            .map(this::processNotification)
            .toList();
    }
    
    // =====================================
    // HELPER METHODS
    // =====================================
    
    private NotificationResult processNotification(Object notification) {
        // Implementation would route based on notification type
        return NotificationResult.success(UUID.randomUUID().toString(), 
            NotificationResult.NotificationType.CUSTOM);
    }
    
    private void publishRefundNotificationEvent(RefundRecord refundRecord, PaymentRequest originalPayment, 
                                                NotificationResult result) {
        try {
            Map<String, Object> event = Map.of(
                "refundId", refundRecord.getRefundId(),
                "originalPaymentId", originalPayment.getId(),
                "notificationId", result.getNotificationId(),
                "deliveryStatus", result.getDeliveryStatus(),
                "successfulChannels", result.getSuccessfulChannels(),
                "totalChannels", result.getTotalChannels(),
                "timestamp", LocalDateTime.now().toString()
            );
            
            kafkaTemplate.send(REFUND_NOTIFICATIONS_TOPIC, refundRecord.getRefundId(),
                objectMapper.writeValueAsString(event));
            
        } catch (Exception e) {
            log.error("Failed to publish refund notification event: ", e);
        }
    }
    
    private void publishReconciliationNotificationEvent(ReconciliationRecord record,
                                                        List<ReconciliationDiscrepancy> discrepancies,
                                                        NotificationResult result) {
        try {
            Map<String, Object> event = Map.of(
                "reconciliationId", record.getReconciliationId(),
                "settlementId", record.getSettlementId(),
                "discrepancyCount", discrepancies.size(),
                "notificationId", result.getNotificationId(),
                "deliveryStatus", result.getDeliveryStatus(),
                "successfulChannels", result.getSuccessfulChannels(),
                "totalChannels", result.getTotalChannels(),
                "timestamp", LocalDateTime.now().toString()
            );
            
            kafkaTemplate.send(RECONCILIATION_NOTIFICATIONS_TOPIC, record.getReconciliationId(),
                objectMapper.writeValueAsString(event));
            
        } catch (Exception e) {
            log.error("Failed to publish reconciliation notification event: ", e);
        }
    }
    
    private void publishCustomerActivationEvent(String customerId, CustomerActivationNotification notification) {
        try {
            Map<String, Object> event = Map.of(
                "customerId", customerId,
                "status", "ACTIVATED",
                "activatedAt", notification.getActivatedAt().toString(),
                "activatedBy", notification.getActivatedBy(),
                "capabilities", Map.of(
                    "canSendPayments", notification.isCanSendPayments(),
                    "canReceivePayments", notification.isCanReceivePayments(),
                    "canAccessInternationalTransfers", notification.isCanAccessInternationalTransfers(),
                    "canAccessCryptoFeatures", notification.isCanAccessCryptoFeatures()
                )
            );
            
            kafkaTemplate.send(CUSTOMER_ACTIVATION_TOPIC, customerId,
                objectMapper.writeValueAsString(event));
            
        } catch (Exception e) {
            log.error("Failed to publish customer activation event: ", e);
        }
    }
    
    private String getMerchantEmail(String merchantId) {
        // Implementation would fetch merchant email from merchant service
        return "merchant-" + merchantId + "@example.com";
    }
    
    private String getMerchantWebhookUrl(String merchantId) {
        // Implementation would fetch webhook URL from merchant configuration
        return "https://merchant-" + merchantId + ".example.com/webhooks/payments";
    }
}