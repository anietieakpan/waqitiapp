package com.waqiti.user.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Customer Notification Service - Handles critical customer notifications
 * 
 * Provides comprehensive notification capabilities for:
 * - Emergency executive alerts for critical customer events
 * - Critical alerts for high-priority customer situations
 * - Regulatory compliance alerts and escalations
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
public class CustomerNotificationService {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${customer.notifications.enabled:true}")
    private boolean notificationsEnabled;

    @Value("${customer.notifications.executive.enabled:true}")
    private boolean executiveNotificationsEnabled;

    @Value("${customer.notifications.emergency.phone:true}")
    private boolean emergencyPhoneEnabled;

    @Value("${customer.notifications.retention.days:365}")
    private int notificationRetentionDays;

    /**
     * Sends emergency executive alert for critical customer events
     */
    public void sendEmergencyExecutiveAlert(
            String customerId,
            String alertType,
            String alertReason,
            BigDecimal fraudAmount) {

        if (!notificationsEnabled || !executiveNotificationsEnabled) {
            log.debug("Executive notifications disabled, skipping emergency alert");
            return;
        }

        try {
            log.error("SENDING EMERGENCY EXECUTIVE ALERT - Customer: {} - Alert: {}", 
                customerId, alertType);

            // Create emergency alert notification
            Map<String, Object> notification = new HashMap<>();
            notification.put("notificationId", UUID.randomUUID().toString());
            notification.put("customerId", customerId);
            notification.put("alertType", alertType);
            notification.put("alertReason", alertReason);
            notification.put("fraudAmount", fraudAmount);
            notification.put("severity", "EMERGENCY");
            notification.put("type", "EMERGENCY_EXECUTIVE_ALERT");
            notification.put("priority", "CRITICAL");
            notification.put("timestamp", LocalDateTime.now());
            notification.put("channels", new String[]{"EMAIL", "SMS", "PHONE", "PUSH", "TEAMS"});
            
            // Add executive-specific data
            Map<String, Object> executiveData = new HashMap<>();
            executiveData.put("requires_immediate_action", true);
            executiveData.put("escalation_level", "EXECUTIVE");
            executiveData.put("response_required_within", "15_MINUTES");
            notification.put("executiveData", executiveData);

            // Send to notification service
            kafkaTemplate.send("notification-requests", notification);

            // Store emergency alert for tracking
            storeEmergencyAlert(notification);

            log.error("Emergency executive alert sent for customer: {}", customerId);

        } catch (Exception e) {
            log.error("CRITICAL FAILURE: Failed to send emergency executive alert for customer: {}", 
                customerId, e);
        }
    }

    /**
     * Sends critical alert to executive team
     */
    public void sendCriticalAlert(
            String customerId,
            String alertType,
            String alertReason) {

        if (!notificationsEnabled) {
            return;
        }

        try {
            log.warn("Sending critical alert for customer: {} - Alert: {}", customerId, alertType);

            Map<String, Object> notification = new HashMap<>();
            notification.put("notificationId", UUID.randomUUID().toString());
            notification.put("customerId", customerId);
            notification.put("alertType", alertType);
            notification.put("alertReason", alertReason);
            notification.put("severity", "CRITICAL");
            notification.put("type", "CRITICAL_EXECUTIVE_ALERT");
            notification.put("priority", "HIGH");
            notification.put("timestamp", LocalDateTime.now());
            notification.put("channels", new String[]{"EMAIL", "SMS", "PUSH", "TEAMS"});
            
            // Add critical alert data
            Map<String, Object> criticalData = new HashMap<>();
            criticalData.put("escalation_level", "EXECUTIVE");
            criticalData.put("response_required_within", "1_HOUR");
            notification.put("criticalData", criticalData);

            // Send critical alert
            kafkaTemplate.send("notification-requests", notification);

            // Track critical alert
            trackCriticalAlert(notification);

            log.warn("Critical alert sent for customer: {}", customerId);

        } catch (Exception e) {
            log.error("Failed to send critical alert for customer: {}", customerId, e);
        }
    }

    /**
     * Sends regulatory compliance alert
     */
    public void sendRegulatoryComplianceAlert(
            String customerId,
            String alertType,
            String alertReason,
            String complianceViolation) {

        if (!notificationsEnabled) {
            return;
        }

        try {
            log.warn("Sending regulatory compliance alert for customer: {} - Type: {}", 
                customerId, alertType);

            Map<String, Object> notification = new HashMap<>();
            notification.put("notificationId", UUID.randomUUID().toString());
            notification.put("customerId", customerId);
            notification.put("alertType", alertType);
            notification.put("alertReason", alertReason);
            notification.put("complianceViolation", complianceViolation);
            notification.put("severity", "REGULATORY");
            notification.put("type", "REGULATORY_COMPLIANCE_ALERT");
            notification.put("priority", "CRITICAL");
            notification.put("timestamp", LocalDateTime.now());
            notification.put("channels", new String[]{"EMAIL", "TEAMS"});
            
            // Add regulatory data
            Map<String, Object> regulatoryData = new HashMap<>();
            regulatoryData.put("requires_regulatory_reporting", true);
            regulatoryData.put("compliance_team_notification", true);
            regulatoryData.put("escalation_level", "REGULATORY_EXECUTIVE");
            notification.put("regulatoryData", regulatoryData);

            // Send regulatory alert
            kafkaTemplate.send("notification-requests", notification);

            // Track regulatory alert
            trackRegulatoryAlert(notification);

            log.warn("Regulatory compliance alert sent for customer: {}", customerId);

        } catch (Exception e) {
            log.error("Failed to send regulatory compliance alert for customer: {}", customerId, e);
        }
    }

    /**
     * Notifies customer of account block (when appropriate)
     */
    public void notifyCustomerOfAccountBlock(
            String customerId,
            String blockType,
            String blockReason,
            boolean isTemporary,
            Integer blockDuration) {

        if (!notificationsEnabled) {
            return;
        }

        try {
            log.info("Notifying customer of account block: {} - Type: {}", customerId, blockType);

            Map<String, Object> notification = new HashMap<>();
            notification.put("notificationId", UUID.randomUUID().toString());
            notification.put("customerId", customerId);
            notification.put("blockType", blockType);
            notification.put("blockReason", sanitizeReasonForCustomer(blockReason));
            notification.put("isTemporary", isTemporary);
            notification.put("blockDuration", blockDuration);
            notification.put("type", "CUSTOMER_ACCOUNT_BLOCK");
            notification.put("priority", "HIGH");
            notification.put("timestamp", LocalDateTime.now());
            notification.put("channels", new String[]{"EMAIL", "SMS", "PUSH", "IN_APP"});
            
            // Add customer support information
            Map<String, Object> supportData = new HashMap<>();
            supportData.put("support_contact", "support@example.com");
            supportData.put("support_phone", "+1-800-WAQITI");
            supportData.put("can_appeal", !blockType.contains("OFAC"));
            supportData.put("appeal_process_url", "https://example.com/account-appeal");
            notification.put("supportData", supportData);

            // Send customer notification
            kafkaTemplate.send("notification-requests", notification);

            // Track customer notification
            trackCustomerNotification(notification);

            log.info("Customer block notification sent: {}", customerId);

        } catch (Exception e) {
            log.error("Failed to notify customer of account block: {}", customerId, e);
        }
    }

    /**
     * Notifies fraud management team
     */
    public void notifyFraudManagementTeam(
            String customerId,
            String blockType,
            BigDecimal fraudAmount,
            Map<String, String> relatedTransactions) {

        if (!notificationsEnabled) {
            return;
        }

        try {
            log.debug("Notifying fraud management team for customer: {}", customerId);

            Map<String, Object> notification = new HashMap<>();
            notification.put("notificationId", UUID.randomUUID().toString());
            notification.put("customerId", customerId);
            notification.put("blockType", blockType);
            notification.put("fraudAmount", fraudAmount);
            notification.put("relatedTransactions", relatedTransactions);
            notification.put("type", "FRAUD_MANAGEMENT_ALERT");
            notification.put("priority", "HIGH");
            notification.put("timestamp", LocalDateTime.now());
            notification.put("channels", new String[]{"EMAIL", "TEAMS"});
            
            // Add fraud team data
            Map<String, Object> fraudData = new HashMap<>();
            fraudData.put("team", "FRAUD_MANAGEMENT");
            fraudData.put("requires_investigation", true);
            fraudData.put("case_priority", determineFraudPriority(fraudAmount));
            notification.put("fraudData", fraudData);

            // Send to fraud team
            kafkaTemplate.send("notification-requests", notification);

            // Track fraud team notification
            trackFraudTeamNotification(notification);

            log.info("Fraud management team notified for customer: {}", customerId);

        } catch (Exception e) {
            log.error("Failed to notify fraud management team for customer: {}", customerId, e);
        }
    }

    /**
     * Notifies compliance team
     */
    public void notifyComplianceTeam(
            String customerId,
            String blockType,
            String complianceViolation,
            String legalReference) {

        if (!notificationsEnabled) {
            return;
        }

        try {
            log.debug("Notifying compliance team for customer: {}", customerId);

            Map<String, Object> notification = new HashMap<>();
            notification.put("notificationId", UUID.randomUUID().toString());
            notification.put("customerId", customerId);
            notification.put("blockType", blockType);
            notification.put("complianceViolation", complianceViolation);
            notification.put("legalReference", legalReference);
            notification.put("type", "COMPLIANCE_TEAM_ALERT");
            notification.put("priority", "HIGH");
            notification.put("timestamp", LocalDateTime.now());
            notification.put("channels", new String[]{"EMAIL", "TEAMS"});
            
            // Add compliance team data
            Map<String, Object> complianceData = new HashMap<>();
            complianceData.put("team", "COMPLIANCE");
            complianceData.put("requires_review", true);
            complianceData.put("regulatory_reporting_required", 
                complianceViolation != null && complianceViolation.contains("OFAC"));
            notification.put("complianceData", complianceData);

            // Send to compliance team
            kafkaTemplate.send("notification-requests", notification);

            // Track compliance team notification
            trackComplianceTeamNotification(notification);

            log.info("Compliance team notified for customer: {}", customerId);

        } catch (Exception e) {
            log.error("Failed to notify compliance team for customer: {}", customerId, e);
        }
    }

    /**
     * Notifies legal team for legal mandate blocks
     */
    public void notifyLegalTeam(
            String customerId,
            String legalReference,
            String blockReason,
            Map<String, String> evidenceDocuments) {

        if (!notificationsEnabled) {
            return;
        }

        try {
            log.debug("Notifying legal team for customer: {}", customerId);

            Map<String, Object> notification = new HashMap<>();
            notification.put("notificationId", UUID.randomUUID().toString());
            notification.put("customerId", customerId);
            notification.put("legalReference", legalReference);
            notification.put("blockReason", blockReason);
            notification.put("evidenceDocuments", evidenceDocuments);
            notification.put("type", "LEGAL_TEAM_ALERT");
            notification.put("priority", "HIGH");
            notification.put("timestamp", LocalDateTime.now());
            notification.put("channels", new String[]{"EMAIL", "TEAMS"});
            
            // Add legal team data
            Map<String, Object> legalData = new HashMap<>();
            legalData.put("team", "LEGAL");
            legalData.put("requires_legal_review", true);
            legalData.put("mandate_type", extractMandateType(legalReference));
            notification.put("legalData", legalData);

            // Send to legal team
            kafkaTemplate.send("notification-requests", notification);

            // Track legal team notification
            trackLegalTeamNotification(notification);

            log.info("Legal team notified for customer: {}", customerId);

        } catch (Exception e) {
            log.error("Failed to notify legal team for customer: {}", customerId, e);
        }
    }

    /**
     * Notifies risk management team
     */
    public void notifyRiskManagementTeam(
            String customerId,
            String blockType,
            Double riskScore,
            BigDecimal fraudAmount) {

        if (!notificationsEnabled) {
            return;
        }

        try {
            log.debug("Notifying risk management team for customer: {}", customerId);

            Map<String, Object> notification = new HashMap<>();
            notification.put("notificationId", UUID.randomUUID().toString());
            notification.put("customerId", customerId);
            notification.put("blockType", blockType);
            notification.put("riskScore", riskScore);
            notification.put("fraudAmount", fraudAmount);
            notification.put("type", "RISK_MANAGEMENT_ALERT");
            notification.put("priority", "MEDIUM");
            notification.put("timestamp", LocalDateTime.now());
            notification.put("channels", new String[]{"EMAIL", "TEAMS"});
            
            // Add risk management data
            Map<String, Object> riskData = new HashMap<>();
            riskData.put("team", "RISK_MANAGEMENT");
            riskData.put("risk_level", getRiskLevel(riskScore));
            riskData.put("requires_risk_assessment", true);
            notification.put("riskData", riskData);

            // Send to risk management team
            kafkaTemplate.send("notification-requests", notification);

            // Track risk management notification
            trackRiskManagementNotification(notification);

            log.info("Risk management team notified for customer: {}", customerId);

        } catch (Exception e) {
            log.error("Failed to notify risk management team for customer: {}", customerId, e);
        }
    }

    // Helper methods

    private void storeEmergencyAlert(Map<String, Object> notification) {
        try {
            String alertKey = "customer:notifications:emergency:" + notification.get("notificationId");
            redisTemplate.opsForHash().putAll(alertKey, notification);
            redisTemplate.expire(alertKey, Duration.ofDays(notificationRetentionDays));
        } catch (Exception e) {
            log.error("Failed to store emergency alert", e);
        }
    }

    private void trackCriticalAlert(Map<String, Object> notification) {
        try {
            String alertKey = "customer:notifications:critical:" + notification.get("notificationId");
            redisTemplate.opsForHash().putAll(alertKey, notification);
            redisTemplate.expire(alertKey, Duration.ofDays(notificationRetentionDays));
        } catch (Exception e) {
            log.error("Failed to track critical alert", e);
        }
    }

    private void trackRegulatoryAlert(Map<String, Object> notification) {
        try {
            String alertKey = "customer:notifications:regulatory:" + notification.get("notificationId");
            redisTemplate.opsForHash().putAll(alertKey, notification);
            redisTemplate.expire(alertKey, Duration.ofDays(notificationRetentionDays));
        } catch (Exception e) {
            log.error("Failed to track regulatory alert", e);
        }
    }

    private void trackCustomerNotification(Map<String, Object> notification) {
        try {
            String notificationKey = "customer:notifications:customer:" + notification.get("notificationId");
            redisTemplate.opsForHash().putAll(notificationKey, notification);
            redisTemplate.expire(notificationKey, Duration.ofDays(notificationRetentionDays));
        } catch (Exception e) {
            log.error("Failed to track customer notification", e);
        }
    }

    private void trackFraudTeamNotification(Map<String, Object> notification) {
        try {
            String notificationKey = "customer:notifications:fraud_team:" + notification.get("notificationId");
            redisTemplate.opsForHash().putAll(notificationKey, notification);
            redisTemplate.expire(notificationKey, Duration.ofDays(notificationRetentionDays));
        } catch (Exception e) {
            log.error("Failed to track fraud team notification", e);
        }
    }

    private void trackComplianceTeamNotification(Map<String, Object> notification) {
        try {
            String notificationKey = "customer:notifications:compliance_team:" + notification.get("notificationId");
            redisTemplate.opsForHash().putAll(notificationKey, notification);
            redisTemplate.expire(notificationKey, Duration.ofDays(notificationRetentionDays));
        } catch (Exception e) {
            log.error("Failed to track compliance team notification", e);
        }
    }

    private void trackLegalTeamNotification(Map<String, Object> notification) {
        try {
            String notificationKey = "customer:notifications:legal_team:" + notification.get("notificationId");
            redisTemplate.opsForHash().putAll(notificationKey, notification);
            redisTemplate.expire(notificationKey, Duration.ofDays(notificationRetentionDays));
        } catch (Exception e) {
            log.error("Failed to track legal team notification", e);
        }
    }

    private void trackRiskManagementNotification(Map<String, Object> notification) {
        try {
            String notificationKey = "customer:notifications:risk_management:" + notification.get("notificationId");
            redisTemplate.opsForHash().putAll(notificationKey, notification);
            redisTemplate.expire(notificationKey, Duration.ofDays(notificationRetentionDays));
        } catch (Exception e) {
            log.error("Failed to track risk management notification", e);
        }
    }

    private String sanitizeReasonForCustomer(String blockReason) {
        if (blockReason == null) return "Account security review required";
        
        // Don't expose sensitive investigation details to customers
        if (blockReason.contains("INVESTIGATION") || blockReason.contains("OFAC") || 
            blockReason.contains("SANCTIONS")) {
            return "Account under compliance review";
        }
        
        if (blockReason.contains("FRAUD")) {
            return "Suspicious activity detected on account";
        }
        
        return "Account security review required";
    }

    private String determineFraudPriority(BigDecimal fraudAmount) {
        if (fraudAmount == null) return "MEDIUM";
        
        if (fraudAmount.compareTo(new BigDecimal("100000")) > 0) return "CRITICAL";
        if (fraudAmount.compareTo(new BigDecimal("10000")) > 0) return "HIGH";
        return "MEDIUM";
    }

    private String extractMandateType(String legalReference) {
        if (legalReference == null) return "UNKNOWN";
        if (legalReference.contains("COURT")) return "COURT_ORDER";
        if (legalReference.contains("SUBPOENA")) return "SUBPOENA";
        if (legalReference.contains("WARRANT")) return "WARRANT";
        return "OTHER";
    }

    private String getRiskLevel(Double riskScore) {
        if (riskScore == null) return "UNKNOWN";
        if (riskScore >= 80.0) return "VERY_HIGH";
        if (riskScore >= 60.0) return "HIGH";
        if (riskScore >= 40.0) return "MEDIUM";
        return "LOW";
    }
}