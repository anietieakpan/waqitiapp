package com.waqiti.security.service;

import com.waqiti.security.domain.AmlAlert;
import com.waqiti.security.domain.SuspiciousActivityReport;
import com.waqiti.security.domain.RegulatoryReport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Service for handling compliance notifications and alerts
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ComplianceNotificationService {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final AuditTrailService auditTrailService;

    /**
     * Notify compliance team of high severity alert
     */
    @Async
    public void notifyHighSeverityAlert(AmlAlert alert) {
        log.info("Notifying compliance team of high severity alert: {}", alert.getId());
        
        try {
            Map<String, Object> notification = Map.of(
                "type", "HIGH_SEVERITY_ALERT",
                "alertId", alert.getId(),
                "userId", alert.getUserId(),
                "transactionId", alert.getTransactionId(),
                "alertType", alert.getAlertType(),
                "severity", alert.getSeverity(),
                "description", alert.getDescription(),
                "timestamp", LocalDateTime.now(),
                "requiresImmediateAction", true
            );
            
            kafkaTemplate.send("compliance-notifications", "high-severity-alert", notification);
            auditTrailService.logComplianceNotification("HIGH_SEVERITY_ALERT", alert.getId(), null);
            
        } catch (Exception e) {
            log.error("Error notifying compliance team of high severity alert: {}", alert.getId(), e);
        }
    }

    /**
     * Recommend account freeze
     */
    @Async
    public void recommendAccountFreeze(UUID userId, UUID alertId) {
        log.warn("Recommending account freeze for user: {} due to alert: {}", userId, alertId);
        
        try {
            Map<String, Object> notification = Map.of(
                "type", "ACCOUNT_FREEZE_RECOMMENDATION",
                "userId", userId,
                "alertId", alertId,
                "timestamp", LocalDateTime.now(),
                "urgency", "CRITICAL",
                "action", "FREEZE_ACCOUNT"
            );
            
            kafkaTemplate.send("compliance-notifications", "account-freeze", notification);
            auditTrailService.logAccountFreezeRecommendation(userId, alertId);
            
        } catch (Exception e) {
            log.error("Error sending account freeze recommendation for user: {}", userId, e);
        }
    }

    /**
     * Notify enhanced due diligence required
     */
    @Async
    public void notifyEnhancedDueDiligenceRequired(UUID userId, String countryCode) {
        log.info("Enhanced due diligence required for user: {} (country: {})", userId, countryCode);
        
        try {
            Map<String, Object> notification = Map.of(
                "type", "ENHANCED_DUE_DILIGENCE_REQUIRED",
                "userId", userId,
                "countryCode", countryCode,
                "timestamp", LocalDateTime.now(),
                "priority", "HIGH"
            );
            
            kafkaTemplate.send("compliance-notifications", "enhanced-dd", notification);
            auditTrailService.logEnhancedDueDiligenceRequest(userId, countryCode);
            
        } catch (Exception e) {
            log.error("Error notifying enhanced due diligence requirement for user: {}", userId, e);
        }
    }

    /**
     * Notify SAR filed
     */
    @Async
    public void notifySarFiled(SuspiciousActivityReport sar) {
        log.info("Notifying SAR filed: {} for user: {}", sar.getReportNumber(), sar.getUserId());
        
        try {
            Map<String, Object> notification = Map.of(
                "type", "SAR_FILED",
                "sarId", sar.getId(),
                "reportNumber", sar.getReportNumber(),
                "userId", sar.getUserId(),
                "transactionId", sar.getTransactionId(),
                "requiresImmediateAttention", sar.isRequiresImmediateAttention(),
                "timestamp", LocalDateTime.now()
            );
            
            kafkaTemplate.send("compliance-notifications", "sar-filed", notification);
            auditTrailService.logSarNotification(sar.getId(), "FILED");
            
        } catch (Exception e) {
            log.error("Error notifying SAR filed: {}", sar.getId(), e);
        }
    }

    /**
     * Notify immediate attention required
     */
    @Async
    public void notifyImmediateAttentionRequired(SuspiciousActivityReport sar) {
        log.warn("Immediate attention required for SAR: {}", sar.getReportNumber());
        
        try {
            Map<String, Object> notification = Map.of(
                "type", "IMMEDIATE_ATTENTION_REQUIRED",
                "sarId", sar.getId(),
                "reportNumber", sar.getReportNumber(),
                "userId", sar.getUserId(),
                "suspiciousActivity", sar.getSuspiciousActivity(),
                "priority", sar.getPriorityLevel(),
                "timestamp", LocalDateTime.now(),
                "escalationLevel", "CRITICAL"
            );
            
            kafkaTemplate.send("compliance-notifications", "immediate-attention", notification);
            auditTrailService.logImmediateAttentionAlert(sar.getId());
            
        } catch (Exception e) {
            log.error("Error notifying immediate attention required for SAR: {}", sar.getId(), e);
        }
    }

    /**
     * Notify reporting failure
     */
    @Async
    public void notifyReportingFailure(String reportType, UUID transactionId, String errorMessage) {
        log.error("Reporting failure for {} transaction: {} - {}", reportType, transactionId, errorMessage);
        
        try {
            Map<String, Object> notification = Map.of(
                "type", "REPORTING_FAILURE",
                "reportType", reportType,
                "transactionId", transactionId,
                "errorMessage", errorMessage,
                "timestamp", LocalDateTime.now(),
                "severity", "HIGH"
            );
            
            kafkaTemplate.send("compliance-notifications", "reporting-failure", notification);
            auditTrailService.logReportingFailure(reportType, transactionId, errorMessage);
            
        } catch (Exception e) {
            log.error("Error notifying reporting failure for {}: {}", reportType, transactionId, e);
        }
    }

    /**
     * Notify monthly report generated
     */
    @Async
    public void notifyMonthlyReportGenerated(RegulatoryReport report) {
        log.info("Monthly compliance report generated: {}", report.getId());
        
        try {
            Map<String, Object> notification = Map.of(
                "type", "MONTHLY_REPORT_GENERATED",
                "reportId", report.getId(),
                "reportType", report.getReportType(),
                "reportingPeriod", report.getReportingPeriod(),
                "jurisdictionCode", report.getJurisdictionCode(),
                "timestamp", LocalDateTime.now()
            );
            
            kafkaTemplate.send("compliance-notifications", "monthly-report", notification);
            auditTrailService.logReportGeneration(report.getId(), "MONTHLY_COMPLIANCE");
            
        } catch (Exception e) {
            log.error("Error notifying monthly report generation: {}", report.getId(), e);
        }
    }

    /**
     * Send compliance dashboard alerts
     */
    @Async
    public void sendDashboardAlert(String alertType, String message, String severity) {
        log.info("Sending dashboard alert: {} - {}", alertType, message);
        
        try {
            Map<String, Object> alert = Map.of(
                "type", "DASHBOARD_ALERT",
                "alertType", alertType,
                "message", message,
                "severity", severity,
                "timestamp", LocalDateTime.now()
            );
            
            kafkaTemplate.send("compliance-notifications", "dashboard-alert", alert);
            
        } catch (Exception e) {
            log.error("Error sending dashboard alert: {}", alertType, e);
        }
    }

    /**
     * Notify regulatory deadline approaching
     */
    @Async
    public void notifyRegulatoryDeadlineApproaching(String reportType, LocalDateTime deadline, int daysRemaining) {
        log.warn("Regulatory deadline approaching: {} due in {} days", reportType, daysRemaining);
        
        try {
            Map<String, Object> notification = Map.of(
                "type", "REGULATORY_DEADLINE_APPROACHING",
                "reportType", reportType,
                "deadline", deadline,
                "daysRemaining", daysRemaining,
                "timestamp", LocalDateTime.now(),
                "urgency", daysRemaining <= 3 ? "HIGH" : "MEDIUM"
            );
            
            kafkaTemplate.send("compliance-notifications", "deadline-approaching", notification);
            
        } catch (Exception e) {
            log.error("Error notifying regulatory deadline: {}", reportType, e);
        }
    }

    /**
     * Notify compliance team
     */
    @Async
    public void notifyComplianceTeam(String subject, Object details) {
        log.info("Notifying compliance team: {}", subject);

        try {
            Map<String, Object> notification = Map.of(
                "type", "COMPLIANCE_TEAM_NOTIFICATION",
                "subject", subject,
                "details", details,
                "timestamp", LocalDateTime.now()
            );

            kafkaTemplate.send("compliance-notifications", "team-notification", notification);

        } catch (Exception e) {
            log.error("Error notifying compliance team: {}", subject, e);
        }
    }

    /**
     * Notify critical compliance failure
     */
    @Async
    public void notifyCriticalComplianceFailure(String message) {
        log.error("CRITICAL COMPLIANCE FAILURE: {}", message);

        try {
            Map<String, Object> notification = Map.of(
                "type", "CRITICAL_COMPLIANCE_FAILURE",
                "message", message,
                "timestamp", LocalDateTime.now(),
                "urgency", "CRITICAL"
            );

            kafkaTemplate.send("compliance-notifications", "critical-failure", notification);

        } catch (Exception e) {
            log.error("Error notifying critical compliance failure", e);
        }
    }
}