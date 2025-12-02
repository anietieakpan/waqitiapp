package com.waqiti.common.email;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

/**
 * CRITICAL SYSTEM EMAIL SERVICE for Waqiti Common Module
 * 
 * ===== ARCHITECTURAL BOUNDARIES =====
 * 
 * PURPOSE: This service handles ONLY critical, time-sensitive system notifications
 * that cannot wait for or depend on external notification services.
 * 
 * SCOPE LIMITATIONS:
 * - Fraud detection alerts (immediate response required)
 * - Security breach notifications (regulatory compliance)
 * - System health critical alerts (service availability)
 * - Compliance violation notifications (legal requirements)
 * - Emergency operational alerts (business continuity)
 * 
 * NOT IN SCOPE (handled by notification-service):
 * - User communication emails
 * - Marketing and promotional content
 * - Bulk messaging operations
 * - Complex email workflows
 * - Template-heavy email campaigns
 * - Non-critical business notifications
 * 
 * RELATIONSHIP WITH NOTIFICATION-SERVICE:
 * - This service: Immediate, critical, system-level alerts
 * - Notification-service: User-facing, workflow-based, bulk communications
 * - Both services coexist for different architectural needs
 * - No duplication of concerns - different use cases
 * 
 * DESIGN PRINCIPLES:
 * - Minimal dependencies (high availability requirement)
 * - Synchronous critical alerts (blocking for security)
 * - Simple, reliable implementations (no complex templating)
 * - Fast failover capabilities (system resilience)
 * - Audit trail for regulatory compliance
 * 
 * USAGE EXAMPLES:
 * ✅ Fraud alert detected - immediate notification required
 * ✅ Security breach - must notify compliance team immediately
 * ✅ System component failure - operations team alert
 * ❌ Welcome email to new user - use notification-service
 * ❌ Marketing newsletter - use notification-service
 * ❌ Password reset email - use notification-service
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "waqiti.email.critical-alerts.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    /**
     * Send CRITICAL FRAUD ALERT - Immediate synchronous delivery
     * 
     * This method blocks until email is sent to ensure critical fraud alerts
     * are delivered immediately for regulatory compliance and risk management.
     */
    public CriticalEmailResult sendCriticalFraudAlert(
            String recipientEmail, 
            String alertId,
            String alertType,
            String transactionId,
            String riskLevel) {
        
        String subject = String.format("[CRITICAL FRAUD ALERT] %s - Alert ID: %s", alertType, alertId);
        String body = String.format(
            "CRITICAL FRAUD ALERT DETECTED\n\n" +
            "Alert ID: %s\n" +
            "Alert Type: %s\n" +
            "Transaction ID: %s\n" +
            "Risk Level: %s\n" +
            "Timestamp: %s\n\n" +
            "IMMEDIATE INVESTIGATION REQUIRED\n" +
            "This is an automated system alert from Waqiti Fraud Detection System.",
            alertId, alertType, transactionId, riskLevel, LocalDateTime.now()
        );

        return sendCriticalEmail(recipientEmail, subject, body, "FRAUD_ALERT");
    }

    /**
     * Send SECURITY BREACH NOTIFICATION - Immediate synchronous delivery
     */
    public CriticalEmailResult sendSecurityBreachAlert(
            String recipientEmail,
            String breachType,
            String severity,
            String description) {
        
        String subject = String.format("[SECURITY BREACH] %s - Severity: %s", breachType, severity);
        String body = String.format(
            "SECURITY BREACH DETECTED\n\n" +
            "Breach Type: %s\n" +
            "Severity: %s\n" +
            "Description: %s\n" +
            "Detection Time: %s\n\n" +
            "IMMEDIATE ACTION REQUIRED\n" +
            "This is an automated security alert from Waqiti Security Monitoring System.",
            breachType, severity, description, LocalDateTime.now()
        );

        return sendCriticalEmail(recipientEmail, subject, body, "SECURITY_BREACH");
    }

    /**
     * Send COMPLIANCE VIOLATION ALERT - Immediate synchronous delivery
     */
    public CriticalEmailResult sendComplianceViolationAlert(
            String recipientEmail,
            String violationType,
            String regulatoryRequirement,
            String actionRequired) {
        
        String subject = String.format("[COMPLIANCE VIOLATION] %s", violationType);
        String body = String.format(
            "COMPLIANCE VIOLATION DETECTED\n\n" +
            "Violation Type: %s\n" +
            "Regulatory Requirement: %s\n" +
            "Action Required: %s\n" +
            "Detection Time: %s\n\n" +
            "REGULATORY COMPLIANCE REQUIRED\n" +
            "This is an automated compliance alert from Waqiti Compliance Monitoring System.",
            violationType, regulatoryRequirement, actionRequired, LocalDateTime.now()
        );

        return sendCriticalEmail(recipientEmail, subject, body, "COMPLIANCE_VIOLATION");
    }

    /**
     * Send SYSTEM HEALTH CRITICAL ALERT - Immediate synchronous delivery
     */
    public CriticalEmailResult sendSystemHealthCriticalAlert(
            String recipientEmail,
            String componentName,
            String healthStatus,
            String impact) {
        
        String subject = String.format("[SYSTEM CRITICAL] %s - Status: %s", componentName, healthStatus);
        String body = String.format(
            "CRITICAL SYSTEM HEALTH ALERT\n\n" +
            "Component: %s\n" +
            "Status: %s\n" +
            "Business Impact: %s\n" +
            "Alert Time: %s\n\n" +
            "IMMEDIATE OPERATIONAL RESPONSE REQUIRED\n" +
            "This is an automated system health alert from Waqiti Operations Monitoring.",
            componentName, healthStatus, impact, LocalDateTime.now()
        );

        return sendCriticalEmail(recipientEmail, subject, body, "SYSTEM_HEALTH");
    }

    /**
     * Core critical email sending method - Synchronous, blocking operation
     */
    private CriticalEmailResult sendCriticalEmail(
            String recipientEmail, 
            String subject, 
            String body, 
            String alertCategory) {
        
        long startTime = System.currentTimeMillis();
        
        try {
            // Validate recipient
            if (recipientEmail == null || recipientEmail.trim().isEmpty()) {
                throw new IllegalArgumentException("Recipient email cannot be null or empty for critical alerts");
            }

            // Create and send message
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(recipientEmail);
            message.setSubject(subject);
            message.setText(body);
            message.setFrom("alerts@example.com"); // System alerts sender
            message.setSentDate(new java.util.Date());

            // Synchronous send - blocks until completed or failed
            mailSender.send(message);
            
            long duration = System.currentTimeMillis() - startTime;
            
            log.warn("CRITICAL EMAIL SENT: Category={}, Recipient={}, Duration={}ms", 
                    alertCategory, recipientEmail, duration);
            
            return CriticalEmailResult.builder()
                .success(true)
                .recipientEmail(recipientEmail)
                .subject(subject)
                .alertCategory(alertCategory)
                .sentAt(LocalDateTime.now())
                .deliveryTimeMs(duration)
                .build();
                
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            
            log.error("CRITICAL EMAIL FAILED: Category={}, Recipient={}, Duration={}ms, Error={}", 
                     alertCategory, recipientEmail, duration, e.getMessage(), e);
            
            return CriticalEmailResult.builder()
                .success(false)
                .recipientEmail(recipientEmail)
                .subject(subject)
                .alertCategory(alertCategory)
                .sentAt(LocalDateTime.now())
                .deliveryTimeMs(duration)
                .errorMessage(e.getMessage())
                .build();
        }
    }

    /**
     * Send asynchronous non-critical system notification
     * Used for notifications that can be delayed but still need system-level handling
     */
    public CompletableFuture<CriticalEmailResult> sendSystemNotificationAsync(
            String recipientEmail,
            String subject,
            String body,
            String category) {
        
        return CompletableFuture.supplyAsync(() -> 
            sendCriticalEmail(recipientEmail, subject, body, category));
    }

    /**
     * Health check for critical email service
     */
    public boolean isEmailServiceHealthy() {
        try {
            // Test mail sender availability
            mailSender.createMimeMessage();
            return true;
        } catch (Exception e) {
            log.error("Email service health check failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Get service statistics for monitoring
     */
    public EmailServiceStats getServiceStats() {
        return EmailServiceStats.builder()
            .serviceName("CriticalEmailService")
            .isHealthy(isEmailServiceHealthy())
            .purpose("Critical system alerts and fraud notifications")
            .scope("Security, Fraud, Compliance, System Health")
            .notInScope("User communications, marketing, workflows")
            .relationshipWithNotificationService("Complementary - handles different use cases")
            .checkedAt(LocalDateTime.now())
            .build();
    }

    // Result DTOs
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class CriticalEmailResult {
        private boolean success;
        private String recipientEmail;
        private String subject;
        private String alertCategory;
        private LocalDateTime sentAt;
        private long deliveryTimeMs;
        private String errorMessage;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class EmailServiceStats {
        private String serviceName;
        private boolean isHealthy;
        private String purpose;
        private String scope;
        private String notInScope;
        private String relationshipWithNotificationService;
        private LocalDateTime checkedAt;
    }
}