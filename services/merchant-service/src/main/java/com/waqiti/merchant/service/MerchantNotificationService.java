package com.waqiti.merchant.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Merchant Notification Service - Handles critical merchant risk notifications
 * 
 * Provides comprehensive notification capabilities for:
 * - Executive alerts for high-risk merchant activities
 * - Risk management team notifications and escalations
 * - Merchant account manager alerts and coordination
 * - Compliance team notifications for violations
 * - Multi-channel notification delivery and tracking
 * - Emergency escalation and crisis management
 * 
 * @author Waqiti Engineering Team
 * @version 2.0.0
 * @since 2024-01-01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MerchantNotificationService {

    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${merchant.notifications.enabled:true}")
    private boolean notificationsEnabled;

    @Value("${merchant.notifications.executive.enabled:true}")
    private boolean executiveNotificationsEnabled;

    @Value("${merchant.notifications.emergency.phone:true}")
    private boolean emergencyPhoneEnabled;

    @Value("${merchant.notifications.retention.days:365}")
    private int notificationRetentionDays;

    /**
     * Sends emergency executive alert for critical merchant risks
     */
    public void sendEmergencyExecutiveAlert(
            String merchantId,
            String riskType,
            String alertReason,
            Double riskScore) {

        if (!notificationsEnabled || !executiveNotificationsEnabled) {
            log.debug("Executive notifications disabled, skipping emergency alert");
            return;
        }

        try {
            log.error("SENDING EMERGENCY EXECUTIVE ALERT - Merchant: {} - Risk: {}", 
                merchantId, riskType);

            // Create emergency alert
            EmergencyAlert alert = EmergencyAlert.builder()
                .alertId(UUID.randomUUID().toString())
                .merchantId(merchantId)
                .riskType(riskType)
                .alertReason(alertReason)
                .riskScore(riskScore)
                .severity("EMERGENCY")
                .sentAt(LocalDateTime.now())
                .build();

            // Send through all available channels for emergency
            sendMultiChannelEmergencyAlert(alert);

            // Track emergency alert
            trackEmergencyAlert(alert);

            // Immediate escalation chain activation
            initiateEmergencyEscalationChain(alert);

            // Schedule immediate follow-up
            scheduleImmediateFollowUp(alert);

            log.error("Emergency executive alert sent for merchant: {}", merchantId);

        } catch (Exception e) {
            log.error("CRITICAL FAILURE: Failed to send emergency executive alert for merchant: {}", 
                merchantId, e);
        }
    }

    /**
     * Sends critical alert to executive team
     */
    public void sendCriticalAlert(
            String merchantId,
            String riskType,
            String alertReason) {

        if (!notificationsEnabled) {
            return;
        }

        try {
            log.warn("Sending critical alert for merchant: {} - Risk: {}", merchantId, riskType);

            CriticalAlert alert = CriticalAlert.builder()
                .alertId(UUID.randomUUID().toString())
                .merchantId(merchantId)
                .riskType(riskType)
                .alertReason(alertReason)
                .severity("CRITICAL")
                .sentAt(LocalDateTime.now())
                .build();

            // Send critical alert through high-priority channels
            sendCriticalAlertNotification(alert);

            // Track critical alert
            trackCriticalAlert(alert);

            // Schedule urgent follow-up
            scheduleUrgentFollowUp(alert);

            log.warn("Critical alert sent for merchant: {}", merchantId);

        } catch (Exception e) {
            log.error("Failed to send critical alert for merchant: {}", merchantId, e);
        }
    }

    /**
     * Notifies merchant of risk alert
     */
    public void notifyMerchantOfRiskAlert(
            String merchantId,
            String riskType,
            String severity,
            String alertReason) {

        if (!notificationsEnabled) {
            return;
        }

        try {
            log.info("Notifying merchant of risk alert: {} - Type: {}", merchantId, riskType);

            MerchantRiskNotification notification = MerchantRiskNotification.builder()
                .notificationId(UUID.randomUUID().toString())
                .merchantId(merchantId)
                .riskType(riskType)
                .severity(severity)
                .alertReason(alertReason)
                .sentAt(LocalDateTime.now())
                .build();

            // Send merchant notification
            sendMerchantRiskNotification(notification);

            // Track merchant notification
            trackMerchantNotification(notification);

            // Schedule merchant follow-up if needed
            if ("HIGH".equals(severity) || "CRITICAL".equals(severity)) {
                scheduleMerchantFollowUp(notification);
            }

            log.info("Merchant risk notification sent: {}", merchantId);

        } catch (Exception e) {
            log.error("Failed to notify merchant of risk alert: {}", merchantId, e);
        }
    }

    /**
     * Notifies risk management team
     */
    public void notifyRiskManagementTeam(
            String merchantId,
            String riskType,
            String severity,
            Double riskScore,
            Double chargebackRatio) {

        if (!notificationsEnabled) {
            return;
        }

        try {
            log.debug("Notifying risk management team for merchant: {}", merchantId);

            RiskManagementNotification notification = RiskManagementNotification.builder()
                .notificationId(UUID.randomUUID().toString())
                .merchantId(merchantId)
                .riskType(riskType)
                .severity(severity)
                .riskScore(riskScore)
                .chargebackRatio(chargebackRatio)
                .sentAt(LocalDateTime.now())
                .build();

            // Send to risk management team
            sendRiskManagementNotification(notification);

            // Track team notification
            trackRiskManagementNotification(notification);

            // Assign to appropriate team member
            assignToRiskAnalyst(notification);

            log.info("Risk management team notified for merchant: {}", merchantId);

        } catch (Exception e) {
            log.error("Failed to notify risk management team for merchant: {}", merchantId, e);
        }
    }

    /**
     * Notifies account managers
     */
    public void notifyAccountManagers(
            String merchantId,
            String riskType,
            String severity,
            BigDecimal processingVolume) {

        if (!notificationsEnabled) {
            return;
        }

        try {
            log.debug("Notifying account managers for merchant: {}", merchantId);

            AccountManagerNotification notification = AccountManagerNotification.builder()
                .notificationId(UUID.randomUUID().toString())
                .merchantId(merchantId)
                .riskType(riskType)
                .severity(severity)
                .processingVolume(processingVolume)
                .sentAt(LocalDateTime.now())
                .build();

            // Send to account management team
            sendAccountManagerNotification(notification);

            // Track account manager notification
            trackAccountManagerNotification(notification);

            // Schedule client outreach if needed
            if ("HIGH".equals(severity) || "CRITICAL".equals(severity)) {
                scheduleClientOutreach(notification);
            }

            log.info("Account managers notified for merchant: {}", merchantId);

        } catch (Exception e) {
            log.error("Failed to notify account managers for merchant: {}", merchantId, e);
        }
    }

    /**
     * Notifies compliance team
     */
    public void notifyComplianceTeam(
            String merchantId,
            String riskType,
            Map<String, String> riskIndicators) {

        if (!notificationsEnabled) {
            return;
        }

        try {
            log.debug("Notifying compliance team for merchant: {}", merchantId);

            ComplianceNotification notification = ComplianceNotification.builder()
                .notificationId(UUID.randomUUID().toString())
                .merchantId(merchantId)
                .riskType(riskType)
                .riskIndicators(riskIndicators)
                .sentAt(LocalDateTime.now())
                .build();

            // Send to compliance team
            sendComplianceNotification(notification);

            // Track compliance notification
            trackComplianceNotification(notification);

            // Schedule compliance review
            scheduleComplianceReview(notification);

            log.info("Compliance team notified for merchant: {}", merchantId);

        } catch (Exception e) {
            log.error("Failed to notify compliance team for merchant: {}", merchantId, e);
        }
    }

    // Helper methods for sending notifications

    private void sendMultiChannelEmergencyAlert(EmergencyAlert alert) {
        try {
            String subject = createEmergencySubject(alert);
            String content = createEmergencyContent(alert);

            // Emergency alerts use ALL channels
            sendEmailNotification(alert.getAlertId(), subject, content, "EMERGENCY_EXECUTIVE");
            sendSMSNotification(alert.getAlertId(), createSMSContent(alert), "EMERGENCY_EXECUTIVE");
            
            if (emergencyPhoneEnabled) {
                initiateEmergencyPhoneCall(alert.getAlertId(), alert.getMerchantId(), alert.getRiskType());
            }
            
            sendPushNotification(alert.getAlertId(), subject, content, "EMERGENCY_EXECUTIVE");
            sendTeamsNotification(alert.getAlertId(), subject, content, "EMERGENCY_EXECUTIVE");

            // Store emergency alert
            storeEmergencyAlert(alert);

        } catch (Exception e) {
            log.error("Failed to send multi-channel emergency alert", e);
        }
    }

    private void sendCriticalAlertNotification(CriticalAlert alert) {
        try {
            String subject = createCriticalSubject(alert);
            String content = createCriticalContent(alert);

            String alertKey = "merchant:notifications:critical:" + alert.getAlertId();
            Map<String, String> alertData = Map.of(
                "alert_id", alert.getAlertId(),
                "merchant_id", alert.getMerchantId(),
                "risk_type", alert.getRiskType(),
                "alert_reason", alert.getAlertReason(),
                "severity", alert.getSeverity(),
                "sent_at", alert.getSentAt().toString()
            );

            redisTemplate.opsForHash().putAll(alertKey, alertData);
            redisTemplate.expire(alertKey, Duration.ofDays(notificationRetentionDays));

            // Send through high-priority channels
            sendEmailNotification(alert.getAlertId(), subject, content, "CRITICAL_EXECUTIVE");
            sendSMSNotification(alert.getAlertId(), subject, "CRITICAL_EXECUTIVE");
            sendTeamsNotification(alert.getAlertId(), subject, content, "CRITICAL_EXECUTIVE");

        } catch (Exception e) {
            log.error("Failed to send critical alert notification", e);
        }
    }

    private void sendMerchantRiskNotification(MerchantRiskNotification notification) {
        try {
            String subject = createMerchantSubject(notification);
            String content = createMerchantContent(notification);

            String notificationKey = "merchant:notifications:merchant:" + notification.getNotificationId();
            Map<String, String> notificationData = Map.of(
                "notification_id", notification.getNotificationId(),
                "merchant_id", notification.getMerchantId(),
                "risk_type", notification.getRiskType(),
                "severity", notification.getSeverity(),
                "alert_reason", notification.getAlertReason(),
                "sent_at", notification.getSentAt().toString()
            );

            redisTemplate.opsForHash().putAll(notificationKey, notificationData);
            redisTemplate.expire(notificationKey, Duration.ofDays(notificationRetentionDays));

            // Send to merchant
            sendEmailNotification(notification.getNotificationId(), subject, content, "MERCHANT");

        } catch (Exception e) {
            log.error("Failed to send merchant risk notification", e);
        }
    }

    private void sendRiskManagementNotification(RiskManagementNotification notification) {
        try {
            String subject = createRiskManagementSubject(notification);
            String content = createRiskManagementContent(notification);

            String notificationKey = "merchant:notifications:risk_management:" + notification.getNotificationId();
            Map<String, String> notificationData = Map.of(
                "notification_id", notification.getNotificationId(),
                "merchant_id", notification.getMerchantId(),
                "risk_type", notification.getRiskType(),
                "severity", notification.getSeverity(),
                "risk_score", notification.getRiskScore() != null ? notification.getRiskScore().toString() : "0",
                "chargeback_ratio", notification.getChargebackRatio() != null ? notification.getChargebackRatio().toString() : "0",
                "sent_at", notification.getSentAt().toString()
            );

            redisTemplate.opsForHash().putAll(notificationKey, notificationData);
            redisTemplate.expire(notificationKey, Duration.ofDays(notificationRetentionDays));

            // Send to risk management team
            sendEmailNotification(notification.getNotificationId(), subject, content, "RISK_MANAGEMENT");
            sendTeamsNotification(notification.getNotificationId(), subject, content, "RISK_MANAGEMENT");

        } catch (Exception e) {
            log.error("Failed to send risk management notification", e);
        }
    }

    private void sendAccountManagerNotification(AccountManagerNotification notification) {
        try {
            String subject = createAccountManagerSubject(notification);
            String content = createAccountManagerContent(notification);

            String notificationKey = "merchant:notifications:account_manager:" + notification.getNotificationId();
            Map<String, String> notificationData = Map.of(
                "notification_id", notification.getNotificationId(),
                "merchant_id", notification.getMerchantId(),
                "risk_type", notification.getRiskType(),
                "severity", notification.getSeverity(),
                "processing_volume", notification.getProcessingVolume() != null ? notification.getProcessingVolume().toString() : "0",
                "sent_at", notification.getSentAt().toString()
            );

            redisTemplate.opsForHash().putAll(notificationKey, notificationData);
            redisTemplate.expire(notificationKey, Duration.ofDays(notificationRetentionDays));

            // Send to account managers
            sendEmailNotification(notification.getNotificationId(), subject, content, "ACCOUNT_MANAGER");

        } catch (Exception e) {
            log.error("Failed to send account manager notification", e);
        }
    }

    private void sendComplianceNotification(ComplianceNotification notification) {
        try {
            String subject = createComplianceSubject(notification);
            String content = createComplianceContent(notification);

            String notificationKey = "merchant:notifications:compliance:" + notification.getNotificationId();
            Map<String, String> notificationData = Map.of(
                "notification_id", notification.getNotificationId(),
                "merchant_id", notification.getMerchantId(),
                "risk_type", notification.getRiskType(),
                "risk_indicators", notification.getRiskIndicators() != null ? notification.getRiskIndicators().toString() : "",
                "sent_at", notification.getSentAt().toString()
            );

            redisTemplate.opsForHash().putAll(notificationKey, notificationData);
            redisTemplate.expire(notificationKey, Duration.ofDays(notificationRetentionDays));

            // Send to compliance team
            sendEmailNotification(notification.getNotificationId(), subject, content, "COMPLIANCE");
            sendTeamsNotification(notification.getNotificationId(), subject, content, "COMPLIANCE");

        } catch (Exception e) {
            log.error("Failed to send compliance notification", e);
        }
    }

    // Content creation methods

    private String createEmergencySubject(EmergencyAlert alert) {
        return String.format("EMERGENCY: High-Risk Merchant Alert - %s - %s", 
            alert.getMerchantId(), alert.getRiskType());
    }

    private String createEmergencyContent(EmergencyAlert alert) {
        return String.format(
            "EMERGENCY MERCHANT ALERT\n\n" +
            "Merchant ID: %s\n" +
            "Risk Type: %s\n" +
            "Risk Score: %s\n" +
            "Alert Reason: %s\n" +
            "Detected At: %s\n\n" +
            "IMMEDIATE ACTION REQUIRED\n" +
            "This merchant has been flagged for emergency risk and requires immediate executive attention.",
            alert.getMerchantId(),
            alert.getRiskType(),
            alert.getRiskScore() != null ? alert.getRiskScore().toString() : "N/A",
            alert.getAlertReason(),
            alert.getSentAt().toString()
        );
    }

    private String createSMSContent(EmergencyAlert alert) {
        return String.format("EMERGENCY: Merchant %s flagged for %s - Risk Score: %s - Immediate action required",
            alert.getMerchantId(),
            alert.getRiskType(),
            alert.getRiskScore() != null ? alert.getRiskScore().toString() : "N/A");
    }

    private String createCriticalSubject(CriticalAlert alert) {
        return String.format("CRITICAL: Merchant Risk Alert - %s - %s", alert.getMerchantId(), alert.getRiskType());
    }

    private String createCriticalContent(CriticalAlert alert) {
        return String.format(
            "CRITICAL MERCHANT RISK ALERT\n\n" +
            "Merchant ID: %s\n" +
            "Risk Type: %s\n" +
            "Alert Reason: %s\n" +
            "Detected At: %s\n\n" +
            "This merchant requires urgent attention and risk assessment.",
            alert.getMerchantId(),
            alert.getRiskType(),
            alert.getAlertReason(),
            alert.getSentAt().toString()
        );
    }

    private String createMerchantSubject(MerchantRiskNotification notification) {
        return String.format("Risk Alert - Account Review Required - %s", notification.getRiskType());
    }

    private String createMerchantContent(MerchantRiskNotification notification) {
        return String.format(
            "Dear Merchant,\n\n" +
            "We have detected a risk alert on your account that requires attention.\n\n" +
            "Risk Type: %s\n" +
            "Severity: %s\n" +
            "Details: %s\n\n" +
            "Please contact our support team for assistance.",
            notification.getRiskType(),
            notification.getSeverity(),
            notification.getAlertReason()
        );
    }

    private String createRiskManagementSubject(RiskManagementNotification notification) {
        return String.format("Risk Management Alert - %s - %s - %s", 
            notification.getMerchantId(), notification.getRiskType(), notification.getSeverity());
    }

    private String createRiskManagementContent(RiskManagementNotification notification) {
        return String.format(
            "RISK MANAGEMENT ALERT\n\n" +
            "Merchant ID: %s\n" +
            "Risk Type: %s\n" +
            "Severity: %s\n" +
            "Risk Score: %s\n" +
            "Chargeback Ratio: %s%%\n" +
            "Detected At: %s\n\n" +
            "Please review and take appropriate risk mitigation action.",
            notification.getMerchantId(),
            notification.getRiskType(),
            notification.getSeverity(),
            notification.getRiskScore() != null ? notification.getRiskScore().toString() : "N/A",
            notification.getChargebackRatio() != null ? notification.getChargebackRatio().toString() : "N/A",
            notification.getSentAt().toString()
        );
    }

    private String createAccountManagerSubject(AccountManagerNotification notification) {
        return String.format("Client Risk Alert - %s - %s", notification.getMerchantId(), notification.getRiskType());
    }

    private String createAccountManagerContent(AccountManagerNotification notification) {
        return String.format(
            "CLIENT RISK ALERT\n\n" +
            "Merchant ID: %s\n" +
            "Risk Type: %s\n" +
            "Severity: %s\n" +
            "Processing Volume: %s\n" +
            "Detected At: %s\n\n" +
            "Please reach out to the client to discuss risk mitigation strategies.",
            notification.getMerchantId(),
            notification.getRiskType(),
            notification.getSeverity(),
            notification.getProcessingVolume() != null ? notification.getProcessingVolume().toString() : "N/A",
            notification.getSentAt().toString()
        );
    }

    private String createComplianceSubject(ComplianceNotification notification) {
        return String.format("Compliance Alert - %s - %s", notification.getMerchantId(), notification.getRiskType());
    }

    private String createComplianceContent(ComplianceNotification notification) {
        return String.format(
            "COMPLIANCE ALERT\n\n" +
            "Merchant ID: %s\n" +
            "Risk Type: %s\n" +
            "Risk Indicators: %s\n" +
            "Detected At: %s\n\n" +
            "Please review for compliance violations and take appropriate action.",
            notification.getMerchantId(),
            notification.getRiskType(),
            notification.getRiskIndicators() != null ? notification.getRiskIndicators().toString() : "N/A",
            notification.getSentAt().toString()
        );
    }

    // Tracking and follow-up methods (simplified implementations)
    private void trackEmergencyAlert(EmergencyAlert alert) { /* Implementation */ }
    private void initiateEmergencyEscalationChain(EmergencyAlert alert) { /* Implementation */ }
    private void scheduleImmediateFollowUp(EmergencyAlert alert) { /* Implementation */ }
    private void trackCriticalAlert(CriticalAlert alert) { /* Implementation */ }
    private void scheduleUrgentFollowUp(CriticalAlert alert) { /* Implementation */ }
    private void trackMerchantNotification(MerchantRiskNotification notification) { /* Implementation */ }
    private void scheduleMerchantFollowUp(MerchantRiskNotification notification) { /* Implementation */ }
    private void trackRiskManagementNotification(RiskManagementNotification notification) { /* Implementation */ }
    private void assignToRiskAnalyst(RiskManagementNotification notification) { /* Implementation */ }
    private void trackAccountManagerNotification(AccountManagerNotification notification) { /* Implementation */ }
    private void scheduleClientOutreach(AccountManagerNotification notification) { /* Implementation */ }
    private void trackComplianceNotification(ComplianceNotification notification) { /* Implementation */ }
    private void scheduleComplianceReview(ComplianceNotification notification) { /* Implementation */ }

    private void storeEmergencyAlert(EmergencyAlert alert) {
        try {
            String alertKey = "merchant:notifications:emergency:" + alert.getAlertId();
            Map<String, String> alertData = Map.of(
                "alert_id", alert.getAlertId(),
                "merchant_id", alert.getMerchantId(),
                "risk_type", alert.getRiskType(),
                "alert_reason", alert.getAlertReason(),
                "risk_score", alert.getRiskScore() != null ? alert.getRiskScore().toString() : "0",
                "severity", alert.getSeverity(),
                "sent_at", alert.getSentAt().toString()
            );

            redisTemplate.opsForHash().putAll(alertKey, alertData);
            redisTemplate.expire(alertKey, Duration.ofDays(notificationRetentionDays));

        } catch (Exception e) {
            log.error("Failed to store emergency alert", e);
        }
    }

    // Placeholder methods for actual notification delivery
    private void sendEmailNotification(String notificationId, String subject, String content, String recipient) {
        log.info("EMAIL NOTIFICATION [{}]: {} - {}", recipient, notificationId, subject);
    }

    private void sendSMSNotification(String notificationId, String message, String recipient) {
        log.info("SMS NOTIFICATION [{}]: {} - {}", recipient, notificationId, message);
    }

    private void initiateEmergencyPhoneCall(String alertId, String merchantId, String riskType) {
        log.error("EMERGENCY PHONE CALL: {} - Merchant: {} - Risk: {}", alertId, merchantId, riskType);
    }

    private void sendPushNotification(String notificationId, String subject, String content, String recipient) {
        log.info("PUSH NOTIFICATION [{}]: {} - {}", recipient, notificationId, subject);
    }

    private void sendTeamsNotification(String notificationId, String subject, String content, String recipient) {
        log.info("TEAMS NOTIFICATION [{}]: {} - {}", recipient, notificationId, subject);
    }

    // Data structures
    @lombok.Data
    @lombok.Builder
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    private static class EmergencyAlert {
        private String alertId;
        private String merchantId;
        private String riskType;
        private String alertReason;
        private Double riskScore;
        private String severity;
        private LocalDateTime sentAt;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    private static class CriticalAlert {
        private String alertId;
        private String merchantId;
        private String riskType;
        private String alertReason;
        private String severity;
        private LocalDateTime sentAt;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    private static class MerchantRiskNotification {
        private String notificationId;
        private String merchantId;
        private String riskType;
        private String severity;
        private String alertReason;
        private LocalDateTime sentAt;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    private static class RiskManagementNotification {
        private String notificationId;
        private String merchantId;
        private String riskType;
        private String severity;
        private Double riskScore;
        private Double chargebackRatio;
        private LocalDateTime sentAt;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    private static class AccountManagerNotification {
        private String notificationId;
        private String merchantId;
        private String riskType;
        private String severity;
        private BigDecimal processingVolume;
        private LocalDateTime sentAt;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    private static class ComplianceNotification {
        private String notificationId;
        private String merchantId;
        private String riskType;
        private Map<String, String> riskIndicators;
        private LocalDateTime sentAt;
    }
}