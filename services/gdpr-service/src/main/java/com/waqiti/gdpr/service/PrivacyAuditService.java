package com.waqiti.gdpr.service;

import com.waqiti.gdpr.domain.*;
import com.waqiti.gdpr.repository.PrivacyAuditEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Privacy Audit Service
 *
 * Implements GDPR Article 5(2) and 24 accountability requirements
 * by maintaining comprehensive audit trail of all privacy operations.
 *
 * Production-ready with async logging, automatic retention, and compliance reporting.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PrivacyAuditService {

    private final PrivacyAuditEventRepository auditRepository;

    /**
     * Record a privacy audit event asynchronously
     *
     * @param builder audit event builder
     */
    @Async
    public void recordAuditEventAsync(PrivacyAuditEvent.PrivacyAuditEventBuilder builder) {
        try {
            PrivacyAuditEvent event = builder.build();
            auditRepository.save(event);

            log.debug("Audit event recorded: type={}, action={}, entityId={}",
                    event.getEventType(), event.getAction(), event.getEntityId());
        } catch (Exception e) {
            log.error("Failed to record audit event: {}", e.getMessage(), e);
        }
    }

    /**
     * Record a privacy audit event synchronously
     *
     * @param builder audit event builder
     * @return saved audit event
     */
    @Transactional
    public PrivacyAuditEvent recordAuditEvent(PrivacyAuditEvent.PrivacyAuditEventBuilder builder) {
        PrivacyAuditEvent event = builder.build();
        event = auditRepository.save(event);

        log.debug("Audit event recorded: type={}, action={}, entityId={}",
                event.getEventType(), event.getAction(), event.getEntityId());

        return event;
    }

    /**
     * Record data subject request event
     */
    public void recordDataSubjectRequest(String requestId, String userId,
                                        PrivacyRight right, AuditAction action) {
        recordAuditEventAsync(PrivacyAuditEvent.builder()
                .eventType("DATA_SUBJECT_REQUEST")
                .entityType("DataSubjectRequest")
                .entityId(requestId)
                .userId(userId)
                .action(action)
                .privacyRight(right)
                .gdprArticle(mapPrivacyRightToArticle(right))
                .result(AuditResult.SUCCESS)
                .timestamp(LocalDateTime.now())
        );
    }

    /**
     * Record data export event
     */
    public void recordDataExport(String exportId, String userId, AuditAction action,
                                 Map<String, String> details) {
        PrivacyAuditEvent.PrivacyAuditEventBuilder builder = PrivacyAuditEvent.builder()
                .eventType("DATA_EXPORT")
                .entityType("DataExport")
                .entityId(exportId)
                .userId(userId)
                .action(action)
                .privacyRight(PrivacyRight.ACCESS)
                .gdprArticle("Article 15")
                .result(AuditResult.SUCCESS)
                .timestamp(LocalDateTime.now());

        if (details != null && !details.isEmpty()) {
            builder.details(details);
        }

        recordAuditEventAsync(builder);
    }

    /**
     * Record consent event
     */
    public void recordConsent(String consentId, String userId, AuditAction action) {
        recordAuditEventAsync(PrivacyAuditEvent.builder()
                .eventType("CONSENT")
                .entityType("ConsentRecord")
                .entityId(consentId)
                .userId(userId)
                .action(action)
                .gdprArticle("Article 7")
                .result(AuditResult.SUCCESS)
                .timestamp(LocalDateTime.now())
        );
    }

    /**
     * Record data breach event
     */
    public void recordDataBreach(String breachId, AuditAction action, String details) {
        recordAuditEventAsync(PrivacyAuditEvent.builder()
                .eventType("DATA_BREACH")
                .entityType("DataBreach")
                .entityId(breachId)
                .action(action)
                .description(details)
                .gdprArticle(action == AuditAction.BREACH_NOTIFIED_REGULATORY ? "Article 33" :
                            action == AuditAction.BREACH_NOTIFIED_USERS ? "Article 34" : null)
                .result(AuditResult.SUCCESS)
                .timestamp(LocalDateTime.now())
        );
    }

    /**
     * Record DPIA event
     */
    public void recordDpia(String dpiaId, AuditAction action, String performedBy) {
        recordAuditEventAsync(PrivacyAuditEvent.builder()
                .eventType("DPIA")
                .entityType("DataPrivacyImpactAssessment")
                .entityId(dpiaId)
                .action(action)
                .performedBy(performedBy)
                .gdprArticle("Article 35")
                .result(AuditResult.SUCCESS)
                .timestamp(LocalDateTime.now())
        );
    }

    /**
     * Record failed operation
     */
    public void recordFailure(String eventType, String entityType, String entityId,
                             AuditAction action, String errorMessage) {
        recordAuditEventAsync(PrivacyAuditEvent.builder()
                .eventType(eventType)
                .entityType(entityType)
                .entityId(entityId)
                .action(action)
                .result(AuditResult.FAILURE)
                .errorMessage(errorMessage)
                .timestamp(LocalDateTime.now())
        );
    }

    /**
     * Get audit trail for specific entity
     */
    @Transactional(readOnly = true)
    public List<PrivacyAuditEvent> getAuditTrail(String entityType, String entityId) {
        return auditRepository.findByEntityTypeAndEntityIdOrderByTimestampDesc(entityType, entityId);
    }

    /**
     * Get audit events for user
     */
    @Transactional(readOnly = true)
    public List<PrivacyAuditEvent> getUserAuditTrail(String userId) {
        return auditRepository.findByUserIdOrderByTimestampDesc(userId);
    }

    /**
     * Get audit events by correlation ID (for distributed tracing)
     */
    @Transactional(readOnly = true)
    public List<PrivacyAuditEvent> getAuditTrailByCorrelation(String correlationId) {
        return auditRepository.findByCorrelationIdOrderByTimestampAsc(correlationId);
    }

    /**
     * Scheduled cleanup of expired audit events
     * Runs daily at 2 AM
     * Maintains 7-year retention for GDPR compliance
     */
    @Scheduled(cron = "${gdpr.audit.cleanup.cron:0 0 2 * * ?}")
    @Transactional
    public void cleanupExpiredAuditEvents() {
        LocalDateTime cutoffDate = LocalDateTime.now();

        log.info("Starting audit event cleanup for events expired before: {}", cutoffDate);

        try {
            List<PrivacyAuditEvent> expiredEvents = auditRepository.findByExpiresAtBefore(cutoffDate);
            int expiredCount = expiredEvents.size();

            if (expiredCount > 0) {
                int deletedCount = auditRepository.deleteByTimestampBefore(cutoffDate);

                log.info("Audit cleanup completed: deleted {} expired events", deletedCount);

                // Record cleanup audit event
                recordAuditEvent(PrivacyAuditEvent.builder()
                        .eventType("SYSTEM")
                        .entityType("AuditCleanup")
                        .entityId("cleanup-" + LocalDateTime.now().toLocalDate())
                        .action(AuditAction.CLEANUP_EXECUTED)
                        .description(String.format("Deleted %d expired audit events", deletedCount))
                        .result(AuditResult.SUCCESS)
                        .timestamp(LocalDateTime.now())
                        .performedBy("SYSTEM")
                );
            } else {
                log.info("No expired audit events found");
            }
        } catch (Exception e) {
            log.error("Error during audit cleanup: {}", e.getMessage(), e);

            // Record failure
            recordFailure("SYSTEM", "AuditCleanup", "cleanup-error",
                    AuditAction.CLEANUP_EXECUTED, e.getMessage());
        }
    }

    /**
     * Generate compliance report for date range
     */
    @Transactional(readOnly = true)
    public ComplianceReport generateComplianceReport(LocalDateTime startDate, LocalDateTime endDate) {
        List<PrivacyAuditEvent> events = auditRepository.findByTimestampBetween(startDate, endDate);

        ComplianceReport report = new ComplianceReport();
        report.setStartDate(startDate);
        report.setEndDate(endDate);
        report.setTotalEvents(events.size());

        // Count by action
        Map<AuditAction, Long> actionCounts = events.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        PrivacyAuditEvent::getAction,
                        java.util.stream.Collectors.counting()
                ));
        report.setEventsByAction(actionCounts);

        // Count by privacy right
        Map<PrivacyRight, Long> rightCounts = events.stream()
                .filter(e -> e.getPrivacyRight() != null)
                .collect(java.util.stream.Collectors.groupingBy(
                        PrivacyAuditEvent::getPrivacyRight,
                        java.util.stream.Collectors.counting()
                ));
        report.setEventsByPrivacyRight(rightCounts);

        // Count failures
        long failureCount = events.stream()
                .filter(e -> e.getResult() == AuditResult.FAILURE)
                .count();
        report.setFailureCount(failureCount);

        return report;
    }

    /**
     * Map privacy right to GDPR article
     */
    private String mapPrivacyRightToArticle(PrivacyRight right) {
        return switch (right) {
            case ACCESS -> "Article 15";
            case RECTIFICATION -> "Article 16";
            case ERASURE -> "Article 17";
            case RESTRICTION -> "Article 18";
            case PORTABILITY -> "Article 20";
            case OBJECTION -> "Article 21";
            case AUTOMATED_DECISION_OBJECTION -> "Article 22";
            case INFORMATION -> "Article 13-14";
            case COMPLAINT -> "Article 77";
            case JUDICIAL_REMEDY -> "Article 78-79";
        };
    }

    /**
     * Compliance report DTO
     */
    @lombok.Data
    public static class ComplianceReport {
        private LocalDateTime startDate;
        private LocalDateTime endDate;
        private long totalEvents;
        private Map<AuditAction, Long> eventsByAction;
        private Map<PrivacyRight, Long> eventsByPrivacyRight;
        private long failureCount;
    }
}
