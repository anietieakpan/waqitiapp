package com.waqiti.notification.integration;

import com.sendgrid.*;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;
import com.sendgrid.helpers.mail.objects.Personalization;
import com.waqiti.notification.dto.EmailRequest;
import com.waqiti.notification.dto.EmailResponse;
import com.waqiti.notification.service.EmailProvider;
import com.waqiti.notification.domain.SendGridWebhookEvent;
import com.waqiti.notification.repository.SendGridWebhookEventRepository;
import com.waqiti.common.exception.BusinessException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * SendGrid Email Provider Implementation
 * 
 * Implements email sending through SendGrid's API v3.
 */
@Slf4j
@Service
public class SendGridEmailProvider implements EmailProvider {

    private final SendGrid sendGrid;
    private final SendGridWebhookEventRepository webhookEventRepository;
    
    @Value("${sendgrid.api.key:}")
    private String apiKey;
    
    @Value("${sendgrid.from.email:noreply@example.com}")
    private String defaultFromEmail;
    
    @Value("${sendgrid.from.name:Waqiti}")
    private String defaultFromName;

    public SendGridEmailProvider(SendGridWebhookEventRepository webhookEventRepository) {
        this.webhookEventRepository = webhookEventRepository;
        this.sendGrid = new SendGrid(apiKey);
    }

    @Override
    @CircuitBreaker(name = "sendgridApi", fallbackMethod = "sendEmailFallback")
    public EmailResponse sendEmail(EmailRequest request) {
        try {
            log.debug("Sending email via SendGrid to: {}", request.getRecipient());
            
            // Create SendGrid mail object
            Email from = new Email(
                request.getSenderEmail() != null ? request.getSenderEmail() : defaultFromEmail,
                request.getSenderName() != null ? request.getSenderName() : defaultFromName
            );
            
            Email to = new Email(request.getRecipient());
            String subject = request.getSubject();
            
            // Create content
            Content content;
            if (request.getHtmlBody() != null && !request.getHtmlBody().trim().isEmpty()) {
                content = new Content("text/html", request.getHtmlBody());
            } else {
                content = new Content("text/plain", request.getBody());
            }
            
            Mail mail = new Mail(from, subject, to, content);
            
            // Add plain text content if HTML is provided
            if (request.getHtmlBody() != null && request.getBody() != null) {
                mail.addContent(new Content("text/plain", request.getBody()));
            }
            
            // Add custom headers
            if (request.getHeaders() != null) {
                for (Map.Entry<String, String> header : request.getHeaders().entrySet()) {
                    mail.addHeader(header.getKey(), header.getValue());
                }
            }
            
            // Add tracking settings
            mail.setTrackingSettings(createTrackingSettings());
            
            // Add template data if using dynamic templates
            if (request.getTemplateName() != null && request.getTemplateData() != null) {
                Personalization personalization = new Personalization();
                personalization.addTo(to);
                
                for (Map.Entry<String, Object> data : request.getTemplateData().entrySet()) {
                    personalization.addDynamicTemplateData(data.getKey(), data.getValue());
                }
                
                mail.addPersonalization(personalization);
            }
            
            // Send the email
            Request sendGridRequest = new Request();
            sendGridRequest.setMethod(Method.POST);
            sendGridRequest.setEndpoint("mail/send");
            sendGridRequest.setBody(mail.build());
            
            Response response = sendGrid.api(sendGridRequest);
            
            if (response.getStatusCode() >= 200 && response.getStatusCode() < 300) {
                // Extract message ID from response headers
                String messageId = response.getHeaders().get("X-Message-Id");
                
                EmailResponse emailResponse = new EmailResponse();
                emailResponse.setRequestId(request.getRequestId());
                emailResponse.setMessageId(messageId);
                emailResponse.setRecipient(request.getRecipient());
                emailResponse.setStatus("SENT");
                emailResponse.setProvider("sendgrid");
                emailResponse.setSentAt(Instant.now());
                emailResponse.setStatusCode(response.getStatusCode());
                
                log.info("Email sent successfully via SendGrid: {}", messageId);
                return emailResponse;
                
            } else {
                log.error("SendGrid API error: {} - {}", response.getStatusCode(), response.getBody());
                throw new BusinessException("SendGrid API error: " + response.getStatusCode());
            }
            
        } catch (IOException e) {
            log.error("Failed to send email via SendGrid: {}", e.getMessage(), e);
            throw new BusinessException("Failed to send email via SendGrid: " + e.getMessage());
        }
    }

    @Override
    @CircuitBreaker(name = "sendgridApi")
    public EmailResponse getEmailStatus(String messageId) {
        try {
            log.debug("Getting email status from SendGrid for message: {}", messageId);
            
            // SendGrid status tracking is done via webhooks
            // Retrieve status from our webhook event store
            EmailResponse response = getStoredEmailStatus(messageId);
            
            if (response == null) {
                // If no webhook data available, create a basic response
                response = new EmailResponse();
                response.setMessageId(messageId);
                response.setStatus("SENT"); // Default status for sent emails
                response.setProvider("sendgrid");
            }
            
            return response;
            
        } catch (Exception e) {
            log.error("Failed to get email status from SendGrid: {}", e.getMessage(), e);
            throw new BusinessException("Failed to get email status: " + e.getMessage());
        }
    }

    @Override
    public boolean isHealthy() {
        try {
            // Make a lightweight API call to check connectivity
            Request request = new Request();
            request.setMethod(Method.GET);
            request.setEndpoint("user/profile");
            
            Response response = sendGrid.api(request);
            return response.getStatusCode() == 200;
            
        } catch (Exception e) {
            log.warn("SendGrid health check failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public String getProviderName() {
        return "sendgrid";
    }

    private TrackingSettings createTrackingSettings() {
        TrackingSettings trackingSettings = new TrackingSettings();
        
        // Enable click tracking
        ClickTrackingSetting clickTrackingSetting = new ClickTrackingSetting();
        clickTrackingSetting.setEnable(true);
        clickTrackingSetting.setEnableText(true);
        trackingSettings.setClickTrackingSetting(clickTrackingSetting);
        
        // Enable open tracking
        OpenTrackingSetting openTrackingSetting = new OpenTrackingSetting();
        openTrackingSetting.setEnable(true);
        trackingSettings.setOpenTrackingSetting(openTrackingSetting);
        
        // Enable subscription tracking
        SubscriptionTrackingSetting subscriptionTrackingSetting = new SubscriptionTrackingSetting();
        subscriptionTrackingSetting.setEnable(true);
        subscriptionTrackingSetting.setText("Unsubscribe");
        subscriptionTrackingSetting.setHtml("<a href=\"{{unsubscribe}}\">Unsubscribe</a>");
        trackingSettings.setSubscriptionTrackingSetting(subscriptionTrackingSetting);
        
        return trackingSettings;
    }

    // Fallback method
    public EmailResponse sendEmailFallback(EmailRequest request, Exception ex) {
        log.error("SendGrid email fallback triggered for recipient: {}", request.getRecipient(), ex);
        
        EmailResponse response = new EmailResponse();
        response.setRequestId(request.getRequestId());
        response.setRecipient(request.getRecipient());
        response.setStatus("FAILED");
        response.setErrorMessage("SendGrid service is temporarily unavailable: " + ex.getMessage());
        response.setSentAt(Instant.now());
        response.setProvider("sendgrid");
        
        return response;
    }
    
    /**
     * Get stored email status from webhook event store
     */
    private EmailResponse getStoredEmailStatus(String messageId) {
        try {
            log.debug("Retrieving stored status for message: {}", messageId);
            
            if (messageId == null || messageId.trim().isEmpty()) {
                log.warn("Cannot retrieve status - messageId is null or empty");
                return null;
            }
            
            // Query the webhook event store via JPA
            return webhookEventRepository.findLatestStatusByMessageId(messageId)
                .map(this::convertWebhookEventToEmailResponse)
                .orElse(null);
                
        } catch (Exception e) {
            log.error("Error retrieving stored email status for messageId {}: {}", messageId, e.getMessage(), e);
            
            // Return a fallback response with unknown status
            EmailResponse fallbackResponse = new EmailResponse();
            fallbackResponse.setMessageId(messageId);
            fallbackResponse.setStatus("UNKNOWN");
            fallbackResponse.setProvider("sendgrid");
            fallbackResponse.setErrorMessage("Error retrieving status: " + e.getMessage());
            
            return fallbackResponse;
        }
    }
    
    /**
     * Convert webhook event to email response
     */
    private EmailResponse convertWebhookEventToEmailResponse(SendGridWebhookEvent event) {
        EmailResponse response = new EmailResponse();
        response.setMessageId(event.getMessageId());
        response.setRecipient(event.getRecipient());
        response.setProvider("sendgrid");
        response.setSentAt(event.getTimestamp());
        
        // Map SendGrid event types to standard statuses
        String status = mapEventTypeToStatus(event.getEventType());
        response.setStatus(status);
        
        if (event.getReason() != null) {
            response.setErrorMessage(event.getReason());
        }
        
        // Add additional metadata
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("eventType", event.getEventType());
        metadata.put("category", event.getCategory());
        metadata.put("uniqueOpens", event.getUniqueOpens());
        metadata.put("uniqueClicks", event.getUniqueClicks());
        metadata.put("userAgent", event.getUserAgent());
        metadata.put("ip", event.getIp());
        response.setMetadata(metadata);
        
        return response;
    }
    
    /**
     * Map SendGrid event types to standard email statuses
     */
    private String mapEventTypeToStatus(String eventType) {
        if (eventType == null) return "UNKNOWN";
        
        switch (eventType.toLowerCase()) {
            case "delivered":
                return "DELIVERED";
            case "bounce":
            case "blocked":
                return "BOUNCED";
            case "dropped":
                return "DROPPED";
            case "deferred":
                return "DEFERRED";
            case "processed":
                return "PROCESSED";
            case "open":
                return "OPENED";
            case "click":
                return "CLICKED";
            case "unsubscribe":
                return "UNSUBSCRIBED";
            case "spamreport":
                return "SPAM_REPORTED";
            case "group_unsubscribe":
                return "GROUP_UNSUBSCRIBED";
            case "group_resubscribe":
                return "GROUP_RESUBSCRIBED";
            default:
                log.warn("Unknown SendGrid event type: {}", eventType);
                return "UNKNOWN";
        }
    }
}