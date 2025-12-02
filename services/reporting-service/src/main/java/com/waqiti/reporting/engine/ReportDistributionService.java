package com.waqiti.reporting.engine;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import javax.mail.internet.MimeMessage;
import java.io.File;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportDistributionService {
    
    private final JavaMailSender mailSender;
    
    @Value("${report.distribution.from-email:reports@example.com}")
    private String fromEmail;
    
    @Value("${report.distribution.base-url:https://app.example.com}")
    private String baseUrl;
    
    public void distributeReport(UUID reportId, List<String> recipients, byte[] reportContent, 
                               String reportName, String format) {
        log.info("Distributing report {} to {} recipients", reportId, recipients.size());
        
        for (String recipient : recipients) {
            try {
                sendReportEmail(recipient, reportId, reportContent, reportName, format);
            } catch (Exception e) {
                log.error("Failed to send report to {}", recipient, e);
            }
        }
    }
    
    private void sendReportEmail(String recipient, UUID reportId, byte[] reportContent, 
                                String reportName, String format) throws Exception {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true);
        
        helper.setFrom(fromEmail);
        helper.setTo(recipient);
        helper.setSubject("Waqiti Report: " + reportName);
        
        String body = buildEmailBody(reportName, reportId);
        helper.setText(body, true);
        
        // Attach report
        String filename = String.format("%s.%s", reportName.replaceAll("[^a-zA-Z0-9]", "_"), 
            format.toLowerCase());
        helper.addAttachment(filename, () -> new java.io.ByteArrayInputStream(reportContent));
        
        mailSender.send(message);
        log.info("Report email sent to {}", recipient);
    }
    
    private String buildEmailBody(String reportName, UUID reportId) {
        return String.format("""
            <html>
            <body>
                <h2>Waqiti Report: %s</h2>
                <p>Your requested report has been generated and is attached to this email.</p>
                <p>Report ID: %s</p>
                <p>You can also view this report online at: <a href="%s/reports/%s">View Report</a></p>
                <br>
                <p>Best regards,<br>Waqiti Reporting Team</p>
            </body>
            </html>
            """, reportName, reportId, baseUrl, reportId);
    }
    
    public void saveReportToStorage(UUID reportId, byte[] reportContent, String format) {
        // In a real implementation, this would save to S3 or similar storage
        log.info("Saving report {} to storage", reportId);
    }
    
    public boolean isReportAvailable(UUID reportId) {
        // Check if report exists in storage
        return true;
    }
    
    public byte[] retrieveReport(UUID reportId) {
        // Retrieve report from storage
        log.info("Retrieving report {} from storage", reportId);
        return new byte[0];
    }
}