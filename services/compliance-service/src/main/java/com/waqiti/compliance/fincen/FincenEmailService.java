package com.waqiti.compliance.fincen;

import com.waqiti.compliance.fincen.entity.ManualFilingQueueEntry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * FinCEN Email Service
 *
 * Sends email alerts for manual SAR filing requirements.
 *
 * @author Waqiti Compliance Team
 * @version 2.0.0
 * @since 2025-11-04
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FincenEmailService {

    private final JavaMailSender mailSender;

    @Value("${compliance.email.from:compliance@example.com}")
    private String fromEmail;

    @Value("${compliance.email.to:compliance@example.com}")
    private String complianceTeamEmail;

    @Value("${compliance.email.cco:cco@example.com}")
    private String ccoEmail;

    @Value("${email.enabled:false}")
    private boolean emailEnabled;

    /**
     * Send manual filing alert to compliance team
     */
    public void sendManualFilingAlert(
            String sarId,
            String jiraTicketId,
            boolean expedited,
            String failureReason,
            LocalDateTime slaDeadline) {

        if (!emailEnabled) {
            log.warn("Email disabled - skipping manual filing alert for SAR: {}", sarId);
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(complianceTeamEmail);
            message.setSubject(String.format("[URGENT] Manual SAR Filing Required - %s", sarId));
            message.setText(buildManualFilingEmailBody(sarId, jiraTicketId, expedited, failureReason, slaDeadline));

            mailSender.send(message);

            log.info("Manual filing alert sent: sarId={}, jiraTicketId={}", sarId, jiraTicketId);

        } catch (Exception e) {
            log.error("Failed to send manual filing alert: sarId={}", sarId, e);
        }
    }

    /**
     * Send daily digest to compliance team
     */
    public void sendDailyDigest(
            long pendingCount,
            long overdueCount,
            long filedLast24Hours,
            List<ManualFilingQueueEntry> criticalEntries) {

        if (!emailEnabled) {
            log.warn("Email disabled - skipping daily digest");
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(complianceTeamEmail);
            message.setSubject("FinCEN Manual Filing Queue - Daily Digest");
            message.setText(buildDailyDigestBody(pendingCount, overdueCount, filedLast24Hours, criticalEntries));

            mailSender.send(message);

            log.info("Daily digest sent: pending={}, overdue={}, filed24h={}",
                    pendingCount, overdueCount, filedLast24Hours);

        } catch (Exception e) {
            log.error("Failed to send daily digest", e);
        }
    }

    // ========== PRIVATE HELPER METHODS ==========

    private String buildManualFilingEmailBody(
            String sarId,
            String jiraTicketId,
            boolean expedited,
            String failureReason,
            LocalDateTime slaDeadline) {

        return String.format("""
                URGENT: Manual SAR Filing Required

                A SAR requires immediate manual filing due to FinCEN API unavailability.

                SAR Details:
                - SAR ID: %s
                - Priority: %s
                - SLA Deadline: %s
                - JIRA Ticket: %s
                - Failure Reason: %s

                Action Required:
                1. Access the manual filing queue dashboard
                2. Retrieve SAR XML content
                3. File manually via FinCEN BSA E-Filing portal: https://bsaefiling.fincen.treas.gov/
                4. Record filing number in system
                5. Update queue status to MANUALLY_FILED
                6. Close JIRA ticket

                CRITICAL: Failure to file before SLA deadline may result in regulatory penalties ($25,000 - $1,000,000).

                If you have any questions, contact the Chief Compliance Officer immediately.

                ---
                Waqiti Compliance System
                This is an automated alert. Do not reply to this email.
                """,
                sarId,
                expedited ? "EXPEDITED (24-hour SLA)" : "STANDARD (72-hour SLA)",
                slaDeadline,
                jiraTicketId,
                failureReason
        );
    }

    private String buildDailyDigestBody(
            long pendingCount,
            long overdueCount,
            long filedLast24Hours,
            List<ManualFilingQueueEntry> criticalEntries) {

        StringBuilder sb = new StringBuilder();
        sb.append("FinCEN Manual Filing Queue - Daily Status Report\n\n");
        sb.append("Summary:\n");
        sb.append(String.format("- Pending Filings: %d\n", pendingCount));
        sb.append(String.format("- Overdue Filings: %d %s\n", overdueCount, overdueCount > 0 ? "⚠️" : ""));
        sb.append(String.format("- Filed (Last 24h): %d\n\n", filedLast24Hours));

        if (overdueCount > 0) {
            sb.append("⚠️ WARNING: There are overdue filings requiring immediate attention!\n\n");
        }

        if (!criticalEntries.isEmpty()) {
            sb.append("Critical Entries (Expedited or Overdue):\n");
            for (ManualFilingQueueEntry entry : criticalEntries) {
                sb.append(String.format("- SAR %s: %s, SLA: %s, JIRA: %s\n",
                        entry.getSarId(),
                        entry.getStatus(),
                        entry.getSlaDeadline(),
                        entry.getJiraTicketId()));
            }
            sb.append("\n");
        }

        sb.append("Dashboard: https://admin.example.com/compliance/manual-filing-queue\n\n");
        sb.append("---\n");
        sb.append("Waqiti Compliance System\n");
        sb.append("This is an automated daily digest. Do not reply to this email.");

        return sb.toString();
    }
}
