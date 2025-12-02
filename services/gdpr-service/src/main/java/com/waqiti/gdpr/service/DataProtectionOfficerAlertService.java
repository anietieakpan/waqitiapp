package com.waqiti.gdpr.service;

import com.waqiti.common.notification.DlqNotificationAdapter;
import com.waqiti.common.service.DlqEscalationService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Data Protection Officer Alert Service
 * Handles critical alerts and escalations to the Data Protection Officer (DPO)
 * Production-ready implementation with fallback mechanisms
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataProtectionOfficerAlertService {

    private final DlqNotificationAdapter notificationAdapter;
    private final DlqEscalationService escalationService;
    private final MeterRegistry meterRegistry;

    /**
     * Send critical DPO alert for GDPR violations or data breaches
     */
    @Async
    public void sendCriticalAlert(String alertType, String message,
                                  Map<String, String> details, String correlationId) {
        log.error("CRITICAL DPO ALERT: type={} message={} correlationId={}",
                alertType, message, correlationId);

        Map<String, Object> alertData = new HashMap<>(details);
        alertData.put("alertType", alertType);
        alertData.put("severity", "CRITICAL");
        alertData.put("message", message);
        alertData.put("correlationId", correlationId);
        alertData.put("timestamp", Instant.now());
        alertData.put("requiresImmediateAction", true);
        alertData.put("targetRole", "DATA_PROTECTION_OFFICER");
        alertData.put("regulatoryImpact", "HIGH");
        alertData.put("escalationLevel", "DPO");

        try {
            // Primary notification via notification adapter
            notificationAdapter.sendNotification(
                    "DPO_CRITICAL_ALERT",
                    String.format("CRITICAL DPO Alert: %s", alertType),
                    message,
                    alertData,
                    correlationId
            );

            // Escalate through escalation service for redundancy
            escalationService.escalate(
                    "CRITICAL",
                    "DPO_ALERT",
                    alertType,
                    message,
                    alertData,
                    correlationId
            );

            recordMetric("dpo_critical_alerts_total", "alert_type", alertType);

            log.error("DPO critical alert sent: type={} correlationId={}", alertType, correlationId);

        } catch (Exception e) {
            log.error("CRITICAL: Failed to send DPO alert: type={} correlationId={}",
                    alertType, correlationId, e);

            // Fallback escalation
            try {
                escalationService.escalate(
                        "CRITICAL",
                        "DPO_ALERT_FALLBACK",
                        "DPO Alert Failed",
                        String.format("Original alert type: %s - %s", alertType, message),
                        alertData,
                        correlationId
                );
            } catch (Exception fallbackException) {
                log.error("CRITICAL: DPO alert fallback also failed: correlationId={}",
                        correlationId, fallbackException);
            }
        }
    }

    /**
     * Escalate export failure to DPO
     */
    @Async
    public void escalateExportFailure(String exportId, String subjectId,
                                     String failureReason, String correlationId) {
        log.error("Escalating export failure to DPO: exportId={} subjectId={} correlationId={}",
                exportId, subjectId, correlationId);

        Map<String, String> details = new HashMap<>();
        details.put("exportId", exportId);
        details.put("subjectId", subjectId);
        details.put("failureReason", failureReason);
        details.put("action", "Manual DPO intervention required");
        details.put("gdprImpact", "Potential Article 15 compliance breach");
        details.put("deadlineRisk", "30-day GDPR deadline may be at risk");

        sendCriticalAlert(
                "EXPORT_FAILURE_DPO_ESCALATION",
                String.format("Data export failure requires DPO intervention - Export: %s", exportId),
                details,
                correlationId
        );

        recordMetric("dpo_export_failure_escalations_total");

        log.error("Export failure escalated to DPO: exportId={} correlationId={}",
                exportId, correlationId);
    }

    /**
     * Send DPO alert for data breach
     */
    @Async
    public void alertDataBreach(String exportId, String subjectId,
                               String breachType, String correlationId) {
        log.error("CRITICAL: Alerting DPO of data breach: exportId={} subjectId={} breach={} correlationId={}",
                exportId, subjectId, breachType, correlationId);

        Map<String, String> details = new HashMap<>();
        details.put("exportId", exportId);
        details.put("subjectId", subjectId);
        details.put("breachType", breachType);
        details.put("action", "Immediate breach assessment and notification procedure required");
        details.put("regulatoryRequirement", "GDPR Article 33 & 34 - 72-hour notification deadline");
        details.put("potentialImpact", "Supervisory authority notification may be required");

        sendCriticalAlert(
                "DATA_BREACH_NOTIFICATION",
                String.format("CRITICAL: Data breach detected in export process - Export: %s - Type: %s",
                        exportId, breachType),
                details,
                correlationId
        );

        recordMetric("dpo_data_breach_alerts_total", "breach_type", breachType);

        log.error("Data breach alert sent to DPO: exportId={} correlationId={}",
                exportId, correlationId);
    }

    /**
     * Alert DPO of GDPR deadline risk
     */
    @Async
    public void alertDeadlineRisk(String exportId, String subjectId,
                                 int daysRemaining, String correlationId) {
        log.warn("Alerting DPO of deadline risk: exportId={} subjectId={} daysRemaining={} correlationId={}",
                exportId, subjectId, daysRemaining, correlationId);

        String severity = daysRemaining <= 3 ? "CRITICAL" : "HIGH";

        Map<String, String> details = new HashMap<>();
        details.put("exportId", exportId);
        details.put("subjectId", subjectId);
        details.put("daysRemaining", String.valueOf(daysRemaining));
        details.put("action", "Expedite export processing to meet GDPR 30-day requirement");
        details.put("regulatoryRisk", daysRemaining <= 3 ? "IMMINENT" : "MEDIUM");

        Map<String, Object> alertData = new HashMap<>(details);
        alertData.put("alertType", "GDPR_DEADLINE_RISK");
        alertData.put("severity", severity);
        alertData.put("correlationId", correlationId);
        alertData.put("timestamp", Instant.now());

        try {
            notificationAdapter.sendNotification(
                    "GDPR_DEADLINE_RISK",
                    String.format("%s: GDPR deadline risk - %d days remaining", severity, daysRemaining),
                    String.format("Export %s is approaching GDPR 30-day deadline with %d days remaining",
                            exportId, daysRemaining),
                    alertData,
                    correlationId
            );

            recordMetric("dpo_deadline_risk_alerts_total",
                    "days_remaining", String.valueOf(daysRemaining));

            log.warn("DPO deadline risk alert sent: exportId={} daysRemaining={} correlationId={}",
                    exportId, daysRemaining, correlationId);

        } catch (Exception e) {
            log.error("Failed to send DPO deadline risk alert: exportId={} correlationId={}",
                    exportId, correlationId, e);
        }
    }

    /**
     * Alert DPO for manual review queue issues
     */
    @Async
    public void alertManualReviewBacklog(int backlogCount, int overdueCount,
                                        String correlationId) {
        log.warn("Alerting DPO of manual review backlog: backlog={} overdue={} correlationId={}",
                backlogCount, overdueCount, correlationId);

        Map<String, String> details = new HashMap<>();
        details.put("backlogCount", String.valueOf(backlogCount));
        details.put("overdueCount", String.valueOf(overdueCount));
        details.put("action", "Review backlog and allocate additional resources");
        details.put("complianceRisk", overdueCount > 0 ? "HIGH" : "MEDIUM");

        Map<String, Object> alertData = new HashMap<>(details);
        alertData.put("alertType", "MANUAL_REVIEW_BACKLOG");
        alertData.put("severity", overdueCount > 0 ? "HIGH" : "MEDIUM");
        alertData.put("correlationId", correlationId);
        alertData.put("timestamp", Instant.now());

        try {
            notificationAdapter.sendNotification(
                    "MANUAL_REVIEW_BACKLOG",
                    "GDPR Manual Review Backlog Alert",
                    String.format("Manual review backlog: %d pending (%d overdue)",
                            backlogCount, overdueCount),
                    alertData,
                    correlationId
            );

            recordMetric("dpo_backlog_alerts_total");

            log.warn("DPO manual review backlog alert sent: backlog={} overdue={} correlationId={}",
                    backlogCount, overdueCount, correlationId);

        } catch (Exception e) {
            log.error("Failed to send DPO backlog alert: correlationId={}", correlationId, e);
        }
    }

    private void recordMetric(String metricName, String... tags) {
        Counter.Builder builder = Counter.builder(metricName);

        for (int i = 0; i < tags.length; i += 2) {
            if (i + 1 < tags.length) {
                builder.tag(tags[i], tags[i + 1]);
            }
        }

        builder.register(meterRegistry).increment();
    }
}
