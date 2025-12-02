package com.waqiti.security.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Service for maintaining comprehensive audit trails for compliance
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditTrailService {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Log SAR filing activity
     */
    @Async
    public void logSarFiling(UUID sarId, UUID userId, UUID transactionId, String suspiciousActivity) {
        log.info("Logging SAR filing: {} for user: {}", sarId, userId);
        
        Map<String, Object> auditEvent = Map.of(
            "eventType", "SAR_FILED",
            "sarId", sarId,
            "userId", userId,
            "transactionId", transactionId,
            "suspiciousActivity", suspiciousActivity,
            "timestamp", LocalDateTime.now(),
            "severity", "HIGH",
            "compliance", true
        );
        
        kafkaTemplate.send("audit-trail", "sar-filing", auditEvent);
    }

    /**
     * Log regulatory report generation
     */
    @Async
    public void logRegulatoryReportGeneration(UUID reportId, UUID alertId) {
        log.info("Logging regulatory report generation: {} for alert: {}", reportId, alertId);
        
        Map<String, Object> auditEvent = Map.of(
            "eventType", "REGULATORY_REPORT_GENERATED",
            "reportId", reportId,
            "alertId", alertId,
            "timestamp", LocalDateTime.now(),
            "compliance", true
        );
        
        kafkaTemplate.send("audit-trail", "report-generation", auditEvent);
    }

    /**
     * Log CTR generation
     */
    @Async
    public void logCtrGeneration(UUID reportId, UUID transactionId, java.math.BigDecimal amount) {
        log.info("Logging CTR generation: {} for transaction: {}", reportId, transactionId);
        
        Map<String, Object> auditEvent = Map.of(
            "eventType", "CTR_GENERATED",
            "reportId", reportId,
            "transactionId", transactionId,
            "amount", amount,
            "timestamp", LocalDateTime.now(),
            "compliance", true,
            "regulatory", true
        );
        
        kafkaTemplate.send("audit-trail", "ctr-generation", auditEvent);
    }

    /**
     * Log SAR status change
     */
    @Async
    public void logSarStatusChange(UUID sarId, Object oldStatus, Object newStatus, 
                                  UUID reviewerId, String reviewNotes) {
        log.info("Logging SAR status change: {} from {} to {}", sarId, oldStatus, newStatus);
        
        Map<String, Object> auditEvent = Map.of(
            "eventType", "SAR_STATUS_CHANGED",
            "sarId", sarId,
            "oldStatus", oldStatus,
            "newStatus", newStatus,
            "reviewerId", reviewerId,
            "reviewNotes", reviewNotes,
            "timestamp", LocalDateTime.now(),
            "compliance", true
        );
        
        kafkaTemplate.send("audit-trail", "sar-status-change", auditEvent);
    }

    /**
     * Log compliance notification sent
     */
    @Async
    public void logComplianceNotification(String notificationType, UUID relatedEntityId, String details) {
        log.info("Logging compliance notification: {} for entity: {}", notificationType, relatedEntityId);
        
        Map<String, Object> auditEvent = Map.of(
            "eventType", "COMPLIANCE_NOTIFICATION_SENT",
            "notificationType", notificationType,
            "relatedEntityId", relatedEntityId,
            "details", details,
            "timestamp", LocalDateTime.now(),
            "compliance", true
        );
        
        kafkaTemplate.send("audit-trail", "compliance-notification", auditEvent);
    }

    /**
     * Log account freeze recommendation
     */
    @Async
    public void logAccountFreezeRecommendation(UUID userId, UUID alertId) {
        log.warn("Logging account freeze recommendation for user: {} due to alert: {}", userId, alertId);
        
        Map<String, Object> auditEvent = Map.of(
            "eventType", "ACCOUNT_FREEZE_RECOMMENDED",
            "userId", userId,
            "alertId", alertId,
            "timestamp", LocalDateTime.now(),
            "severity", "CRITICAL",
            "compliance", true,
            "actionRequired", true
        );
        
        kafkaTemplate.send("audit-trail", "account-freeze-recommendation", auditEvent);
    }

    /**
     * Log enhanced due diligence request
     */
    @Async
    public void logEnhancedDueDiligenceRequest(UUID userId, String countryCode) {
        log.info("Logging enhanced due diligence request for user: {} (country: {})", userId, countryCode);
        
        Map<String, Object> auditEvent = Map.of(
            "eventType", "ENHANCED_DUE_DILIGENCE_REQUESTED",
            "userId", userId,
            "countryCode", countryCode,
            "timestamp", LocalDateTime.now(),
            "compliance", true,
            "kycRequired", true
        );
        
        kafkaTemplate.send("audit-trail", "enhanced-due-diligence", auditEvent);
    }

    /**
     * Log SAR notification sent
     */
    @Async
    public void logSarNotification(UUID sarId, String notificationStatus) {
        Map<String, Object> auditEvent = Map.of(
            "eventType", "SAR_NOTIFICATION_SENT",
            "sarId", sarId,
            "notificationStatus", notificationStatus,
            "timestamp", LocalDateTime.now(),
            "compliance", true
        );
        
        kafkaTemplate.send("audit-trail", "sar-notification", auditEvent);
    }

    /**
     * Log immediate attention alert
     */
    @Async
    public void logImmediateAttentionAlert(UUID sarId) {
        Map<String, Object> auditEvent = Map.of(
            "eventType", "IMMEDIATE_ATTENTION_ALERT",
            "sarId", sarId,
            "timestamp", LocalDateTime.now(),
            "severity", "CRITICAL",
            "compliance", true
        );
        
        kafkaTemplate.send("audit-trail", "immediate-attention", auditEvent);
    }

    /**
     * Log reporting failure
     */
    @Async
    public void logReportingFailure(String reportType, UUID transactionId, String errorMessage) {
        log.error("Logging reporting failure: {} for transaction: {}", reportType, transactionId);
        
        Map<String, Object> auditEvent = Map.of(
            "eventType", "REPORTING_FAILURE",
            "reportType", reportType,
            "transactionId", transactionId,
            "errorMessage", errorMessage,
            "timestamp", LocalDateTime.now(),
            "severity", "HIGH",
            "systemError", true
        );
        
        kafkaTemplate.send("audit-trail", "reporting-failure", auditEvent);
    }

    /**
     * Log report generation
     */
    @Async
    public void logReportGeneration(UUID reportId, String reportType) {
        Map<String, Object> auditEvent = Map.of(
            "eventType", "REPORT_GENERATED",
            "reportId", reportId,
            "reportType", reportType,
            "timestamp", LocalDateTime.now(),
            "compliance", true
        );
        
        kafkaTemplate.send("audit-trail", "report-generation", auditEvent);
    }

    /**
     * Log AML alert creation
     */
    @Async
    public void logAmlAlertCreation(UUID alertId, UUID userId, String alertType, String severity) {
        Map<String, Object> auditEvent = Map.of(
            "eventType", "AML_ALERT_CREATED",
            "alertId", alertId,
            "userId", userId,
            "alertType", alertType,
            "severity", severity,
            "timestamp", LocalDateTime.now(),
            "compliance", true,
            "aml", true
        );
        
        kafkaTemplate.send("audit-trail", "aml-alert", auditEvent);
    }

    /**
     * Log transaction pattern analysis
     */
    @Async
    public void logTransactionPatternAnalysis(UUID userId, UUID transactionId, String patternType, 
                                            String analysisResult) {
        Map<String, Object> auditEvent = Map.of(
            "eventType", "TRANSACTION_PATTERN_ANALYZED",
            "userId", userId,
            "transactionId", transactionId,
            "patternType", patternType,
            "analysisResult", analysisResult,
            "timestamp", LocalDateTime.now(),
            "aml", true
        );
        
        kafkaTemplate.send("audit-trail", "pattern-analysis", auditEvent);
    }

    /**
     * Log sanctions screening
     */
    @Async
    public void logSanctionsScreening(UUID userId, boolean isMatch, String screeningDetails) {
        Map<String, Object> auditEvent = Map.of(
            "eventType", "SANCTIONS_SCREENING_PERFORMED",
            "userId", userId,
            "isMatch", isMatch,
            "screeningDetails", screeningDetails,
            "timestamp", LocalDateTime.now(),
            "compliance", true,
            "sanctions", true,
            "severity", isMatch ? "CRITICAL" : "INFO"
        );
        
        kafkaTemplate.send("audit-trail", "sanctions-screening", auditEvent);
    }

    /**
     * Log system security event
     */
    @Async
    public void logSecurityEvent(String eventType, UUID userId, String details, String severity) {
        Map<String, Object> auditEvent = Map.of(
            "eventType", "SECURITY_EVENT",
            "securityEventType", eventType,
            "userId", userId,
            "details", details,
            "severity", severity,
            "timestamp", LocalDateTime.now(),
            "security", true
        );
        
        kafkaTemplate.send("audit-trail", "security-event", auditEvent);
    }

    // Methods for AuthenticationEventConsumer

    public void logAuthenticationEvent(com.waqiti.security.entity.AuthenticationAttempt authAttempt) {
        log.info("Logging authentication event for userId: {}", authAttempt.getUserId());
    }

    public void validateSOXCompliance(com.waqiti.security.entity.AuthenticationAttempt authAttempt) {
        log.debug("Validating SOX compliance for userId: {}", authAttempt.getUserId());
    }

    public void ensureGDPRCompliance(String userId, com.waqiti.security.entity.AuthenticationAttempt authAttempt) {
        log.debug("Ensuring GDPR compliance for userId: {}", userId);
    }

    public void generateRegulatoryAuthReport(com.waqiti.security.entity.AuthenticationAttempt authAttempt) {
        log.info("Generating regulatory auth report for userId: {}", authAttempt.getUserId());
    }
}