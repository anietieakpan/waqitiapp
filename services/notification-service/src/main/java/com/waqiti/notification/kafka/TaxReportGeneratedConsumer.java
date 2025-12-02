package com.waqiti.notification.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.notification.service.NotificationService;
import com.waqiti.notification.service.EmailNotificationService;
import com.waqiti.notification.service.PushNotificationService;
import com.waqiti.notification.service.InAppNotificationService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Kafka consumer for TaxReportGenerated events
 * Sends notifications to users when their tax reports are ready for download
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaxReportGeneratedConsumer extends BaseKafkaConsumer {

    private final NotificationService notificationService;
    private final EmailNotificationService emailNotificationService;
    private final PushNotificationService pushNotificationService;
    private final InAppNotificationService inAppNotificationService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "tax-report-generated", groupId = "notification-service-group")
    @CircuitBreaker(name = "tax-report-consumer")
    @Retry(name = "tax-report-consumer")
    @Transactional
    public void handleTaxReportGenerated(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        MDC.put("eventType", "tax-report-generated");
        
        try {
            log.info("Processing tax report generated event: partition={}, offset={}", 
                    record.partition(), record.offset());
            
            JsonNode eventData = objectMapper.readTree(record.value());
            
            String reportId = eventData.path("reportId").asText();
            String userId = eventData.path("userId").asText();
            String reportType = eventData.path("reportType").asText();
            String taxYear = eventData.path("taxYear").asText();
            String downloadUrl = eventData.path("downloadUrl").asText();
            String reportFormat = eventData.path("reportFormat").asText("PDF");
            LocalDateTime generatedAt = LocalDateTime.parse(eventData.path("generatedAt").asText());
            boolean isAmended = eventData.path("isAmended").asBoolean(false);
            
            log.info("Sending tax report notifications: reportId={}, userId={}, type={}, year={}", 
                    reportId, userId, reportType, taxYear);
            
            // Send comprehensive notifications for tax report availability
            sendTaxReportNotifications(reportId, userId, reportType, taxYear, downloadUrl, 
                    reportFormat, generatedAt, isAmended);
            
            ack.acknowledge();
            log.info("Successfully processed tax report notification: reportId={}", reportId);
            
        } catch (JsonProcessingException e) {
            log.error("Failed to parse tax report event: {}", e.getMessage(), e);
            handleProcessingError(record, e);
        } catch (Exception e) {
            log.error("Error processing tax report event: {}", e.getMessage(), e);
            handleProcessingError(record, e);
        } finally {
            MDC.clear();
        }
    }

    private void sendTaxReportNotifications(String reportId, String userId, String reportType, 
                                            String taxYear, String downloadUrl, String reportFormat,
                                            LocalDateTime generatedAt, boolean isAmended) {
        
        try {
            // Prepare notification data
            Map<String, Object> notificationData = new HashMap<>();
            notificationData.put("reportId", reportId);
            notificationData.put("reportType", reportType);
            notificationData.put("taxYear", taxYear);
            notificationData.put("downloadUrl", downloadUrl);
            notificationData.put("reportFormat", reportFormat);
            notificationData.put("generatedAt", generatedAt.toString());
            notificationData.put("isAmended", isAmended);
            
            // Send email notification
            sendEmailNotification(userId, reportType, taxYear, downloadUrl, reportFormat, isAmended, notificationData);
            
            // Send push notification
            sendPushNotification(userId, reportType, taxYear, isAmended, notificationData);
            
            // Send in-app notification
            sendInAppNotification(userId, reportType, taxYear, downloadUrl, isAmended, notificationData);
            
            log.info("All tax report notifications sent successfully: reportId={}, userId={}", reportId, userId);
            
        } catch (Exception e) {
            log.error("Error sending tax report notifications: reportId={}, userId={}, error={}", 
                    reportId, userId, e.getMessage(), e);
            throw e;
        }
    }
    
    private void sendEmailNotification(String userId, String reportType, String taxYear, String downloadUrl,
                                       String reportFormat, boolean isAmended, Map<String, Object> data) {
        
        try {
            String subject = String.format("Your %s %s Tax Report is Ready%s", 
                    taxYear, reportType, isAmended ? " (Amended)" : "");
            
            String htmlBody = buildEmailHtmlContent(reportType, taxYear, downloadUrl, reportFormat, isAmended);
            String textBody = buildEmailTextContent(reportType, taxYear, downloadUrl, reportFormat, isAmended);
            
            emailNotificationService.sendEmailNotification(
                    userId,
                    subject,
                    htmlBody,
                    textBody,
                    "TAX_REPORT_READY",
                    data
            );
            
            log.debug("Tax report email notification sent: userId={}, reportType={}", userId, reportType);
            
        } catch (Exception e) {
            log.error("Failed to send tax report email notification: userId={}, error={}", userId, e.getMessage(), e);
            // Don't rethrow - continue with other notifications
        }
    }
    
    private void sendPushNotification(String userId, String reportType, String taxYear, 
                                      boolean isAmended, Map<String, Object> data) {
        
        try {
            String title = "Tax Report Ready";
            String message = String.format("Your %s %s tax report is ready for download%s", 
                    taxYear, reportType, isAmended ? " (Amended version)" : "");
            
            pushNotificationService.sendPushNotification(
                    userId,
                    title,
                    message,
                    "TAX_REPORT_READY",
                    data
            );
            
            log.debug("Tax report push notification sent: userId={}, reportType={}", userId, reportType);
            
        } catch (Exception e) {
            log.error("Failed to send tax report push notification: userId={}, error={}", userId, e.getMessage(), e);
            // Don't rethrow - continue with other notifications
        }
    }
    
    private void sendInAppNotification(String userId, String reportType, String taxYear, String downloadUrl,
                                       boolean isAmended, Map<String, Object> data) {
        
        try {
            String title = String.format("%s Tax Report Ready", taxYear);
            String message = String.format("Your %s %s tax report has been generated and is ready for download.%s", 
                    taxYear, reportType, isAmended ? " This is an amended version." : "");
            
            // Add action button for direct download
            data.put("actionType", "DOWNLOAD_TAX_REPORT");
            data.put("actionUrl", downloadUrl);
            data.put("actionLabel", "Download Report");
            
            inAppNotificationService.sendInAppNotification(
                    userId,
                    title,
                    message,
                    "TAX_REPORT_READY",
                    "HIGH", // High priority for tax documents
                    data
            );
            
            log.debug("Tax report in-app notification sent: userId={}, reportType={}", userId, reportType);
            
        } catch (Exception e) {
            log.error("Failed to send tax report in-app notification: userId={}, error={}", userId, e.getMessage(), e);
            // Don't rethrow - continue processing
        }
    }
    
    private String buildEmailHtmlContent(String reportType, String taxYear, String downloadUrl, 
                                         String reportFormat, boolean isAmended) {
        
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>")
            .append("<html><head><title>Tax Report Ready</title></head><body>")
            .append("<div style=\"font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;\">")
            .append("<h2 style=\"color: #2c3e50;\">Your Tax Report is Ready</h2>");
        
        if (isAmended) {
            html.append("<div style=\"background-color: #fff3cd; border: 1px solid #ffeaa7; padding: 10px; margin: 10px 0; border-radius: 5px;\">")
                .append("<strong>Note:</strong> This is an amended version of your tax report.")
                .append("</div>");
        }
        
        html.append("<p>Your <strong>").append(taxYear).append(" ").append(reportType).append("</strong> tax report has been generated successfully.</p>")
            .append("<div style=\"background-color: #f8f9fa; border-left: 4px solid #28a745; padding: 15px; margin: 20px 0;\">")
            .append("<h3 style=\"margin-top: 0; color: #28a745;\">Report Details</h3>")
            .append("<ul style=\"list-style: none; padding: 0;\">")
            .append("<li><strong>Tax Year:</strong> ").append(taxYear).append("</li>")
            .append("<li><strong>Report Type:</strong> ").append(reportType).append("</li>")
            .append("<li><strong>Format:</strong> ").append(reportFormat).append("</li>")
            .append("<li><strong>Status:</strong> ").append(isAmended ? "Amended" : "Original").append("</li>")
            .append("</ul>")
            .append("</div>")
            .append("<div style=\"text-align: center; margin: 30px 0;\">")
            .append("<a href=\"").append(downloadUrl).append("\" style=\"background-color: #007bff; color: white; padding: 12px 30px; text-decoration: none; border-radius: 5px; display: inline-block;\">")
            .append("Download Report")
            .append("</a>")
            .append("</div>")
            .append("<div style=\"background-color: #e3f2fd; border: 1px solid #bbdefb; padding: 15px; margin: 20px 0; border-radius: 5px;\">")
            .append("<h4 style=\"margin-top: 0; color: #1976d2;\">Important Tax Information</h4>")
            .append("<ul>")
            .append("<li>Please review your report carefully before filing</li>")
            .append("<li>Keep a copy of this report for your records</li>")
            .append("<li>Consult with a tax professional if you have questions</li>")
            .append("<li>This report will be available for download for 90 days</li>")
            .append("</ul>")
            .append("</div>")
            .append("<p style=\"color: #666; font-size: 12px; margin-top: 30px;\">")
            .append("If you have any questions about your tax report, please contact our support team.")
            .append("</p>")
            .append("</div></body></html>");
        
        return html.toString();
    }
    
    private String buildEmailTextContent(String reportType, String taxYear, String downloadUrl, 
                                         String reportFormat, boolean isAmended) {
        
        StringBuilder text = new StringBuilder();
        text.append("Your Tax Report is Ready\n\n");
        
        if (isAmended) {
            text.append("*** AMENDED REPORT ***\n");
            text.append("This is an amended version of your tax report.\n\n");
        }
        
        text.append("Your ").append(taxYear).append(" ").append(reportType).append(" tax report has been generated successfully.\n\n")
            .append("Report Details:\n")
            .append("- Tax Year: ").append(taxYear).append("\n")
            .append("- Report Type: ").append(reportType).append("\n")
            .append("- Format: ").append(reportFormat).append("\n")
            .append("- Status: ").append(isAmended ? "Amended" : "Original").append("\n\n")
            .append("Download your report here: ").append(downloadUrl).append("\n\n")
            .append("Important Reminders:\n")
            .append("- Please review your report carefully before filing\n")
            .append("- Keep a copy of this report for your records\n")
            .append("- Consult with a tax professional if you have questions\n")
            .append("- This report will be available for download for 90 days\n\n")
            .append("If you have any questions about your tax report, please contact our support team.");
        
        return text.toString();
    }
}