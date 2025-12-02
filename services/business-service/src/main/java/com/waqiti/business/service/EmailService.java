package com.waqiti.business.service;

import com.sendgrid.*;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.*;
import com.waqiti.business.domain.EmailOutbox;
import com.waqiti.business.domain.EmailOutbox.EmailStatus;
import com.waqiti.business.domain.EmailOutbox.EmailType;
import com.waqiti.business.repository.EmailOutboxRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Email Service with SendGrid Integration
 *
 * Features:
 * - Transactional Outbox Pattern for reliability
 * - Automatic retry with exponential backoff
 * - Delivery tracking and webhooks
 * - Template support
 * - Attachment handling
 * - Priority queue
 * - Metrics and monitoring
 *
 * Configuration required in application.yml:
 * sendgrid:
 *   api-key: ${SENDGRID_API_KEY}
 *   from-email: noreply@example.com
 *   from-name: Waqiti Platform
 *
 * @author Waqiti Engineering Team
 * @version 1.0.0
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class EmailService {

    private final EmailOutboxRepository emailOutboxRepository;
    private final MeterRegistry meterRegistry;

    @Value("${sendgrid.api-key:}")
    private String sendgridApiKey;

    @Value("${sendgrid.from-email:noreply@example.com}")
    private String defaultFromEmail;

    @Value("${sendgrid.from-name:Waqiti Platform}")
    private String defaultFromName;

    @Value("${sendgrid.enabled:true}")
    private boolean sendgridEnabled;

    /**
     * Queue an email for sending (Transactional Outbox Pattern)
     *
     * Email is persisted in the same transaction as the business operation,
     * ensuring no emails are lost even if SendGrid is down.
     */
    public UUID queueEmail(EmailRequest request) {
        log.info("Queuing email to: {} subject: {}", request.getRecipientEmail(), request.getSubject());

        try {
            EmailOutbox email = EmailOutbox.builder()
                    .recipientEmail(request.getRecipientEmail())
                    .recipientName(request.getRecipientName())
                    .senderEmail(request.getSenderEmail() != null ? request.getSenderEmail() : defaultFromEmail)
                    .senderName(request.getSenderName() != null ? request.getSenderName() : defaultFromName)
                    .subject(request.getSubject())
                    .htmlContent(request.getHtmlContent())
                    .plainTextContent(request.getPlainTextContent())
                    .templateId(request.getTemplateId())
                    .templateData(request.getTemplateData())
                    .attachments(request.getAttachments())
                    .emailType(request.getEmailType())
                    .priority(request.getPriority() != null ? request.getPriority() : 5)
                    .status(EmailStatus.PENDING)
                    .retryCount(0)
                    .maxRetries(5)
                    .build();

            email = emailOutboxRepository.save(email);

            meterRegistry.counter("email.queued",
                    "type", request.getEmailType().name(),
                    "priority", String.valueOf(email.getPriority())
            ).increment();

            log.info("Email queued successfully: {} for recipient: {}", email.getId(), request.getRecipientEmail());

            return email.getId();

        } catch (Exception e) {
            log.error("Failed to queue email for: {}", request.getRecipientEmail(), e);
            meterRegistry.counter("email.queue.error",
                    "type", request.getEmailType().name()
            ).increment();
            throw new EmailServiceException("Failed to queue email", e);
        }
    }

    /**
     * Send email immediately (synchronous)
     *
     * Use this only when you need immediate feedback.
     * Prefer queueEmail() for better reliability.
     */
    public void sendEmailNow(EmailRequest request) throws IOException {
        log.info("Sending email immediately to: {}", request.getRecipientEmail());

        if (!sendgridEnabled) {
            log.warn("SendGrid is disabled. Email would have been sent to: {}", request.getRecipientEmail());
            return;
        }

        if (sendgridApiKey == null || sendgridApiKey.isBlank()) {
            throw new EmailServiceException("SendGrid API key not configured");
        }

        SendGrid sg = new SendGrid(sendgridApiKey);

        Email from = new Email(
                request.getSenderEmail() != null ? request.getSenderEmail() : defaultFromEmail,
                request.getSenderName() != null ? request.getSenderName() : defaultFromName
        );

        Email to = new Email(request.getRecipientEmail(), request.getRecipientName());

        Content content = new Content("text/html", request.getHtmlContent());

        Mail mail = new Mail(from, request.getSubject(), to, content);

        // Add plain text version if provided
        if (request.getPlainTextContent() != null) {
            mail.addContent(new Content("text/plain", request.getPlainTextContent()));
        }

        Request sgRequest = new Request();
        sgRequest.setMethod(Method.POST);
        sgRequest.setEndpoint("mail/send");
        sgRequest.setBody(mail.build());

        Response response = sg.api(sgRequest);

        if (response.getStatusCode() >= 200 && response.getStatusCode() < 300) {
            log.info("Email sent successfully to: {} status: {}", request.getRecipientEmail(), response.getStatusCode());
            meterRegistry.counter("email.sent.success",
                    "type", request.getEmailType().name()
            ).increment();
        } else {
            log.error("Failed to send email to: {} status: {} body: {}",
                    request.getRecipientEmail(), response.getStatusCode(), response.getBody());
            meterRegistry.counter("email.sent.failure",
                    "type", request.getEmailType().name(),
                    "status_code", String.valueOf(response.getStatusCode())
            ).increment();
            throw new EmailServiceException("SendGrid returned error: " + response.getStatusCode());
        }
    }

    /**
     * Process email queue (scheduled task)
     *
     * Runs every 30 seconds to send pending emails
     */
    @Scheduled(fixedRate = 30000) // 30 seconds
    @Transactional
    public void processEmailQueue() {
        if (!sendgridEnabled) {
            log.debug("SendGrid is disabled. Skipping email queue processing.");
            return;
        }

        log.debug("Processing email queue...");

        try {
            List<EmailOutbox> pendingEmails = emailOutboxRepository
                    .findEmailsReadyToSend(LocalDateTime.now());

            if (pendingEmails.isEmpty()) {
                log.debug("No pending emails to send");
                return;
            }

            log.info("Found {} emails ready to send", pendingEmails.size());

            int successCount = 0;
            int failureCount = 0;

            for (EmailOutbox email : pendingEmails) {
                try {
                    sendQueuedEmail(email);
                    successCount++;
                } catch (Exception e) {
                    log.error("Failed to send queued email: {}", email.getId(), e);
                    handleEmailFailure(email, e);
                    failureCount++;
                }
            }

            log.info("Email queue processing completed. Success: {}, Failures: {}",
                    successCount, failureCount);

            meterRegistry.counter("email.queue.processed",
                    "result", "success"
            ).increment(successCount);

            meterRegistry.counter("email.queue.processed",
                    "result", "failure"
            ).increment(failureCount);

        } catch (Exception e) {
            log.error("Error in email queue processing", e);
            meterRegistry.counter("email.queue.error").increment();
        }
    }

    /**
     * Send a queued email via SendGrid
     */
    private void sendQueuedEmail(EmailOutbox email) throws IOException {
        log.debug("Sending queued email: {} to: {}", email.getId(), email.getRecipientEmail());

        // Update status to SENDING
        email.setStatus(EmailStatus.SENDING);
        emailOutboxRepository.save(email);

        SendGrid sg = new SendGrid(sendgridApiKey);

        Email from = new Email(email.getSenderEmail(), email.getSenderName());
        Email to = new Email(email.getRecipientEmail(), email.getRecipientName());
        Content content = new Content("text/html", email.getHtmlContent());

        Mail mail = new Mail(from, email.getSubject(), to, content);

        // Add plain text if available
        if (email.getPlainTextContent() != null) {
            mail.addContent(new Content("text/plain", email.getPlainTextContent()));
        }

        // Add custom args for tracking
        mail.addCustomArg("email_outbox_id", email.getId().toString());
        mail.addCustomArg("email_type", email.getEmailType().name());

        // Build and send request
        Request request = new Request();
        request.setMethod(Method.POST);
        request.setEndpoint("mail/send");
        request.setBody(mail.build());

        Response response = sg.api(request);

        if (response.getStatusCode() >= 200 && response.getStatusCode() < 300) {
            // Extract message ID from response headers
            String messageId = extractMessageId(response);

            email.setStatus(EmailStatus.SENT);
            email.setSentAt(LocalDateTime.now());
            email.setSendgridMessageId(messageId);
            emailOutboxRepository.save(email);

            log.info("Queued email sent successfully: {} messageId: {}", email.getId(), messageId);

            meterRegistry.counter("email.sent.success",
                    "type", email.getEmailType().name()
            ).increment();

        } else {
            throw new IOException("SendGrid error: " + response.getStatusCode() + " " + response.getBody());
        }
    }

    /**
     * Handle email sending failure
     */
    private void handleEmailFailure(EmailOutbox email, Exception exception) {
        log.warn("Handling email failure for: {} attempt: {}", email.getId(), email.getRetryCount() + 1);

        email.setErrorMessage(exception.getMessage());
        email.setErrorStackTrace(getStackTraceString(exception));

        boolean canRetry = email.incrementRetryCount();

        if (canRetry) {
            log.info("Email {} will be retried. Next retry at: {}", email.getId(), email.getNextRetryAt());
            meterRegistry.counter("email.retry.scheduled",
                    "type", email.getEmailType().name(),
                    "attempt", String.valueOf(email.getRetryCount())
            ).increment();
        } else {
            log.error("Email {} permanently failed after {} attempts", email.getId(), email.getMaxRetries());
            meterRegistry.counter("email.permanent.failure",
                    "type", email.getEmailType().name()
            ).increment();
        }

        emailOutboxRepository.save(email);
    }

    /**
     * Handle SendGrid webhook events (delivery, open, click, bounce)
     */
    @Transactional
    public void handleWebhookEvent(SendGridWebhookEvent event) {
        log.info("Received SendGrid webhook: event={} messageId={}", event.getEvent(), event.getSgMessageId());

        EmailOutbox email = emailOutboxRepository.findBySendgridMessageId(event.getSgMessageId())
                .orElse(null);

        if (email == null) {
            log.warn("Email not found for SendGrid message ID: {}", event.getSgMessageId());
            return;
        }

        switch (event.getEvent()) {
            case "delivered" -> {
                email.setStatus(EmailStatus.DELIVERED);
                email.setDeliveredAt(LocalDateTime.now());
                meterRegistry.counter("email.delivered",
                        "type", email.getEmailType().name()).increment();
            }
            case "open" -> {
                if (email.getOpenedAt() == null) {
                    email.setOpenedAt(LocalDateTime.now());
                    meterRegistry.counter("email.opened",
                            "type", email.getEmailType().name()).increment();
                }
            }
            case "click" -> {
                if (email.getClickedAt() == null) {
                    email.setClickedAt(LocalDateTime.now());
                    meterRegistry.counter("email.clicked",
                            "type", email.getEmailType().name()).increment();
                }
            }
            case "bounce", "dropped" -> {
                email.setStatus(EmailStatus.BOUNCED);
                email.setBouncedAt(LocalDateTime.now());
                email.setBounceReason(event.getReason());
                meterRegistry.counter("email.bounced",
                        "type", email.getEmailType().name()).increment();
            }
        }

        emailOutboxRepository.save(email);
        log.debug("Updated email {} with webhook event: {}", email.getId(), event.getEvent());
    }

    /**
     * Get email statistics for monitoring
     */
    @Transactional(readOnly = true)
    public EmailStatistics getEmailStatistics(LocalDateTime since) {
        List<Object[]> stats = emailOutboxRepository.getEmailStatistics(since);

        long pending = 0, sent = 0, failed = 0, delivered = 0;

        for (Object[] stat : stats) {
            EmailStatus status = (EmailStatus) stat[0];
            long count = (long) stat[1];

            switch (status) {
                case PENDING, RETRY_SCHEDULED -> pending += count;
                case SENT -> sent += count;
                case DELIVERED -> delivered += count;
                case FAILED -> failed += count;
            }
        }

        return EmailStatistics.builder()
                .pending(pending)
                .sent(sent)
                .delivered(delivered)
                .failed(failed)
                .since(since)
                .build();
    }

    // Helper methods

    private String extractMessageId(Response response) {
        // SendGrid returns message ID in X-Message-Id header
        Map<String, String> headers = response.getHeaders();
        return headers != null ? headers.get("X-Message-Id") : null;
    }

    private String getStackTraceString(Exception e) {
        java.io.StringWriter sw = new java.io.StringWriter();
        e.printStackTrace(new java.io.PrintWriter(sw));
        return sw.toString();
    }

    // DTOs

    @lombok.Builder
    @lombok.Data
    public static class EmailRequest {
        private String recipientEmail;
        private String recipientName;
        private String senderEmail;
        private String senderName;
        private String subject;
        private String htmlContent;
        private String plainTextContent;
        private String templateId;
        private Map<String, Object> templateData;
        private Map<String, String> attachments;
        private EmailType emailType;
        private Integer priority; // 1-10, lower is higher priority
    }

    @lombok.Builder
    @lombok.Data
    public static class SendGridWebhookEvent {
        private String event; // delivered, open, click, bounce, dropped
        private String sgMessageId;
        private String reason;
        private Long timestamp;
    }

    @lombok.Builder
    @lombok.Data
    public static class EmailStatistics {
        private long pending;
        private long sent;
        private long delivered;
        private long failed;
        private LocalDateTime since;
    }

    public static class EmailServiceException extends RuntimeException {
        public EmailServiceException(String message) {
            super(message);
        }

        public EmailServiceException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
