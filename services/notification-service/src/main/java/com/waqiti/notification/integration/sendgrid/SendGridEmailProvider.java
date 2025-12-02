package com.waqiti.notification.integration.sendgrid;

import com.sendgrid.*;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.*;
import com.waqiti.notification.domain.EmailTemplate;
import com.waqiti.notification.domain.NotificationMessage;
import com.waqiti.notification.dto.request.EmailRequest;
import com.waqiti.notification.dto.response.EmailResponse;
import com.waqiti.notification.exception.EmailDeliveryException;
import com.waqiti.notification.integration.EmailProvider;
import com.waqiti.common.cache.CacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SendGridEmailProvider implements EmailProvider {

    @Value("${sendgrid.api.key}")
    private String apiKey;
    
    @Value("${sendgrid.from.email}")
    private String fromEmail;
    
    @Value("${sendgrid.from.name}")
    private String fromName;
    
    @Value("${sendgrid.reply-to.email:}")
    private String replyToEmail;
    
    @Value("${sendgrid.webhook.enabled:true}")
    private boolean webhookEnabled;
    
    @Value("${sendgrid.tracking.enabled:true}")
    private boolean trackingEnabled;
    
    private final CacheService cacheService;
    private final SendGridWebhookHandler webhookHandler;
    
    private SendGrid sendGrid;
    
    @PostConstruct
    public void init() {
        sendGrid = new SendGrid(apiKey);
        log.info("SendGrid email provider initialized");
    }
    
    @Override
    public String getProviderName() {
        return "SENDGRID";
    }
    
    @Override
    public boolean isAvailable() {
        try {
            // Test API connectivity by getting account details
            Request request = new Request();
            request.setMethod(Method.GET);
            request.setEndpoint("user/account");
            
            Response response = sendGrid.api(request);
            return response.getStatusCode() == 200;
        } catch (Exception e) {
            log.error("SendGrid availability check failed: {}", e.getMessage());
            return false;
        }
    }
    
    @Override
    public CompletableFuture<EmailResponse> sendEmail(EmailRequest emailRequest) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("Sending email via SendGrid to: {} subject: {}", 
                        emailRequest.getTo(), emailRequest.getSubject());
                
                Mail mail = buildMail(emailRequest);
                
                Request request = new Request();
                request.setMethod(Method.POST);
                request.setEndpoint("mail/send");
                request.setBody(mail.build());
                
                Response response = sendGrid.api(request);
                
                if (response.getStatusCode() >= 200 && response.getStatusCode() < 300) {
                    String messageId = extractMessageId(response);
                    
                    return EmailResponse.builder()
                            .messageId(messageId)
                            .status(NotificationMessage.Status.SENT)
                            .providerResponse(response.getBody())
                            .sentAt(LocalDateTime.now())
                            .build();
                } else {
                    log.error("SendGrid API error: {} - {}", response.getStatusCode(), response.getBody());
                    throw new EmailDeliveryException("SendGrid API error: " + response.getBody());
                }
                
            } catch (IOException e) {
                log.error("Failed to send email via SendGrid", e);
                throw new EmailDeliveryException("Failed to send email", e);
            }
        });
    }
    
    @Override
    public CompletableFuture<EmailResponse> sendTemplatedEmail(String templateId, 
                                                             Map<String, Object> templateData,
                                                             EmailRequest emailRequest) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("Sending templated email via SendGrid template: {} to: {}", 
                        templateId, emailRequest.getTo());
                
                Mail mail = buildTemplatedMail(templateId, templateData, emailRequest);
                
                Request request = new Request();
                request.setMethod(Method.POST);
                request.setEndpoint("mail/send");
                request.setBody(mail.build());
                
                Response response = sendGrid.api(request);
                
                if (response.getStatusCode() >= 200 && response.getStatusCode() < 300) {
                    String messageId = extractMessageId(response);
                    
                    return EmailResponse.builder()
                            .messageId(messageId)
                            .status(NotificationMessage.Status.SENT)
                            .providerResponse(response.getBody())
                            .templateId(templateId)
                            .templateData(templateData)
                            .sentAt(LocalDateTime.now())
                            .build();
                } else {
                    log.error("SendGrid templated email error: {} - {}", 
                            response.getStatusCode(), response.getBody());
                    throw new EmailDeliveryException("SendGrid API error: " + response.getBody());
                }
                
            } catch (IOException e) {
                log.error("Failed to send templated email via SendGrid", e);
                throw new EmailDeliveryException("Failed to send templated email", e);
            }
        });
    }
    
    @Override
    public CompletableFuture<Void> sendBulkEmail(List<EmailRequest> emailRequests) {
        return CompletableFuture.runAsync(() -> {
            try {
                log.info("Sending bulk email via SendGrid count: {}", emailRequests.size());
                
                // Group by template for efficiency
                Map<String, List<EmailRequest>> groupedByTemplate = emailRequests.stream()
                        .collect(Collectors.groupingBy(
                                req -> req.getTemplateId() != null ? req.getTemplateId() : "no-template"
                        ));
                
                for (Map.Entry<String, List<EmailRequest>> entry : groupedByTemplate.entrySet()) {
                    String templateKey = entry.getKey();
                    List<EmailRequest> requests = entry.getValue();
                    
                    if ("no-template".equals(templateKey)) {
                        // Send individual emails for non-templated requests
                        for (EmailRequest request : requests) {
                            sendEmail(request);
                        }
                    } else {
                        // Use batch API for templated emails
                        sendBatchTemplatedEmails(templateKey, requests);
                    }
                }
                
            } catch (Exception e) {
                log.error("Failed to send bulk email via SendGrid", e);
                throw new EmailDeliveryException("Failed to send bulk email", e);
            }
        });
    }
    
    public CompletableFuture<String> createTemplate(EmailTemplate template) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("Creating SendGrid template: {}", template.getName());
                
                // Create template
                Request request = new Request();
                request.setMethod(Method.POST);
                request.setEndpoint("templates");
                
                Map<String, Object> templateData = Map.of(
                        "name", template.getName(),
                        "generation", "dynamic"
                );
                
                request.setBody(objectMapper.writeValueAsString(templateData));
                Response response = sendGrid.api(request);
                
                if (response.getStatusCode() != 201) {
                    throw new EmailDeliveryException("Failed to create template: " + response.getBody());
                }
                
                Map<String, Object> responseData = objectMapper.readValue(response.getBody(), Map.class);
                String templateId = (String) responseData.get("id");
                
                // Create template version
                createTemplateVersion(templateId, template);
                
                return templateId;
                
            } catch (Exception e) {
                log.error("Failed to create SendGrid template", e);
                throw new EmailDeliveryException("Failed to create template", e);
            }
        });
    }
    
    public CompletableFuture<Void> updateTemplate(String templateId, EmailTemplate template) {
        return CompletableFuture.runAsync(() -> {
            try {
                log.info("Updating SendGrid template: {}", templateId);
                
                // Update template metadata
                Request request = new Request();
                request.setMethod(Method.PATCH);
                request.setEndpoint("templates/" + templateId);
                
                Map<String, Object> templateData = Map.of(
                        "name", template.getName()
                );
                
                request.setBody(objectMapper.writeValueAsString(templateData));
                Response response = sendGrid.api(request);
                
                if (response.getStatusCode() != 200) {
                    throw new EmailDeliveryException("Failed to update template: " + response.getBody());
                }
                
                // Create new template version
                createTemplateVersion(templateId, template);
                
            } catch (Exception e) {
                log.error("Failed to update SendGrid template", e);
                throw new EmailDeliveryException("Failed to update template", e);
            }
        });
    }
    
    public CompletableFuture<Map<String, Object>> getEmailStats(String messageId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String cacheKey = "sendgrid-stats:" + messageId;
                Map<String, Object> cached = cacheService.get(cacheKey, Map.class);
                if (cached != null) {
                    return cached;
                }
                
                // Get email activity
                Request request = new Request();
                request.setMethod(Method.GET);
                request.setEndpoint("messages/" + messageId);
                
                Response response = sendGrid.api(request);
                
                if (response.getStatusCode() == 200) {
                    Map<String, Object> stats = objectMapper.readValue(response.getBody(), Map.class);
                    
                    // Cache for 1 hour
                    cacheService.set(cacheKey, stats, Duration.ofHours(1));
                    
                    return stats;
                } else {
                    return Map.of("status", "unknown", "message", "Stats not available");
                }
                
            } catch (Exception e) {
                log.error("Failed to get email stats from SendGrid", e);
                return Map.of("status", "error", "message", e.getMessage());
            }
        });
    }
    
    public void handleWebhook(String payload, String signature) {
        if (webhookEnabled) {
            if (!verifyWebhookSignature(payload, signature)) {
                throw new SecurityException("Invalid webhook signature");
            }
            
            try {
                List<SendGridWebhookEvent> events = parseWebhookEvents(payload);
                events.forEach(webhookHandler::handleEvent);
            } catch (Exception e) {
                log.error("Failed to process SendGrid webhook", e);
                throw new RuntimeException("Failed to process webhook", e);
            }
        }
    }
    
    // Helper methods
    
    private Mail buildMail(EmailRequest emailRequest) {
        Email from = new Email(fromEmail, fromName);
        Email to = new Email(emailRequest.getTo(), emailRequest.getToName());
        
        Mail mail = new Mail(from, emailRequest.getSubject(), to, new Content("text/html", emailRequest.getHtmlContent()));
        
        // Add plain text content if available
        if (emailRequest.getTextContent() != null) {
            mail.addContent(new Content("text/plain", emailRequest.getTextContent()));
        }
        
        // Add reply-to
        if (replyToEmail != null && !replyToEmail.isEmpty()) {
            mail.setReplyTo(new Email(replyToEmail));
        }
        
        // Add CC recipients
        if (emailRequest.getCc() != null && !emailRequest.getCc().isEmpty()) {
            Personalization personalization = new Personalization();
            personalization.addTo(to);
            emailRequest.getCc().forEach(cc -> personalization.addCc(new Email(cc)));
            mail.addPersonalization(personalization);
        }
        
        // Add BCC recipients
        if (emailRequest.getBcc() != null && !emailRequest.getBcc().isEmpty()) {
            Personalization personalization = mail.getPersonalization().get(0);
            emailRequest.getBcc().forEach(bcc -> personalization.addBcc(new Email(bcc)));
        }
        
        // Add attachments
        if (emailRequest.getAttachments() != null && !emailRequest.getAttachments().isEmpty()) {
            emailRequest.getAttachments().forEach(attachment -> {
                Attachments sendGridAttachment = new Attachments();
                sendGridAttachment.setContent(Base64.getEncoder().encodeToString(attachment.getData()));
                sendGridAttachment.setType(attachment.getMimeType());
                sendGridAttachment.setFilename(attachment.getFilename());
                sendGridAttachment.setDisposition("attachment");
                mail.addAttachments(sendGridAttachment);
            });
        }
        
        // Add tracking settings
        if (trackingEnabled) {
            addTrackingSettings(mail);
        }
        
        // Add custom headers
        if (emailRequest.getHeaders() != null && !emailRequest.getHeaders().isEmpty()) {
            emailRequest.getHeaders().forEach(mail::addHeader);
        }
        
        // Add categories for analytics
        if (emailRequest.getCategory() != null) {
            mail.addCategory(emailRequest.getCategory());
        }
        
        // Add custom arguments
        if (emailRequest.getCustomArgs() != null && !emailRequest.getCustomArgs().isEmpty()) {
            emailRequest.getCustomArgs().forEach(mail::addCustomArg);
        }
        
        return mail;
    }
    
    private Mail buildTemplatedMail(String templateId, Map<String, Object> templateData, EmailRequest emailRequest) {
        Email from = new Email(fromEmail, fromName);
        
        Mail mail = new Mail();
        mail.setFrom(from);
        mail.setTemplateId(templateId);
        
        // Add personalization
        Personalization personalization = new Personalization();
        personalization.addTo(new Email(emailRequest.getTo(), emailRequest.getToName()));
        
        // Add dynamic template data
        templateData.forEach(personalization::addDynamicTemplateData);
        
        // Add custom arguments
        if (emailRequest.getCustomArgs() != null) {
            emailRequest.getCustomArgs().forEach(personalization::addCustomArg);
        }
        
        mail.addPersonalization(personalization);
        
        // Add reply-to
        if (replyToEmail != null && !replyToEmail.isEmpty()) {
            mail.setReplyTo(new Email(replyToEmail));
        }
        
        // Add tracking settings
        if (trackingEnabled) {
            addTrackingSettings(mail);
        }
        
        // Add categories
        if (emailRequest.getCategory() != null) {
            mail.addCategory(emailRequest.getCategory());
        }
        
        return mail;
    }
    
    private void addTrackingSettings(Mail mail) {
        TrackingSettings trackingSettings = new TrackingSettings();
        
        // Click tracking
        ClickTrackingSetting clickTrackingSetting = new ClickTrackingSetting();
        clickTrackingSetting.setEnable(true);
        clickTrackingSetting.setEnableText(true);
        trackingSettings.setClickTrackingSetting(clickTrackingSetting);
        
        // Open tracking
        OpenTrackingSetting openTrackingSetting = new OpenTrackingSetting();
        openTrackingSetting.setEnable(true);
        trackingSettings.setOpenTrackingSetting(openTrackingSetting);
        
        // Subscription tracking
        SubscriptionTrackingSetting subscriptionTrackingSetting = new SubscriptionTrackingSetting();
        subscriptionTrackingSetting.setEnable(true);
        subscriptionTrackingSetting.setText("Unsubscribe");
        subscriptionTrackingSetting.setHtml("<p><a href=\"[unsubscribe]\">Unsubscribe</a></p>");
        trackingSettings.setSubscriptionTrackingSetting(subscriptionTrackingSetting);
        
        mail.setTrackingSettings(trackingSettings);
    }
    
    private void createTemplateVersion(String templateId, EmailTemplate template) throws IOException {
        Request request = new Request();
        request.setMethod(Method.POST);
        request.setEndpoint("templates/" + templateId + "/versions");
        
        Map<String, Object> versionData = Map.of(
                "name", template.getName() + " v1",
                "subject", template.getSubject(),
                "html_content", template.getHtmlContent(),
                "plain_content", template.getTextContent() != null ? template.getTextContent() : "",
                "active", 1
        );
        
        request.setBody(objectMapper.writeValueAsString(versionData));
        Response response = sendGrid.api(request);
        
        if (response.getStatusCode() != 201) {
            throw new EmailDeliveryException("Failed to create template version: " + response.getBody());
        }
    }
    
    private void sendBatchTemplatedEmails(String templateId, List<EmailRequest> requests) throws IOException {
        // Implementation for batch sending with templates
        // This would use SendGrid's batch API for efficiency
        for (EmailRequest request : requests) {
            sendTemplatedEmail(templateId, request.getTemplateData(), request);
        }
    }
    
    private String extractMessageId(Response response) {
        // Extract message ID from response headers
        Map<String, String> headers = response.getHeaders();
        return headers.getOrDefault("X-Message-Id", UUID.randomUUID().toString());
    }
    
    private boolean verifyWebhookSignature(String payload, String signature) {
        // Implement SendGrid webhook signature verification
        // This would use the webhook verification key
        return true; // Simplified for demo
    }
    
    private List<SendGridWebhookEvent> parseWebhookEvents(String payload) {
        try {
            return Arrays.asList(objectMapper.readValue(payload, SendGridWebhookEvent[].class));
        } catch (Exception e) {
            log.error("Failed to parse SendGrid webhook payload", e);
            return Collections.emptyList();
        }
    }
    
    // Inner classes for webhook events
    public static class SendGridWebhookEvent {
        private String email;
        private String event;
        private String reason;
        private String status;
        private String response;
        private String attempt;
        private String useragent;
        private String ip;
        private String url;
        private String timestamp;
        private String sg_message_id;
        private String sg_event_id;
        
        // Getters and setters
    }
}