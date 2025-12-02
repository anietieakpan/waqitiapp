package com.waqiti.transaction.service;

import com.waqiti.common.client.NotificationServiceClient;
import com.waqiti.common.notification.model.*;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {
    
    private final NotificationServiceClient notificationClient;
    
    @CircuitBreaker(name = "notification-service", fallbackMethod = "sendUrgentBlockNotificationFallback")
    @Retry(name = "notification-service")
    public void sendUrgentBlockNotification(Object request, Object result) {
        log.info("Sending urgent block notification for: {}", request);
        
        try {
            CriticalAlertRequest alertRequest = CriticalAlertRequest.builder()
                    .title("Urgent Transaction Block")
                    .message("An urgent transaction block has been applied: " + request.toString())
                    .severity(CriticalAlertRequest.Severity.HIGH)
                    .category("TRANSACTION_BLOCK")
                    .metadata(Map.of("request", request, "result", result))
                    .build();
            
            notificationClient.sendCriticalAlert(alertRequest);
            log.info("Successfully sent urgent block notification");
            
        } catch (Exception e) {
            log.error("Failed to send urgent block notification", e);
            sendUrgentBlockNotificationFallback(request, result, e);
        }
    }
    
    @CircuitBreaker(name = "notification-service", fallbackMethod = "sendCriticalBlockAlertFallback")
    @Retry(name = "notification-service")
    public void sendCriticalBlockAlert(Map<String, Object> notificationData) {
        log.warn("Sending critical block alert: {}", notificationData);
        
        try {
            CriticalAlertRequest alertRequest = CriticalAlertRequest.builder()
                    .title("CRITICAL: Transaction Block Applied")
                    .message(String.format("Critical block: %s on %s %s", 
                            notificationData.get("blockType"),
                            notificationData.get("targetType"),
                            notificationData.get("targetId")))
                    .severity(CriticalAlertRequest.Severity.CRITICAL)
                    .category("CRITICAL_TRANSACTION_BLOCK")
                    .metadata(notificationData)
                    .build();
            
            notificationClient.sendCriticalAlert(alertRequest);
            log.info("Successfully sent critical block alert");
            
        } catch (Exception e) {
            log.error("Failed to send critical block alert", e);
            sendCriticalBlockAlertFallback(notificationData, e);
        }
    }
    
    @CircuitBreaker(name = "notification-service", fallbackMethod = "sendExecutiveAlertFallback")
    @Retry(name = "notification-service")
    public void sendExecutiveAlert(String alertType, Map<String, Object> notificationData) {
        log.warn("Sending executive alert: {} data: {}", alertType, notificationData);
        
        try {
            CriticalAlertRequest alertRequest = CriticalAlertRequest.builder()
                    .title("Executive Alert: " + alertType)
                    .message("Executive attention required for: " + alertType)
                    .severity(CriticalAlertRequest.Severity.CRITICAL)
                    .category("EXECUTIVE_ALERT")
                    .metadata(notificationData)
                    .requiresAcknowledgment(true)
                    .build();
            
            notificationClient.sendCriticalAlert(alertRequest);
            log.info("Successfully sent executive alert");
            
        } catch (Exception e) {
            log.error("Failed to send executive alert", e);
            sendExecutiveAlertFallback(alertType, notificationData, e);
        }
    }
    
    @CircuitBreaker(name = "notification-service", fallbackMethod = "sendBlockNotificationFallback")
    @Retry(name = "notification-service")
    public void sendBlockNotification(Object targetType, String targetId, Map<String, Object> notificationData) {
        log.info("Sending block notification to: {} {}", targetType, targetId);
        
        try {
            InAppNotificationRequest notification = InAppNotificationRequest.builder()
                    .userId(targetId)
                    .title("Transaction Block Notification")
                    .message(String.format("A block has been applied: %s", 
                            notificationData.get("blockReason")))
                    .priority(InAppNotificationRequest.Priority.HIGH)
                    .category("TRANSACTION_BLOCK")
                    .data(notificationData)
                    .build();
            
            notificationClient.sendInAppNotification(notification);
            log.info("Successfully sent block notification");
            
        } catch (Exception e) {
            log.error("Failed to send block notification", e);
            sendBlockNotificationFallback(targetType, targetId, notificationData, e);
        }
    }
    
    @CircuitBreaker(name = "notification-service", fallbackMethod = "sendOperationsNotificationFallback")
    @Retry(name = "notification-service")
    public void sendOperationsNotification(String operationType, Map<String, Object> notificationData) {
        log.info("Sending operations notification: {} data: {}", operationType, notificationData);
        
        try {
            EmailNotificationRequest emailRequest = EmailNotificationRequest.builder()
                    .to(java.util.List.of("operations@example.com"))
                    .subject("Transaction Block Operation: " + operationType)
                    .htmlContent(buildOperationsEmailContent(operationType, notificationData))
                    .priority(EmailNotificationRequest.Priority.HIGH)
                    .build();
            
            notificationClient.sendEmailNotification(emailRequest);
            log.info("Successfully sent operations notification");
            
        } catch (Exception e) {
            log.error("Failed to send operations notification", e);
            sendOperationsNotificationFallback(operationType, notificationData, e);
        }
    }
    
    private String buildOperationsEmailContent(String operationType, Map<String, Object> data) {
        StringBuilder content = new StringBuilder();
        content.append("<h2>Transaction Block Operation: ").append(operationType).append("</h2>");
        content.append("<h3>Details:</h3><ul>");
        data.forEach((key, value) -> 
                content.append("<li><strong>").append(key).append(":</strong> ").append(value).append("</li>"));
        content.append("</ul>");
        return content.toString();
    }
    
    private void sendUrgentBlockNotificationFallback(Object request, Object result, Exception e) {
        log.error("Notification service unavailable - urgent block notification not sent (fallback)");
    }
    
    private void sendCriticalBlockAlertFallback(Map<String, Object> notificationData, Exception e) {
        log.error("Notification service unavailable - critical block alert not sent (fallback)");
    }
    
    private void sendExecutiveAlertFallback(String alertType, Map<String, Object> notificationData, Exception e) {
        log.error("Notification service unavailable - executive alert not sent (fallback): {}", alertType);
    }
    
    private void sendBlockNotificationFallback(Object targetType, String targetId, 
                                             Map<String, Object> notificationData, Exception e) {
        log.error("Notification service unavailable - block notification not sent (fallback): {}", targetId);
    }
    
    private void sendOperationsNotificationFallback(String operationType, 
                                                  Map<String, Object> notificationData, Exception e) {
        log.error("Notification service unavailable - operations notification not sent (fallback): {}", operationType);
    }
}