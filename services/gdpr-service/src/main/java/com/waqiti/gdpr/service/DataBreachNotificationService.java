package com.waqiti.gdpr.service;

import com.waqiti.gdpr.domain.*;
import com.waqiti.gdpr.repository.DataBreachRepository;
import com.waqiti.gdpr.repository.PrivacyAuditEventRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Data Breach Notification Service
 *
 * Implements GDPR Articles 33-34 breach notification requirements:
 * - Article 33: Notification to supervisory authority within 72 hours
 * - Article 34: Communication to data subjects without undue delay if high risk
 *
 * Production-ready with deadline monitoring, automated notifications, and compliance tracking.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataBreachNotificationService {

    private final DataBreachRepository dataBreachRepository;
    private final PrivacyAuditEventRepository auditRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    @Value("${gdpr.breach.notification.regulatory-email:dpa@supervisory-authority.eu}")
    private String regulatoryNotificationEmail;

    @Value("${gdpr.breach.notification.dpo-email:dpo@company.com}")
    private String dpoEmail;

    @Value("${gdpr.breach.notification.legal-email:legal@company.com}")
    private String legalEmail;

    @Value("${gdpr.breach.notification.user-notification-enabled:true}")
    private boolean userNotificationEnabled;

    /**
     * Report a new data breach
     *
     * @param breachReport breach details
     * @return created DataBreach entity
     */
    @Transactional
    public DataBreach reportDataBreach(DataBreachReport breachReport) {
        log.warn("Data breach reported: type={}, severity={}, affectedUsers={}",
                breachReport.getBreachType(), breachReport.getSeverity(), breachReport.getAffectedUserCount());

        // Create breach record
        DataBreach breach = DataBreach.builder()
                .breachType(breachReport.getBreachType())
                .severity(breachReport.getSeverity())
                .description(breachReport.getDescription())
                .discoveredAt(breachReport.getDiscoveredAt() != null ? breachReport.getDiscoveredAt() : LocalDateTime.now())
                .breachOccurredAt(breachReport.getBreachOccurredAt())
                .reportedBy(breachReport.getReportedBy())
                .affectedUserCount(breachReport.getAffectedUserCount())
                .affectedDataCategories(breachReport.getAffectedDataCategories())
                .status(BreachStatus.REPORTED)
                .likelyConsequences(breachReport.getLikelyConsequences())
                .mitigationMeasures(breachReport.getMitigationMeasures())
                .attackVector(breachReport.getAttackVector())
                .vulnerabilityExploited(breachReport.getVulnerabilityExploited())
                .systemsAffected(breachReport.getSystemsAffected())
                .dataCompromised(breachReport.getDataCompromised())
                .createdAt(LocalDateTime.now())
                .build();

        // Perform risk assessment
        RiskAssessment riskAssessment = performRiskAssessment(breach);
        breach.setRiskAssessment(riskAssessment);

        // Determine notification requirements based on risk
        breach.setRequiresRegulatoryNotification(requiresRegulatoryNotification(breach));
        breach.setRequiresUserNotification(requiresUserNotification(breach));

        // Save breach
        breach = dataBreachRepository.save(breach);

        // Record audit event
        recordAuditEvent(AuditAction.BREACH_REPORTED, breach, null);

        // Notify DPO immediately
        notifyDpo(breach);

        // Publish breach event
        publishBreachEvent("breach.reported", breach);

        // Metrics
        meterRegistry.counter("gdpr.breaches.reported",
                "type", breach.getBreachType().toString(),
                "severity", breach.getSeverity().toString()
        ).increment();

        log.info("Data breach recorded: id={}, severity={}, regulatoryNotification={}, userNotification={}",
                breach.getId(), breach.getSeverity(), breach.isRequiresRegulatoryNotification(),
                breach.isRequiresUserNotification());

        return breach;
    }

    /**
     * Notify regulatory authority (Article 33)
     *
     * @param breachId breach ID
     * @param reference external reference number
     * @return updated breach
     */
    @Transactional
    public DataBreach notifyRegulatoryAuthority(String breachId, String reference) {
        DataBreach breach = dataBreachRepository.findById(breachId)
                .orElseThrow(() -> new IllegalArgumentException("Breach not found: " + breachId));

        if (!breach.isRequiresRegulatoryNotification()) {
            throw new IllegalStateException("Breach does not require regulatory notification");
        }

        if (breach.getRegulatoryNotifiedAt() != null) {
            log.warn("Regulatory authority already notified for breach: {}", breachId);
            return breach;
        }

        // Mark as notified
        breach.markRegulatoryNotified(reference);
        breach = dataBreachRepository.save(breach);

        // Record audit
        recordAuditEvent(AuditAction.BREACH_NOTIFIED_REGULATORY, breach, reference);

        // Publish event
        publishBreachEvent("breach.regulatory.notified", breach);

        // Metrics
        meterRegistry.counter("gdpr.breaches.regulatory.notified").increment();

        // Check if within 72-hour deadline
        if (breach.isRegulatoryDeadlineBreached()) {
            log.error("COMPLIANCE VIOLATION: Regulatory notification sent AFTER 72-hour deadline: breachId={}", breachId);
            meterRegistry.counter("gdpr.breaches.regulatory.deadline.breached").increment();
        } else {
            log.info("Regulatory authority notified within 72 hours: breachId={}, reference={}", breachId, reference);
        }

        return breach;
    }

    /**
     * Notify affected users (Article 34)
     *
     * @param breachId breach ID
     * @param notificationCount number of users notified
     * @param method notification method
     * @return updated breach
     */
    @Transactional
    public DataBreach notifyAffectedUsers(String breachId, int notificationCount, String method) {
        DataBreach breach = dataBreachRepository.findById(breachId)
                .orElseThrow(() -> new IllegalArgumentException("Breach not found: " + breachId));

        if (!breach.isRequiresUserNotification()) {
            throw new IllegalStateException("Breach does not require user notification");
        }

        if (breach.getUsersNotifiedAt() != null) {
            log.warn("Users already notified for breach: {}", breachId);
            return breach;
        }

        // Mark users as notified
        breach.markUsersNotified(notificationCount, method);
        breach = dataBreachRepository.save(breach);

        // Record audit
        recordAuditEvent(AuditAction.BREACH_NOTIFIED_USERS, breach,
                String.format("Notified %d users via %s", notificationCount, method));

        // Publish event
        publishBreachEvent("breach.users.notified", breach);

        // Metrics
        meterRegistry.counter("gdpr.breaches.users.notified",
                "method", method
        ).increment();
        meterRegistry.counter("gdpr.breaches.users.notified.count")
                .increment(notificationCount);

        log.info("Affected users notified: breachId={}, count={}, method={}", breachId, notificationCount, method);

        return breach;
    }

    /**
     * Mark breach as contained
     */
    @Transactional
    public DataBreach containBreach(String breachId, String containmentActions) {
        DataBreach breach = dataBreachRepository.findById(breachId)
                .orElseThrow(() -> new IllegalArgumentException("Breach not found: " + breachId));

        breach.markContained(containmentActions);
        breach = dataBreachRepository.save(breach);

        recordAuditEvent(AuditAction.BREACH_CONTAINED, breach, containmentActions);
        publishBreachEvent("breach.contained", breach);

        meterRegistry.counter("gdpr.breaches.contained").increment();

        log.info("Breach contained: breachId={}", breachId);

        return breach;
    }

    /**
     * Mark breach as resolved
     */
    @Transactional
    public DataBreach resolveBreach(String breachId, String recoveryActions) {
        DataBreach breach = dataBreachRepository.findById(breachId)
                .orElseThrow(() -> new IllegalArgumentException("Breach not found: " + breachId));

        breach.markResolved(recoveryActions);
        breach = dataBreachRepository.save(breach);

        recordAuditEvent(AuditAction.BREACH_RESOLVED, breach, recoveryActions);
        publishBreachEvent("breach.resolved", breach);

        meterRegistry.counter("gdpr.breaches.resolved",
                "severity", breach.getSeverity().toString()
        ).increment();

        log.info("Breach resolved: breachId={}, duration={}",
                breachId, java.time.Duration.between(breach.getDiscoveredAt(), breach.getClosedAt()));

        return breach;
    }

    /**
     * Scheduled job to monitor breach notification deadlines
     * Runs every 15 minutes
     */
    @Scheduled(cron = "${gdpr.breach.deadline-check.cron:0 */15 * * * *}")
    public void monitorBreachDeadlines() {
        LocalDateTime now = LocalDateTime.now();

        // Check regulatory notification deadlines (72 hours)
        List<DataBreach> regulatoryDue = dataBreachRepository.findRegulatoryNotificationsDue(now);
        if (!regulatoryDue.isEmpty()) {
            log.warn("URGENT: {} breaches require regulatory notification", regulatoryDue.size());

            for (DataBreach breach : regulatoryDue) {
                long hoursRemaining = java.time.Duration.between(now, breach.getRegulatoryNotificationDeadline()).toHours();

                if (hoursRemaining <= 0) {
                    log.error("COMPLIANCE VIOLATION: Regulatory notification deadline BREACHED for breach: {}", breach.getId());
                    meterRegistry.counter("gdpr.breaches.regulatory.deadline.breached").increment();
                    alertManagement(breach, "REGULATORY NOTIFICATION DEADLINE BREACHED");
                } else if (hoursRemaining <= 12) {
                    log.warn("CRITICAL: Only {} hours remaining for regulatory notification: breachId={}",
                            hoursRemaining, breach.getId());
                    alertManagement(breach, String.format("URGENT: %d hours until regulatory deadline", hoursRemaining));
                }
            }
        }

        // Check user notification deadlines
        List<DataBreach> usersDue = dataBreachRepository.findUserNotificationsDue(now);
        if (!usersDue.isEmpty()) {
            log.warn("URGENT: {} breaches require user notification", usersDue.size());

            for (DataBreach breach : usersDue) {
                if (breach.isUserNotificationDeadlineBreached()) {
                    log.error("User notification deadline BREACHED for breach: {}", breach.getId());
                    alertManagement(breach, "USER NOTIFICATION DEADLINE BREACHED");
                } else {
                    alertManagement(breach, "User notification required");
                }
            }
        }
    }

    /**
     * Perform risk assessment for breach
     */
    private RiskAssessment performRiskAssessment(DataBreach breach) {
        RiskAssessment assessment = RiskAssessment.builder()
                .assessedAt(LocalDateTime.now())
                .assessedBy("AUTOMATED_SYSTEM")
                .assessmentMethodology("GDPR_BREACH_RISK_MATRIX")
                .build();

        // Calculate likelihood score (1-10)
        int likelihoodScore = calculateLikelihoodScore(breach);
        assessment.setLikelihoodScore(likelihoodScore);

        // Calculate impact score (1-10)
        int impactScore = calculateImpactScore(breach);
        assessment.setImpactScore(impactScore);

        // Calculate overall risk score
        assessment.calculateRiskScore();

        // Determine if DPIA required
        assessment.setRequiresDpia(assessment.shouldRequireDpia());

        return assessment;
    }

    private int calculateLikelihoodScore(DataBreach breach) {
        int score = 5; // baseline

        // Increase for certain breach types
        if (breach.getBreachType() == BreachType.RANSOMWARE ||
            breach.getBreachType() == BreachType.CONFIDENTIALITY_BREACH) {
            score += 2;
        }

        // Increase for large user count
        if (breach.getAffectedUserCount() != null && breach.getAffectedUserCount() > 1000) {
            score += 2;
        }

        return Math.min(score, 10);
    }

    private int calculateImpactScore(DataBreach breach) {
        int score = 5; // baseline

        // Increase for severity
        switch (breach.getSeverity()) {
            case CRITICAL -> score += 5;
            case HIGH -> score += 3;
            case MEDIUM -> score += 1;
            default -> score += 0;
        }

        // Increase for large user count
        if (breach.getAffectedUserCount() != null) {
            if (breach.getAffectedUserCount() > 10000) score += 3;
            else if (breach.getAffectedUserCount() > 1000) score += 2;
            else if (breach.getAffectedUserCount() > 100) score += 1;
        }

        return Math.min(score, 10);
    }

    /**
     * Determine if regulatory notification required (Article 33)
     * Required unless unlikely to result in risk to rights and freedoms
     */
    private boolean requiresRegulatoryNotification(DataBreach breach) {
        // HIGH and CRITICAL severity always require notification
        if (breach.getSeverity() == BreachSeverity.HIGH ||
            breach.getSeverity() == BreachSeverity.CRITICAL) {
            return true;
        }

        // Large number of affected users requires notification
        if (breach.getAffectedUserCount() != null && breach.getAffectedUserCount() > 100) {
            return true;
        }

        // Certain breach types always require notification
        if (breach.getBreachType() == BreachType.RANSOMWARE ||
            breach.getBreachType() == BreachType.CONFIDENTIALITY_BREACH) {
            return true;
        }

        // Default to requiring notification (err on side of compliance)
        return true;
    }

    /**
     * Determine if user notification required (Article 34)
     * Required if likely to result in high risk to rights and freedoms
     */
    private boolean requiresUserNotification(DataBreach breach) {
        // CRITICAL severity always requires user notification
        if (breach.getSeverity() == BreachSeverity.CRITICAL) {
            return true;
        }

        // HIGH risk level requires user notification
        if (breach.getRiskAssessment() != null &&
            breach.getRiskAssessment().getRiskLevel() == RiskLevel.HIGH) {
            return true;
        }

        // Large number of affected users with HIGH severity
        if (breach.getSeverity() == BreachSeverity.HIGH &&
            breach.getAffectedUserCount() != null &&
            breach.getAffectedUserCount() > 1000) {
            return true;
        }

        return false;
    }

    private void notifyDpo(DataBreach breach) {
        breach.setDpoNotifiedAt(LocalDateTime.now());
        dataBreachRepository.save(breach);

        // Send notification (implementation depends on notification service)
        publishBreachEvent("breach.dpo.notification", breach);

        log.info("DPO notified of breach: {}", breach.getId());
    }

    private void alertManagement(DataBreach breach, String message) {
        Map<String, Object> alert = new HashMap<>();
        alert.put("breachId", breach.getId());
        alert.put("severity", breach.getSeverity());
        alert.put("message", message);
        alert.put("timestamp", LocalDateTime.now());

        publishBreachEvent("breach.management.alert", alert);

        log.warn("Management alert sent: breachId={}, message={}", breach.getId(), message);
    }

    private void recordAuditEvent(AuditAction action, DataBreach breach, String details) {
        PrivacyAuditEvent auditEvent = PrivacyAuditEvent.builder()
                .eventType("DATA_BREACH")
                .entityType("DataBreach")
                .entityId(breach.getId())
                .action(action)
                .description(details)
                .timestamp(LocalDateTime.now())
                .result(AuditResult.SUCCESS)
                .gdprArticle(action == AuditAction.BREACH_NOTIFIED_REGULATORY ? "Article 33" :
                            action == AuditAction.BREACH_NOTIFIED_USERS ? "Article 34" : null)
                .build();

        auditRepository.save(auditEvent);
    }

    private void publishBreachEvent(String topic, Object payload) {
        try {
            kafkaTemplate.send("gdpr." + topic, payload);
        } catch (Exception e) {
            log.error("Failed to publish breach event to topic {}: {}", topic, e.getMessage());
        }
    }

    /**
     * Breach report DTO
     */
    @lombok.Data
    @lombok.Builder
    public static class DataBreachReport {
        private BreachType breachType;
        private BreachSeverity severity;
        private String description;
        private LocalDateTime discoveredAt;
        private LocalDateTime breachOccurredAt;
        private String reportedBy;
        private Integer affectedUserCount;
        private java.util.Set<String> affectedDataCategories;
        private String likelyConsequences;
        private String mitigationMeasures;
        private String attackVector;
        private String vulnerabilityExploited;
        private String systemsAffected;
        private String dataCompromised;
    }
}
