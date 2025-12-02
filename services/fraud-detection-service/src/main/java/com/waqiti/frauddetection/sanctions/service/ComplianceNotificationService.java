package com.waqiti.frauddetection.sanctions.service;

import com.waqiti.common.kafka.KafkaProducerService;
import com.waqiti.common.notification.NotificationService;
import com.waqiti.common.notification.model.*;
import com.waqiti.frauddetection.sanctions.entity.SanctionsCheckRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

/**
 * Service for sending compliance notifications related to sanctions screening.
 *
 * Features:
 * - Email notifications to compliance team
 * - Slack/Teams integration for real-time alerts
 * - Dashboard notifications
 * - Escalation workflows
 *
 * @author Waqiti Platform
 * @version 1.0
 * @since 2025-10-01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ComplianceNotificationService {

    private final NotificationService notificationService;
    private final KafkaProducerService kafkaProducerService;

    @Value("${waqiti.compliance.email:compliance@example.com}")
    private String complianceEmail;

    @Value("${waqiti.compliance.slack.webhook:}")
    private String slackWebhook;

    @Value("${waqiti.compliance.senior-email:compliance-director@example.com}")
    private String seniorComplianceEmail;

    @Value("${waqiti.compliance.sms.phone:}")
    private String complianceSmsPhone;

    /**
     * Notify compliance team of a sanctions match.
     *
     * @param checkRecord The sanctions check record with match details
     */
    public void notifySanctionsMatch(SanctionsCheckRecord checkRecord) {
        log.info("COMPLIANCE ALERT: Sanctions match detected - Check ID: {}, Entity: {}, Risk: {}",
                checkRecord.getId(),
                checkRecord.getEntityId(),
                checkRecord.getRiskLevel());

        // Send email notification
        sendEmailNotification(checkRecord);

        // Send real-time notification (Slack/Teams)
        sendRealtimeNotification(checkRecord);

        // Create dashboard alert
        createDashboardAlert(checkRecord);

        // If critical risk, escalate immediately
        if (checkRecord.getRiskLevel() == SanctionsCheckRecord.RiskLevel.CRITICAL) {
            escalateToSeniorCompliance(checkRecord);
        }
    }

    /**
     * Notify compliance team of manual resolution.
     *
     * @param checkRecord The resolved sanctions check record
     */
    public void notifyManualResolution(SanctionsCheckRecord checkRecord) {
        log.info("COMPLIANCE: Manual resolution completed - Check ID: {}, Resolution: {}",
                checkRecord.getId(),
                checkRecord.getResolution());

        // Send resolution notification
        sendResolutionNotification(checkRecord);

        // Update audit trail
        updateAuditTrail(checkRecord);
    }

    /**
     * Send email notification to compliance team.
     */
    private void sendEmailNotification(SanctionsCheckRecord checkRecord) {
        log.info("COMPLIANCE: Sending email notification for sanctions match: {}", checkRecord.getId());

        try {
            EmailNotification email = EmailNotification.builder()
                .to(complianceEmail)
                .subject(String.format("🚨 SANCTIONS MATCH DETECTED - %s Risk", checkRecord.getRiskLevel()))
                .body(buildEmailBody(checkRecord))
                .priority(EmailPriority.HIGH)
                .category("COMPLIANCE_ALERT")
                .metadata(Map.of(
                    "checkId", checkRecord.getId().toString(),
                    "entityId", checkRecord.getEntityId(),
                    "riskLevel", checkRecord.getRiskLevel().toString()
                ))
                .timestamp(Instant.now())
                .build();

            notificationService.sendEmail(email);
            log.info("Compliance email sent successfully for check: {}", checkRecord.getId());
        } catch (Exception e) {
            log.error("Failed to send compliance email for check: {}", checkRecord.getId(), e);
        }
    }

    /**
     * Send real-time notification (Slack/Teams).
     */
    private void sendRealtimeNotification(SanctionsCheckRecord checkRecord) {
        log.info("COMPLIANCE: Sending real-time notification for sanctions match: {}", checkRecord.getId());

        try {
            SlackNotification slack = SlackNotification.builder()
                .webhook(slackWebhook)
                .channel("#compliance-alerts")
                .username("Compliance Bot")
                .icon(":warning:")
                .text(String.format("🚨 *SANCTIONS MATCH DETECTED*\\n" +
                    "Risk Level: *%s*\\n" +
                    "Entity ID: `%s`\\n" +
                    "Check ID: `%s`\\n" +
                    "List: %s\\n" +
                    "Action Required: Manual Review",
                    checkRecord.getRiskLevel(),
                    checkRecord.getEntityId(),
                    checkRecord.getId(),
                    checkRecord.getListName()))
                .timestamp(Instant.now())
                .build();

            kafkaProducerService.sendMessage("slack-notifications", checkRecord.getId().toString(), slack);
            log.info("Slack notification sent successfully for check: {}", checkRecord.getId());
        } catch (Exception e) {
            log.error("Failed to send Slack notification for check: {}", checkRecord.getId(), e);
        }
    }

    /**
     * Create dashboard alert.
     */
    private void createDashboardAlert(SanctionsCheckRecord checkRecord) {
        log.info("COMPLIANCE: Creating dashboard alert for sanctions match: {}", checkRecord.getId());

        try {
            InAppNotification alert = InAppNotification.builder()
                .userId("COMPLIANCE_TEAM")
                .title("Sanctions Match Detected")
                .message(String.format("Entity %s matched %s risk sanctions list",
                    checkRecord.getEntityId(), checkRecord.getRiskLevel()))
                .type(NotificationType.COMPLIANCE_ALERT)
                .priority(NotificationPriority.HIGH)
                .actionRequired(true)
                .actionUrl(String.format("/compliance/sanctions/%s", checkRecord.getId()))
                .metadata(Map.of(
                    "checkId", checkRecord.getId().toString(),
                    "riskLevel", checkRecord.getRiskLevel().toString(),
                    "entityId", checkRecord.getEntityId()
                ))
                .expiresAt(Instant.now().plusSeconds(86400)) // 24 hours
                .timestamp(Instant.now())
                .build();

            kafkaProducerService.sendMessage("in-app-notifications", checkRecord.getId().toString(), alert);
            log.info("Dashboard alert created successfully for check: {}", checkRecord.getId());
        } catch (Exception e) {
            log.error("Failed to create dashboard alert for check: {}", checkRecord.getId(), e);
        }
    }

    /**
     * Escalate to senior compliance officer.
     */
    private void escalateToSeniorCompliance(SanctionsCheckRecord checkRecord) {
        log.warn("COMPLIANCE ESCALATION: Critical sanctions match - Check ID: {}", checkRecord.getId());

        try {
            // Send email to senior compliance
            EmailNotification seniorEmail = EmailNotification.builder()
                .to(seniorComplianceEmail)
                .subject(String.format("🔴 CRITICAL SANCTIONS MATCH - Immediate Action Required"))
                .body(buildEscalationEmailBody(checkRecord))
                .priority(EmailPriority.URGENT)
                .category("COMPLIANCE_ESCALATION")
                .timestamp(Instant.now())
                .build();

            notificationService.sendEmail(seniorEmail);

            // Send SMS if configured
            if (complianceSmsPhone != null && !complianceSmsPhone.isEmpty()) {
                SmsNotification sms = SmsNotification.builder()
                    .phoneNumber(complianceSmsPhone)
                    .message(String.format("CRITICAL: Sanctions match detected - Entity: %s, Check ID: %s - Login immediately",
                        checkRecord.getEntityId(), checkRecord.getId()))
                    .priority(SmsPriority.HIGH)
                    .timestamp(Instant.now())
                    .build();

                notificationService.sendSms(sms);
            }

            // Create escalation record
            ComplianceEscalation escalation = ComplianceEscalation.builder()
                .checkId(checkRecord.getId().toString())
                .escalationType("CRITICAL_SANCTIONS_MATCH")
                .escalatedTo(seniorComplianceEmail)
                .reason("Critical risk level sanctions match requires immediate senior review")
                .timestamp(Instant.now())
                .status("PENDING")
                .build();

            kafkaProducerService.sendMessage("compliance-escalations", checkRecord.getId().toString(), escalation);

            log.info("Escalated to senior compliance successfully for check: {}", checkRecord.getId());
        } catch (Exception e) {
            log.error("Failed to escalate to senior compliance for check: {}", checkRecord.getId(), e);
        }
    }

    /**
     * Send resolution notification.
     */
    private void sendResolutionNotification(SanctionsCheckRecord checkRecord) {
        log.info("COMPLIANCE: Sending resolution notification: {}", checkRecord.getId());

        try {
            EmailNotification email = EmailNotification.builder()
                .to(complianceEmail)
                .subject(String.format("✅ Sanctions Check Resolved - Check ID: %s", checkRecord.getId()))
                .body(buildResolutionEmailBody(checkRecord))
                .priority(EmailPriority.NORMAL)
                .category("COMPLIANCE_RESOLUTION")
                .timestamp(Instant.now())
                .build();

            notificationService.sendEmail(email);

            // Send Slack notification
            SlackNotification slack = SlackNotification.builder()
                .webhook(slackWebhook)
                .channel("#compliance-alerts")
                .username("Compliance Bot")
                .icon(":white_check_mark:")
                .text(String.format("✅ *Sanctions Check Resolved*\\n" +
                    "Check ID: `%s`\\n" +
                    "Entity ID: `%s`\\n" +
                    "Resolution: %s\\n" +
                    "Resolved By: %s",
                    checkRecord.getId(),
                    checkRecord.getEntityId(),
                    checkRecord.getResolution(),
                    checkRecord.getReviewedBy() != null ? checkRecord.getReviewedBy() : "System"))
                .timestamp(Instant.now())
                .build();

            kafkaProducerService.sendMessage("slack-notifications", checkRecord.getId().toString(), slack);

            log.info("Resolution notification sent successfully for check: {}", checkRecord.getId());
        } catch (Exception e) {
            log.error("Failed to send resolution notification for check: {}", checkRecord.getId(), e);
        }
    }

    /**
     * Update audit trail.
     */
    private void updateAuditTrail(SanctionsCheckRecord checkRecord) {
        log.info("COMPLIANCE: Updating audit trail for resolution: {}", checkRecord.getId());

        try {
            ComplianceAuditEvent auditEvent = ComplianceAuditEvent.builder()
                .eventId(java.util.UUID.randomUUID().toString())
                .eventType("SANCTIONS_CHECK_RESOLVED")
                .checkId(checkRecord.getId().toString())
                .entityId(checkRecord.getEntityId())
                .entityType(checkRecord.getEntityType().toString())
                .riskLevel(checkRecord.getRiskLevel().toString())
                .resolution(checkRecord.getResolution())
                .reviewedBy(checkRecord.getReviewedBy())
                .reviewedAt(checkRecord.getReviewedAt())
                .notes(checkRecord.getNotes())
                .timestamp(Instant.now())
                .build();

            kafkaProducerService.sendMessage("compliance-audit-trail", auditEvent.getEventId(), auditEvent);
            log.info("Audit trail updated successfully for check: {}", checkRecord.getId());
        } catch (Exception e) {
            log.error("Failed to update audit trail for check: {}", checkRecord.getId(), e);
        }
    }

    private String buildEmailBody(SanctionsCheckRecord checkRecord) {
        return String.format(
            "A sanctions match has been detected and requires immediate review.\\n\\n" +
            "Details:\\n" +
            "- Check ID: %s\\n" +
            "- Entity ID: %s\\n" +
            "- Entity Type: %s\\n" +
            "- Risk Level: %s\\n" +
            "- List Name: %s\\n" +
            "- Match Score: %s\\n" +
            "- Status: %s\\n\\n" +
            "Action Required: Log in to the compliance dashboard and review this match immediately.\\n" +
            "Dashboard Link: https://compliance.example.com/sanctions/%s\\n\\n" +
            "This is an automated message from the Waqiti Compliance System.",
            checkRecord.getId(),
            checkRecord.getEntityId(),
            checkRecord.getEntityType(),
            checkRecord.getRiskLevel(),
            checkRecord.getListName(),
            checkRecord.getMatchScore(),
            checkRecord.getStatus(),
            checkRecord.getId()
        );
    }

    private String buildEscalationEmailBody(SanctionsCheckRecord checkRecord) {
        return String.format(
            "CRITICAL ALERT: A high-risk sanctions match requires your immediate attention.\\n\\n" +
            "Details:\\n" +
            "- Check ID: %s\\n" +
            "- Entity ID: %s\\n" +
            "- Risk Level: CRITICAL\\n" +
            "- List Name: %s\\n" +
            "- Match Score: %s\\n\\n" +
            "This match has been escalated due to its critical risk level. Immediate action is required.\\n" +
            "Please review and take appropriate action within 1 hour.\\n\\n" +
            "Dashboard Link: https://compliance.example.com/sanctions/%s\\n\\n" +
            "If you are unable to respond, please contact the compliance team immediately.",
            checkRecord.getId(),
            checkRecord.getEntityId(),
            checkRecord.getListName(),
            checkRecord.getMatchScore(),
            checkRecord.getId()
        );
    }

    private String buildResolutionEmailBody(SanctionsCheckRecord checkRecord) {
        return String.format(
            "A sanctions check has been resolved.\\n\\n" +
            "Details:\\n" +
            "- Check ID: %s\\n" +
            "- Entity ID: %s\\n" +
            "- Original Risk Level: %s\\n" +
            "- Resolution: %s\\n" +
            "- Reviewed By: %s\\n" +
            "- Reviewed At: %s\\n" +
            "- Notes: %s\\n\\n" +
            "This case is now closed.\\n\\n" +
            "Audit Trail: https://compliance.example.com/audit/%s",
            checkRecord.getId(),
            checkRecord.getEntityId(),
            checkRecord.getRiskLevel(),
            checkRecord.getResolution(),
            checkRecord.getReviewedBy() != null ? checkRecord.getReviewedBy() : "System",
            checkRecord.getReviewedAt(),
            checkRecord.getNotes() != null ? checkRecord.getNotes() : "N/A",
            checkRecord.getId()
        );
    }
}
