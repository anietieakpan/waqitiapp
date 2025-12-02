package com.waqiti.payment.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Service for sending payment-related notifications
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentNotificationService {
    
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    /**
     * Send account control notification to user
     */
    public void sendAccountControlNotification(String userId, String action, String reason, String correlationId) {
        log.info("Sending account control notification to user: {}, action: {}, reason: {}", userId, action, reason);
        
        Map<String, Object> notification = new HashMap<>();
        notification.put("userId", userId);
        notification.put("notificationType", "ACCOUNT_CONTROL");
        notification.put("action", action);
        notification.put("reason", reason);
        notification.put("correlationId", correlationId);
        notification.put("timestamp", Instant.now().toString());
        notification.put("priority", "HIGH");
        notification.put("channels", new String[]{"EMAIL", "SMS", "PUSH"});
        
        String message = buildNotificationMessage(action, reason);
        notification.put("message", message);
        
        kafkaTemplate.send("user-notifications", notification);
        
        log.debug("Account control notification sent for user: {}", userId);
    }
    
    /**
     * Send payment success notification
     */
    public void sendPaymentSuccessNotification(String userId, String transactionId, double amount, String currency) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("userId", userId);
        notification.put("notificationType", "PAYMENT_SUCCESS");
        notification.put("transactionId", transactionId);
        notification.put("amount", amount);
        notification.put("currency", currency);
        notification.put("timestamp", Instant.now().toString());
        notification.put("channels", new String[]{"EMAIL", "PUSH"});
        
        kafkaTemplate.send("user-notifications", notification);
        
        log.debug("Payment success notification sent for transaction: {}", transactionId);
    }
    
    /**
     * Send payment failure notification
     */
    public void sendPaymentFailureNotification(String userId, String transactionId, String failureReason) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("userId", userId);
        notification.put("notificationType", "PAYMENT_FAILURE");
        notification.put("transactionId", transactionId);
        notification.put("failureReason", failureReason);
        notification.put("timestamp", Instant.now().toString());
        notification.put("priority", "HIGH");
        notification.put("channels", new String[]{"EMAIL", "SMS", "PUSH"});
        
        kafkaTemplate.send("user-notifications", notification);
        
        log.debug("Payment failure notification sent for transaction: {}", transactionId);
    }
    
    /**
     * Send refund notification
     */
    public void sendRefundNotification(String userId, String refundId, double amount, String currency) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("userId", userId);
        notification.put("notificationType", "REFUND_PROCESSED");
        notification.put("refundId", refundId);
        notification.put("amount", amount);
        notification.put("currency", currency);
        notification.put("timestamp", Instant.now().toString());
        notification.put("channels", new String[]{"EMAIL", "PUSH"});
        
        kafkaTemplate.send("user-notifications", notification);
        
        log.debug("Refund notification sent for refund: {}", refundId);
    }
    
    /**
     * Build notification message based on action and reason
     */
    private String buildNotificationMessage(String action, String reason) {
        StringBuilder message = new StringBuilder();
        
        switch (action) {
            case "FREEZE_ACCOUNTS" -> message.append("Your account has been temporarily frozen");
            case "SUSPEND_PAYMENTS" -> message.append("Payment services have been temporarily suspended");
            case "RESTRICT_TRANSACTIONS" -> message.append("Your transaction capabilities have been restricted");
            case "BLOCK_CARD_OPERATIONS" -> message.append("Card operations have been blocked");
            default -> message.append("Account control action has been applied");
        }
        
        if (reason != null && !reason.isEmpty()) {
            message.append(" due to: ").append(reason);
        }
        
        message.append(". Please contact support for assistance.");
        
        return message.toString();
    }
    
    /**
     * Send regulatory alert notification
     */
    public void sendRegulatoryAlert(String title, String message, Map<String, Object> metadata) {
        log.warn("Sending regulatory alert: {}", title);
        
        Map<String, Object> alert = new HashMap<>();
        alert.put("alertType", "REGULATORY");
        alert.put("title", title);
        alert.put("message", message);
        alert.put("metadata", metadata);
        alert.put("timestamp", Instant.now().toString());
        alert.put("priority", "CRITICAL");
        alert.put("recipients", new String[]{"compliance@example.com", "legal@example.com"});
        
        kafkaTemplate.send("regulatory-alerts", alert);
        
        log.info("Regulatory alert sent: {}", title);
    }
    
    /**
     * Send critical operational alert
     */
    public void sendCriticalOperationalAlert(String title, String message, Map<String, Object> metadata) {
        log.error("Sending critical operational alert: {}", title);
        
        Map<String, Object> alert = new HashMap<>();
        alert.put("alertType", "OPERATIONAL_CRITICAL");
        alert.put("title", title);
        alert.put("message", message);
        alert.put("metadata", metadata);
        alert.put("timestamp", Instant.now().toString());
        alert.put("severity", "CRITICAL");
        alert.put("requiresImmediateAction", true);
        alert.put("channels", new String[]{"PAGERDUTY", "SLACK", "EMAIL", "SMS"});
        
        kafkaTemplate.send("operational-alerts", alert);
        kafkaTemplate.send("critical-incidents", alert);
        
        log.error("Critical operational alert dispatched: {}", title);
    }
}