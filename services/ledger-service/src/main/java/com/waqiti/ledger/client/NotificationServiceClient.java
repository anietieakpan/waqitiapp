package com.waqiti.ledger.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Notification Service Client
 * 
 * Feign client for sending notifications and alerts
 */
@FeignClient(
    name = "notification-service", 
    path = "/api/v1/notifications",
    fallback = NotificationServiceClientFallback.class
)
public interface NotificationServiceClient {
    
    @PostMapping("/send")
    ResponseEntity<NotificationResponse> sendNotification(
        @RequestBody NotificationRequest request
    );
    
    @PostMapping("/send/bulk")
    ResponseEntity<BulkNotificationResponse> sendBulkNotifications(
        @RequestBody List<NotificationRequest> requests
    );
    
    @PostMapping("/send/template")
    ResponseEntity<NotificationResponse> sendTemplateNotification(
        @RequestBody TemplateNotificationRequest request
    );
    
    @GetMapping("/status/{notificationId}")
    ResponseEntity<NotificationStatus> getNotificationStatus(
        @PathVariable UUID notificationId
    );
    
    @PostMapping("/alerts/critical")
    ResponseEntity<AlertResponse> sendCriticalAlert(
        @RequestBody CriticalAlertRequest request
    );
    
    // DTOs for notification operations
    record NotificationRequest(
        UUID userId,
        String channel, // EMAIL, SMS, PUSH, IN_APP
        String subject,
        String message,
        Map<String, Object> metadata,
        String priority // LOW, MEDIUM, HIGH, CRITICAL
    ) {}
    
    record NotificationResponse(
        UUID notificationId,
        String status,
        LocalDateTime sentAt,
        String channel,
        Map<String, Object> details
    ) {}
    
    record BulkNotificationResponse(
        int totalRequested,
        int successCount,
        int failureCount,
        List<NotificationResponse> notifications
    ) {}
    
    record TemplateNotificationRequest(
        UUID userId,
        String templateId,
        Map<String, Object> templateData,
        List<String> channels,
        String priority
    ) {}
    
    record NotificationStatus(
        UUID notificationId,
        String status, // PENDING, SENT, DELIVERED, FAILED, BOUNCED
        LocalDateTime sentAt,
        LocalDateTime deliveredAt,
        String failureReason
    ) {}
    
    record CriticalAlertRequest(
        String alertType,
        String message,
        Map<String, Object> context,
        List<String> recipientRoles,
        boolean requiresAcknowledgment
    ) {}
    
    record AlertResponse(
        UUID alertId,
        String status,
        LocalDateTime createdAt,
        List<String> notifiedUsers
    ) {}
}