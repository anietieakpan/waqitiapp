package com.waqiti.ledger.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Async;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Email Alert Service
 *
 * Sends email notifications for critical financial alerts,
 * reconciliation reports, and compliance notifications.
 *
 * Security: All emails are HTML-encoded to prevent XSS
 * Compliance: Email records retained for audit trail
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailAlertService {

    private final JavaMailSender mailSender;

    @Value("${waqiti.email.from:noreply@example.com}")
    private String fromEmail;

    @Value("${waqiti.email.enabled:true}")
    private boolean enabled;

    // Pre-configured recipient groups
    @Value("${waqiti.email.recipients.cfo:cfo@example.com}")
    private String cfoEmail;

    @Value("${waqiti.email.recipients.ceo:ceo@example.com}")
    private String ceoEmail;

    @Value("${waqiti.email.recipients.cto:cto@example.com}")
    private String ctoEmail;

    @Value("${waqiti.email.recipients.controller:controller@example.com}")
    private String controllerEmail;

    @Value("${waqiti.email.recipients.finance-ops:finance-ops@example.com}")
    private String financeOpsEmail;

    @Value("${waqiti.email.recipients.compliance:compliance@example.com}")
    private String complianceEmail;

    @Value("${waqiti.email.recipients.finance-manager:finance-manager@example.com}")
    private String financeManagerEmail;

    /**
     * Sends an email alert
     *
     * @param recipients List of recipient roles (CFO, CEO, etc.) or email addresses
     * @param subject Email subject
     * @param body HTML email body
     */
    @Async
    public void sendEmail(List<String> recipients, String subject, String body) {
        if (!enabled) {
            log.warn("Email is disabled. Email not sent - Subject: {}", subject);
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setSubject(subject);
            helper.setText(body, true); // true = HTML content

            // Resolve recipients (roles → emails)
            String[] emailAddresses = resolveRecipients(recipients);
            helper.setTo(emailAddresses);

            mailSender.send(message);

            log.info("✅ Email sent successfully - Subject: {}, Recipients: {}", subject, String.join(", ", emailAddresses));

        } catch (MessagingException e) {
            log.error("❌ Failed to send email - Subject: {}, Error: {}", subject, e.getMessage(), e);
        } catch (Exception e) {
            log.error("❌ Unexpected error sending email - Subject: {}, Error: {}", subject, e.getMessage(), e);
        }
    }

    /**
     * Sends a critical alert email with high priority
     */
    @Async
    public void sendCriticalAlert(List<String> recipients, String subject, String body) {
        if (!enabled) {
            log.warn("Email is disabled. Critical alert not sent - Subject: {}", subject);
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setSubject("🚨 CRITICAL: " + subject);
            helper.setText(wrapInCriticalTemplate(body), true);

            // Set high priority
            message.setHeader("X-Priority", "1");
            message.setHeader("Importance", "High");

            String[] emailAddresses = resolveRecipients(recipients);
            helper.setTo(emailAddresses);

            // Add CC to compliance and audit
            helper.setCc(complianceEmail);

            mailSender.send(message);

            log.info("✅ CRITICAL email sent - Subject: {}, Recipients: {}", subject, String.join(", ", emailAddresses));

        } catch (Exception e) {
            log.error("❌ Failed to send CRITICAL email - Subject: {}, Error: {}", subject, e.getMessage(), e);
        }
    }

    /**
     * Sends emergency reconciliation notification
     */
    @Async
    public void sendEmergencyReconciliationEmail(
            List<String> recipients,
            String accountId,
            String discrepancy,
            String severity,
            Map<String, String> details) {

        String subject = String.format("Emergency Reconciliation Required - Account %s - %s", accountId, severity);

        StringBuilder body = new StringBuilder();
        body.append(buildEmailHeader("Emergency Reconciliation Alert"));
        body.append("<div style='background-color: #fff3cd; border: 2px solid #ffc107; padding: 20px; margin: 20px 0; border-radius: 5px;'>");
        body.append("<h2 style='color: #856404; margin-top: 0;'>🚨 Emergency Reconciliation Required</h2>");
        body.append(String.format("<p><strong>An emergency reconciliation has been triggered due to a critical discrepancy.</strong></p>"));
        body.append("</div>");

        body.append("<table style='width: 100%; border-collapse: collapse; margin: 20px 0;'>");
        body.append("<tr style='background-color: #f8f9fa;'><th style='padding: 12px; text-align: left; border: 1px solid #dee2e6;'>Detail</th><th style='padding: 12px; text-align: left; border: 1px solid #dee2e6;'>Value</th></tr>");
        body.append(String.format("<tr><td style='padding: 12px; border: 1px solid #dee2e6;'><strong>Account ID</strong></td><td style='padding: 12px; border: 1px solid #dee2e6;'>%s</td></tr>", escapeHtml(accountId)));
        body.append(String.format("<tr><td style='padding: 12px; border: 1px solid #dee2e6;'><strong>Discrepancy Amount</strong></td><td style='padding: 12px; border: 1px solid #dee2e6; color: #dc3545; font-weight: bold;'>%s</td></tr>", escapeHtml(discrepancy)));
        body.append(String.format("<tr><td style='padding: 12px; border: 1px solid #dee2e6;'><strong>Severity</strong></td><td style='padding: 12px; border: 1px solid #dee2e6;'><span style='background-color: %s; color: white; padding: 4px 8px; border-radius: 3px;'>%s</span></td></tr>",
                getSeverityColor(severity), escapeHtml(severity)));
        body.append(String.format("<tr><td style='padding: 12px; border: 1px solid #dee2e6;'><strong>Timestamp</strong></td><td style='padding: 12px; border: 1px solid #dee2e6;'>%s</td></tr>",
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))));

        if (details != null) {
            for (Map.Entry<String, String> entry : details.entrySet()) {
                body.append(String.format("<tr><td style='padding: 12px; border: 1px solid #dee2e6;'><strong>%s</strong></td><td style='padding: 12px; border: 1px solid #dee2e6;'>%s</td></tr>",
                        escapeHtml(entry.getKey()), escapeHtml(entry.getValue())));
            }
        }
        body.append("</table>");

        body.append("<div style='background-color: #f8d7da; border: 1px solid #f5c6cb; padding: 15px; margin: 20px 0; border-radius: 5px;'>");
        body.append("<h3 style='color: #721c24; margin-top: 0;'>Required Action</h3>");
        body.append("<p>This alert requires immediate attention. Please review the reconciliation details in the Waqiti Admin Dashboard and take appropriate corrective action.</p>");
        body.append("</div>");

        body.append(buildEmailFooter());

        sendCriticalAlert(recipients, subject, body.toString());
    }

    /**
     * Sends daily reconciliation summary
     */
    @Async
    public void sendDailyReconciliationSummary(String recipient, Map<String, Object> summary) {
        String subject = String.format("Daily Reconciliation Summary - %s",
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));

        StringBuilder body = new StringBuilder();
        body.append(buildEmailHeader("Daily Reconciliation Summary"));

        body.append("<h2>Daily Reconciliation Summary</h2>");
        body.append("<table style='width: 100%; border-collapse: collapse; margin: 20px 0;'>");

        for (Map.Entry<String, Object> entry : summary.entrySet()) {
            body.append(String.format("<tr><td style='padding: 8px; border: 1px solid #ddd;'>%s</td><td style='padding: 8px; border: 1px solid #ddd;'>%s</td></tr>",
                    escapeHtml(entry.getKey()), escapeHtml(String.valueOf(entry.getValue()))));
        }

        body.append("</table>");
        body.append(buildEmailFooter());

        sendEmail(List.of(recipient), subject, body.toString());
    }

    /**
     * Resolves recipient roles to email addresses
     */
    private String[] resolveRecipients(List<String> recipients) {
        return recipients.stream()
                .map(this::resolveRecipient)
                .toArray(String[]::new);
    }

    /**
     * Resolves a single recipient (role → email)
     */
    private String resolveRecipient(String recipient) {
        switch (recipient.toUpperCase()) {
            case "CFO":
                return cfoEmail;
            case "CEO":
                return ceoEmail;
            case "CTO":
                return ctoEmail;
            case "CONTROLLER":
                return controllerEmail;
            case "FINANCE_OPS":
            case "FINANCE-OPS":
                return financeOpsEmail;
            case "COMPLIANCE":
                return complianceEmail;
            case "FINANCE_MANAGER":
            case "FINANCE-MANAGER":
                return financeManagerEmail;
            default:
                // Assume it's already an email address
                return recipient;
        }
    }

    /**
     * Wraps content in critical alert template
     */
    private String wrapInCriticalTemplate(String content) {
        StringBuilder html = new StringBuilder();
        html.append(buildEmailHeader("CRITICAL ALERT"));
        html.append("<div style='background-color: #f8d7da; border-left: 5px solid #dc3545; padding: 20px; margin: 20px 0;'>");
        html.append("<h2 style='color: #721c24; margin-top: 0;'>🚨 CRITICAL ALERT</h2>");
        html.append(content);
        html.append("</div>");
        html.append(buildEmailFooter());
        return html.toString();
    }

    /**
     * Builds standardized email header
     */
    private String buildEmailHeader(String title) {
        return String.format(
                "<!DOCTYPE html>" +
                "<html>" +
                "<head>" +
                "<meta charset='UTF-8'>" +
                "<title>%s</title>" +
                "</head>" +
                "<body style='font-family: Arial, sans-serif; line-height: 1.6; color: #333; max-width: 800px; margin: 0 auto; padding: 20px;'>" +
                "<div style='background-color: #007bff; color: white; padding: 20px; text-align: center;'>" +
                "<h1 style='margin: 0;'>Waqiti Ledger Service</h1>" +
                "<p style='margin: 5px 0 0 0;'>%s</p>" +
                "</div>" +
                "<div style='padding: 20px; background-color: #ffffff;'>",
                escapeHtml(title),
                escapeHtml(title)
        );
    }

    /**
     * Builds standardized email footer
     */
    private String buildEmailFooter() {
        return String.format(
                "</div>" +
                "<div style='background-color: #f8f9fa; padding: 20px; text-align: center; margin-top: 20px; border-top: 1px solid #dee2e6;'>" +
                "<p style='color: #6c757d; font-size: 12px; margin: 0;'>This is an automated message from Waqiti Ledger Service</p>" +
                "<p style='color: #6c757d; font-size: 12px; margin: 5px 0 0 0;'>Timestamp: %s UTC</p>" +
                "<p style='color: #6c757d; font-size: 12px; margin: 5px 0 0 0;'>&copy; 2025 Waqiti. All rights reserved.</p>" +
                "</div>" +
                "</body>" +
                "</html>",
                LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        );
    }

    /**
     * Gets color for severity level
     */
    private String getSeverityColor(String severity) {
        switch (severity.toUpperCase()) {
            case "CRITICAL":
                return "#dc3545"; // Red
            case "HIGH":
                return "#fd7e14"; // Orange
            case "MEDIUM":
                return "#ffc107"; // Yellow
            case "LOW":
                return "#28a745"; // Green
            default:
                return "#6c757d"; // Gray
        }
    }

    /**
     * Escapes HTML to prevent XSS
     */
    private String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#x27;");
    }
}
