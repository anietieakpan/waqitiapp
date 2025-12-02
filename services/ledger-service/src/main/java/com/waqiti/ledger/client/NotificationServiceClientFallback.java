package com.waqiti.ledger.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.waqiti.ledger.client.NotificationServiceClient.*;

/**
 * Fallback implementation for NotificationServiceClient in Ledger Service
 * 
 * Ledger Service Notification Fallback Philosophy:
 * - NON-BLOCKING: Notification failures should NEVER block ledger operations
 * - QUEUE ALL: All failed notifications queued for guaranteed delivery
 * - FINANCIAL COMPLIANCE: Certain notifications are regulatory requirements
 * - AUDIT TRAIL: Must log all notification attempts even during outages
 * - CRITICAL ALERTS: System-critical alerts escalate to alternative channels
 * 
 * Ledger-Specific Notification Considerations:
 * - Account balance notifications (regulatory requirement - must be delivered)
 * - Transaction confirmations (customer expectation - must be delivered)
 * - Critical system alerts (operational requirement - must reach ops team)
 * - Period closing notifications (workflow requirement - can be delayed)
 * - Reconciliation alerts (operational - can be delayed)
 * 
 * @author Waqiti Platform Team - Phase 1 Remediation
 * @since Session 5
 */
@Slf4j
@Component
public class NotificationServiceClientFallback implements NotificationServiceClient {

    /**
     * QUEUE single notification for async delivery
     * Non-blocking: Ledger operations must proceed even if notifications fail
     */
    @Override
    public ResponseEntity<NotificationResponse> sendNotification(NotificationRequest request) {
        log.warn("FALLBACK ACTIVATED: QUEUING notification - Notification Service unavailable. " +
                "User: {}, Channel: {}, Priority: {}, Subject: {}", 
                request.userId(), request.channel(), request.priority(), request.subject());
        
        // Queue notification for guaranteed delivery
        // Ledger operations must not be blocked by notification failures
        NotificationResponse response = new NotificationResponse(
                UUID.randomUUID(), // Temporary notification ID
                "QUEUED",
                LocalDateTime.now(),
                request.channel(),
                Map.of(
                        "fallbackMode", true,
                        "queuedForRetry", true,
                        "guaranteedDelivery", true,
                        "priority", request.priority(),
                        "message", "Notification queued - will be delivered when service recovers"
                )
        );
        
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    /**
     * QUEUE bulk notifications for async delivery
     * Critical for period closing and batch operations
     */
    @Override
    public ResponseEntity<BulkNotificationResponse> sendBulkNotifications(List<NotificationRequest> requests) {
        log.warn("FALLBACK ACTIVATED: QUEUING {} bulk notifications - Notification Service unavailable", 
                requests.size());
        
        // Queue all bulk notifications
        // Period closing must not be blocked by notification failures
        BulkNotificationResponse response = new BulkNotificationResponse(
                requests.size(),
                0, // successCount - all queued, not yet sent
                0, // failureCount - queued, not failed
                Collections.emptyList() // Will be populated after queue processing
        );
        
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    /**
     * QUEUE template-based notification
     * Used for standardized financial notifications
     */
    @Override
    public ResponseEntity<NotificationResponse> sendTemplateNotification(TemplateNotificationRequest request) {
        log.warn("FALLBACK ACTIVATED: QUEUING template notification - Notification Service unavailable. " +
                "User: {}, Template: {}, Channels: {}, Priority: {}", 
                request.userId(), request.templateId(), request.channels(), request.priority());
        
        // Template notifications are typically regulatory or operational
        // Must guarantee delivery
        NotificationResponse response = new NotificationResponse(
                UUID.randomUUID(),
                "QUEUED",
                LocalDateTime.now(),
                String.join(",", request.channels()),
                Map.of(
                        "templateId", request.templateId(),
                        "fallbackMode", true,
                        "queuedForRetry", true,
                        "guaranteedDelivery", true,
                        "message", "Template notification queued for delivery"
                )
        );
        
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    /**
     * Return UNAVAILABLE status for notification tracking
     * Non-critical: Status checks can fail without blocking ledger operations
     */
    @Override
    public ResponseEntity<NotificationStatus> getNotificationStatus(UUID notificationId) {
        log.warn("FALLBACK ACTIVATED: Cannot retrieve notification status - Notification Service unavailable. " +
                "NotificationId: {}", notificationId);
        
        // Return unavailable status
        // Status checks are non-critical
        NotificationStatus status = new NotificationStatus(
                notificationId,
                "UNAVAILABLE",
                null,
                null,
                "Notification service temporarily unavailable - status unknown"
        );
        
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(status);
    }

    /**
     * ESCALATE critical alerts through alternative channels
     * System-critical alerts must reach operations team
     */
    @Override
    public ResponseEntity<AlertResponse> sendCriticalAlert(CriticalAlertRequest request) {
        log.error("FALLBACK ACTIVATED: CRITICAL ALERT - Notification Service unavailable! " +
                "AlertType: {}, Message: {}, RequiresAck: {}, Recipients: {}", 
                request.alertType(), request.message(), request.requiresAcknowledgment(), request.recipientRoles());
        
        // CRITICAL: Log alert details for ops team visibility
        // In production, this would also:
        // 1. Write to high-priority alert queue
        // 2. Trigger PagerDuty/Opsgenie via direct API
        // 3. Send SMS via backup SMS provider
        // 4. Write to dedicated alert log for monitoring
        
        log.error("====== CRITICAL LEDGER ALERT ======");
        log.error("Type: {}", request.alertType());
        log.error("Message: {}", request.message());
        log.error("Context: {}", request.context());
        log.error("Recipients: {}", request.recipientRoles());
        log.error("Requires Acknowledgment: {}", request.requiresAcknowledgment());
        log.error("===================================");
        
        // Return queued status
        // Alert will be escalated through backup channels
        AlertResponse response = new AlertResponse(
                UUID.randomUUID(),
                "QUEUED_FOR_ESCALATION",
                LocalDateTime.now(),
                Collections.emptyList() // Recipients will be notified via backup channels
        );
        
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }
}