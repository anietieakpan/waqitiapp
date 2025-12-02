package com.waqiti.compliance.service;

import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;

import java.util.Map;

/**
 * Notification Service for Compliance
 *
 * Handles sending notifications for compliance events.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationService {

    private final ComplianceNotificationService complianceNotificationService;

    /**
     * Send AML alert notification
     */
    public void sendAMLAlertNotification(String userId, String alertType, String severity, Map<String, Object> details) {
        log.info("Sending AML alert notification: user={}, type={}, severity={}",
                userId, alertType, severity);

        try {
            // Delegate to compliance notification service
            complianceNotificationService.sendAlertNotification(userId, alertType, severity, details);
        } catch (Exception e) {
            log.error("Failed to send AML alert notification: user={}, type={}",
                    userId, alertType, e);
        }
    }

    /**
     * Send compliance notification
     */
    public void sendComplianceNotification(String userId, String notificationType, Map<String, Object> data) {
        log.info("Sending compliance notification: user={}, type={}", userId, notificationType);

        try {
            complianceNotificationService.sendAlertNotification(userId, notificationType, "INFO", data);
        } catch (Exception e) {
            log.error("Failed to send compliance notification: user={}, type={}",
                    userId, notificationType, e);
        }
    }

    /**
     * Send investigation update notification
     */
    public void sendInvestigationUpdateNotification(String userId, String investigationId, String status) {
        log.info("Sending investigation update notification: user={}, investigationId={}, status={}",
                userId, investigationId, status);

        Map<String, Object> details = Map.of(
                "investigationId", investigationId,
                "status", status
        );

        try {
            complianceNotificationService.sendAlertNotification(userId, "INVESTIGATION_UPDATE", "INFO", details);
        } catch (Exception e) {
            log.error("Failed to send investigation update notification: user={}, investigationId={}",
                    userId, investigationId, e);
        }
    }
}
