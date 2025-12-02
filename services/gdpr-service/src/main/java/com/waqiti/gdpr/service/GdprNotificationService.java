package com.waqiti.gdpr.service;

import com.waqiti.common.notification.DlqNotificationAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * GDPR Notification Service
 * Wrapper around DlqNotificationAdapter for GDPR-specific notifications
 * Handles Data Protection Officer alerts, compliance team notifications, and data subject communications
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GdprNotificationService {

    private final DlqNotificationAdapter notificationAdapter;

    /**
     * Send data export completion notification to data subject
     */
    @Async
    public void sendDataExportCompletionNotification(String subjectId, String exportId,
                                                     String requestType, String downloadLink,
                                                     String correlationId) {
        log.info("Sending data export completion notification: subjectId={} exportId={} correlationId={}",
                subjectId, exportId, correlationId);

        Map<String, Object> notificationData = new HashMap<>();
        notificationData.put("alertType", "DATA_EXPORT_COMPLETED");
        notificationData.put("severity", "INFO");
        notificationData.put("subjectId", subjectId);
        notificationData.put("exportId", exportId);
        notificationData.put("requestType", requestType);
        notificationData.put("downloadLink", downloadLink);
        notificationData.put("correlationId", correlationId);
        notificationData.put("timestamp", Instant.now());
        notificationData.put("expiresInDays", 7);

        try {
            notificationAdapter.sendNotification(
                    "GDPR_EXPORT_COMPLETED",
                    "Your Data Export is Ready",
                    String.format("Your GDPR data export (ID: %s) is ready for download. Link: %s",
                            exportId, downloadLink),
                    notificationData,
                    correlationId
            );

            log.info("Data export completion notification sent: subjectId={} exportId={} correlationId={}",
                    subjectId, exportId, correlationId);

        } catch (Exception e) {
            log.error("Failed to send data export completion notification: subjectId={} exportId={} correlationId={}",
                    subjectId, exportId, correlationId, e);
        }
    }

    /**
     * Send manual review notification to data subject
     */
    @Async
    public void sendDataExportManualReviewNotification(String subjectId, String exportId,
                                                       String reviewReason, String correlationId) {
        log.info("Sending manual review notification: subjectId={} exportId={} correlationId={}",
                subjectId, exportId, correlationId);

        Map<String, Object> notificationData = new HashMap<>();
        notificationData.put("alertType", "DATA_EXPORT_MANUAL_REVIEW");
        notificationData.put("severity", "MEDIUM");
        notificationData.put("subjectId", subjectId);
        notificationData.put("exportId", exportId);
        notificationData.put("reviewReason", reviewReason);
        notificationData.put("correlationId", correlationId);
        notificationData.put("timestamp", Instant.now());
        notificationData.put("estimatedCompletionDays", 30);

        try {
            notificationAdapter.sendNotification(
                    "GDPR_EXPORT_MANUAL_REVIEW",
                    "Your Data Export Requires Review",
                    String.format("Your data export request (ID: %s) requires manual review: %s. " +
                                    "We will complete this within 30 days as required by GDPR.",
                            exportId, reviewReason),
                    notificationData,
                    correlationId
            );

            log.info("Manual review notification sent: subjectId={} exportId={} correlationId={}",
                    subjectId, exportId, correlationId);

        } catch (Exception e) {
            log.error("Failed to send manual review notification: subjectId={} exportId={} correlationId={}",
                    subjectId, exportId, correlationId, e);
        }
    }

    /**
     * Send data export failure notification to data subject
     */
    @Async
    public void sendDataExportFailureNotification(String subjectId, String exportId,
                                                  String failureReason, String correlationId) {
        log.error("Sending data export failure notification: subjectId={} exportId={} reason={} correlationId={}",
                subjectId, exportId, failureReason, correlationId);

        Map<String, Object> notificationData = new HashMap<>();
        notificationData.put("alertType", "DATA_EXPORT_FAILED");
        notificationData.put("severity", "HIGH");
        notificationData.put("subjectId", subjectId);
        notificationData.put("exportId", exportId);
        notificationData.put("failureReason", failureReason);
        notificationData.put("correlationId", correlationId);
        notificationData.put("timestamp", Instant.now());
        notificationData.put("supportContact", "dpo@example.com");

        try {
            notificationAdapter.sendNotification(
                    "GDPR_EXPORT_FAILED",
                    "Data Export Request Failed",
                    String.format("Your data export request (ID: %s) encountered an error: %s. " +
                                    "Our Data Protection Officer has been notified and will contact you shortly.",
                            exportId, failureReason),
                    notificationData,
                    correlationId
            );

            log.error("Data export failure notification sent: subjectId={} exportId={} correlationId={}",
                    subjectId, exportId, correlationId);

        } catch (Exception e) {
            log.error("Failed to send data export failure notification: subjectId={} exportId={} correlationId={}",
                    subjectId, exportId, correlationId, e);
        }
    }

    /**
     * Send Data Protection Officer critical alert
     */
    @Async
    public void sendDataProtectionOfficerAlert(String alertType, String message,
                                              Map<String, String> details, String correlationId) {
        log.error("Sending DPO alert: type={} message={} correlationId={}",
                alertType, message, correlationId);

        Map<String, Object> alertData = new HashMap<>();
        alertData.put("alertType", alertType);
        alertData.put("severity", "CRITICAL");
        alertData.put("message", message);
        alertData.put("details", details);
        alertData.put("correlationId", correlationId);
        alertData.put("timestamp", Instant.now());
        alertData.put("requiresImmediateAction", true);
        alertData.put("targetRole", "DATA_PROTECTION_OFFICER");

        try {
            notificationAdapter.sendNotification(
                    "DPO_CRITICAL_ALERT",
                    String.format("DPO Alert: %s", alertType),
                    message,
                    alertData,
                    correlationId
            );

            log.error("DPO alert sent: type={} correlationId={}", alertType, correlationId);

        } catch (Exception e) {
            log.error("CRITICAL: Failed to send DPO alert: type={} correlationId={}",
                    alertType, correlationId, e);
        }
    }

    /**
     * Send GDPR compliance team alert
     */
    @Async
    public void sendGdprTeamAlert(String alertType, String message,
                                 String severity, String correlationId) {
        log.warn("Sending GDPR team alert: type={} severity={} correlationId={}",
                alertType, severity, correlationId);

        Map<String, Object> alertData = new HashMap<>();
        alertData.put("alertType", alertType);
        alertData.put("severity", severity);
        alertData.put("message", message);
        alertData.put("correlationId", correlationId);
        alertData.put("timestamp", Instant.now());
        alertData.put("targetTeam", "GDPR_COMPLIANCE");

        try {
            notificationAdapter.sendNotification(
                    "GDPR_TEAM_ALERT",
                    String.format("GDPR Alert: %s", alertType),
                    message,
                    alertData,
                    correlationId
            );

            log.warn("GDPR team alert sent: type={} correlationId={}", alertType, correlationId);

        } catch (Exception e) {
            log.error("Failed to send GDPR team alert: type={} correlationId={}",
                    alertType, correlationId, e);
        }
    }
}
