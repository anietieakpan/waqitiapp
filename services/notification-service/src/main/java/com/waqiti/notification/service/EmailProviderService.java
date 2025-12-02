package com.waqiti.notification.service;

import com.waqiti.notification.dto.EmailRequest;
import com.waqiti.notification.dto.EmailResponse;
import com.waqiti.notification.model.EmailTemplate;
import com.waqiti.notification.integration.SendGridEmailProvider;
import com.waqiti.notification.service.provider.SesEmailProvider;
import com.waqiti.notification.service.provider.MailgunEmailProvider;
import com.waqiti.common.exception.BusinessException;
import com.waqiti.common.idempotency.EnhancedIdempotencyService;
import com.waqiti.common.idempotency.IdempotencyContext;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Email Provider Service
 * 
 * Manages email sending through multiple providers with fallback support.
 * Supports SendGrid, Amazon SES, and Mailgun.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailProviderService {

    private final SendGridEmailProvider sendGridProvider;
    private final SesEmailProvider amazonSESProvider;
    private final MailgunEmailProvider mailgunProvider;
    private final EnhancedIdempotencyService idempotencyService;
    private final EmailTemplateService emailTemplateService;
    
    @Value("${email.provider.primary:sendgrid}")
    private String primaryProvider;
    
    @Value("${email.provider.fallback:amazon-ses}")
    private String fallbackProvider;
    
    @Value("${email.provider.tertiary:mailgun}")
    private String tertiaryProvider;
    
    @Value("${email.rate-limit.per-hour:1000}")
    private Integer hourlyRateLimit;

    /**
     * Sends an email using the configured provider with fallback
     */
    @CircuitBreaker(name = "emailService", fallbackMethod = "sendEmailFallback")
    @Retry(name = "emailService")
    public EmailResponse sendEmail(EmailRequest request) {
        String idempotencyKey = "email_send_" + request.getRecipient() + "_" +
                               request.getSubject().hashCode() + "_" + request.getRequestId();

        IdempotencyContext context = IdempotencyContext.builder()
                .idempotencyKey(idempotencyKey)
                .serviceName("notification-service")
                .operationType("SEND_EMAIL")
                .build();

        return idempotencyService.executeIdempotent(context, () -> {
            log.info("Sending email to: {} with subject: {}", request.getRecipient(), request.getSubject());
            
            // Validate request
            validateEmailRequest(request);
            
            // Try primary provider first
            try {
                EmailResponse response = getEmailProvider(primaryProvider).sendEmail(request);
                response.setProvider(primaryProvider);
                log.info("Email sent successfully via {}: {}", primaryProvider, response.getMessageId());
                return response;
                
            } catch (Exception e) {
                log.warn("Primary email provider {} failed, trying fallback: {}", 
                    primaryProvider, e.getMessage());
                
                try {
                    EmailResponse response = getEmailProvider(fallbackProvider).sendEmail(request);
                    response.setProvider(fallbackProvider);
                    log.info("Email sent successfully via fallback {}: {}", 
                        fallbackProvider, response.getMessageId());
                    return response;
                    
                } catch (Exception fallbackException) {
                    log.warn("Fallback email provider {} failed, trying tertiary: {}", 
                        fallbackProvider, fallbackException.getMessage());
                    
                    try {
                        EmailResponse response = getEmailProvider(tertiaryProvider).sendEmail(request);
                        response.setProvider(tertiaryProvider);
                        log.info("Email sent successfully via tertiary {}: {}", 
                            tertiaryProvider, response.getMessageId());
                        return response;
                        
                    } catch (Exception tertiaryException) {
                        log.error("All email providers failed", tertiaryException);
                        throw new BusinessException("All email providers are unavailable");
                    }
                }
            }
        });
    }

    /**
     * Sends a templated email
     */
    @CircuitBreaker(name = "emailService")
    public EmailResponse sendTemplatedEmail(String templateName, String recipient, 
                                          Map<String, Object> templateData, String language) {
        try {
            log.info("Sending templated email: {} to: {}", templateName, recipient);
            
            // Get the email template
            EmailTemplate template = emailTemplateService.getTemplate(templateName, language);
            
            // Process template with data
            String processedSubject = emailTemplateService.processTemplate(template.getSubject(), templateData);
            String processedBody = emailTemplateService.processTemplate(template.getBody(), templateData);
            
            // Create email request
            EmailRequest request = EmailRequest.builder()
                .requestId(java.util.UUID.randomUUID().toString())
                .recipient(recipient)
                .subject(processedSubject)
                .body(processedBody)
                .htmlBody(template.getHtmlBody() != null ? 
                    emailTemplateService.processTemplate(template.getHtmlBody(), templateData) : null)
                .senderName(template.getSenderName())
                .senderEmail(template.getSenderEmail())
                .templateName(templateName)
                .templateData(templateData)
                .priority(template.getPriority())
                .build();
            
            return sendEmail(request);
            
        } catch (Exception e) {
            log.error("Failed to send templated email: {}", e.getMessage(), e);
            throw new BusinessException("Failed to send templated email: " + e.getMessage());
        }
    }

    /**
     * Sends bulk emails
     */
    @CircuitBreaker(name = "emailService")
    public List<EmailResponse> sendBulkEmails(List<EmailRequest> requests) {
        log.info("Sending bulk emails: {} recipients", requests.size());
        
        // Validate bulk request
        if (requests.size() > hourlyRateLimit) {
            throw new BusinessException("Bulk email request exceeds hourly rate limit");
        }
        
        return requests.parallelStream()
            .map(this::sendEmail)
            .toList();
    }

    /**
     * Sends a welcome email to new users
     */
    public EmailResponse sendWelcomeEmail(String recipient, String firstName, String activationLink) {
        Map<String, Object> templateData = Map.of(
            "firstName", firstName,
            "activationLink", activationLink,
            "supportEmail", "support@example.com",
            "year", java.time.Year.now().getValue()
        );
        
        return sendTemplatedEmail("welcome", recipient, templateData, "en");
    }

    /**
     * Sends password reset email
     */
    public EmailResponse sendPasswordResetEmail(String recipient, String firstName, String resetLink) {
        Map<String, Object> templateData = Map.of(
            "firstName", firstName,
            "resetLink", resetLink,
            "expiryTime", "24 hours",
            "supportEmail", "support@example.com"
        );
        
        return sendTemplatedEmail("password-reset", recipient, templateData, "en");
    }

    /**
     * Sends transaction notification email
     */
    public EmailResponse sendTransactionNotificationEmail(String recipient, String firstName, 
                                                         String transactionType, String amount, 
                                                         String currency, String transactionId) {
        Map<String, Object> templateData = Map.of(
            "firstName", firstName,
            "transactionType", transactionType,
            "amount", amount,
            "currency", currency,
            "transactionId", transactionId,
            "transactionDate", Instant.now().toString(),
            "supportEmail", "support@example.com"
        );
        
        return sendTemplatedEmail("transaction-notification", recipient, templateData, "en");
    }

    /**
     * Sends security alert email
     */
    public EmailResponse sendSecurityAlertEmail(String recipient, String firstName, 
                                              String alertType, String deviceInfo, String location) {
        Map<String, Object> templateData = Map.of(
            "firstName", firstName,
            "alertType", alertType,
            "deviceInfo", deviceInfo,
            "location", location,
            "timestamp", Instant.now().toString(),
            "supportEmail", "support@example.com",
            "securityUrl", "https://app.example.com/security"
        );
        
        return sendTemplatedEmail("security-alert", recipient, templateData, "en");
    }

    /**
     * Sends KYC status notification email
     */
    public EmailResponse sendKycStatusEmail(String recipient, String firstName, 
                                          String status, String nextSteps) {
        Map<String, Object> templateData = Map.of(
            "firstName", firstName,
            "status", status,
            "nextSteps", nextSteps,
            "kycUrl", "https://app.example.com/kyc",
            "supportEmail", "support@example.com"
        );
        
        return sendTemplatedEmail("kyc-status", recipient, templateData, "en");
    }

    /**
     * Gets delivery status of an email
     */
    @CircuitBreaker(name = "emailService")
    public EmailResponse getEmailStatus(String messageId, String provider) {
        try {
            return getEmailProvider(provider).getEmailStatus(messageId);
        } catch (Exception e) {
            log.error("Failed to get email status: {}", e.getMessage(), e);
            throw new BusinessException("Failed to get email status: " + e.getMessage());
        }
    }

    private void validateEmailRequest(EmailRequest request) {
        if (request.getRecipient() == null || request.getRecipient().trim().isEmpty()) {
            throw new BusinessException("Recipient email is required");
        }
        
        if (request.getSubject() == null || request.getSubject().trim().isEmpty()) {
            throw new BusinessException("Email subject is required");
        }
        
        if (request.getBody() == null || request.getBody().trim().isEmpty()) {
            throw new BusinessException("Email body is required");
        }
        
        // Validate email format
        if (!isValidEmail(request.getRecipient())) {
            throw new BusinessException("Invalid email format: " + request.getRecipient());
        }
    }

    private boolean isValidEmail(String email) {
        return email.matches("^[A-Za-z0-9+_.-]+@([A-Za-z0-9.-]+\\.[A-Za-z]{2,})$");
    }

    private EmailProvider getEmailProvider(String providerName) {
        return switch (providerName.toLowerCase()) {
            case "sendgrid" -> sendGridProvider;
            case "amazon-ses", "ses" -> amazonSESProvider;
            case "mailgun" -> mailgunProvider;
            default -> throw new BusinessException("Unknown email provider: " + providerName);
        };
    }

    // Fallback method
    public EmailResponse sendEmailFallback(EmailRequest request, Exception ex) {
        log.error("Email service fallback triggered for recipient: {}", request.getRecipient(), ex);
        
        EmailResponse response = new EmailResponse();
        response.setRequestId(request.getRequestId());
        response.setRecipient(request.getRecipient());
        response.setStatus("FAILED");
        response.setErrorMessage("All email providers are unavailable: " + ex.getMessage());
        response.setSentAt(Instant.now());
        
        return response;
    }
}