package com.waqiti.payment.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.mail.*;
import javax.mail.internet.*;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for sending emails with various templates and attachments
 */
@Slf4j
@Service
public class EmailService {
    
    @Value("${email.smtp.host:smtp.gmail.com}")
    private String smtpHost;
    
    @Value("${email.smtp.port:587}")
    private int smtpPort;
    
    @Value("${email.smtp.username:}")
    private String smtpUsername;
    
    @Value("${email.smtp.password:}")
    private String smtpPassword;
    
    @Value("${email.smtp.auth:true}")
    private boolean smtpAuth;
    
    @Value("${email.smtp.starttls:true}")
    private boolean smtpStartTls;
    
    @Value("${email.from.address:noreply@example.com}")
    private String fromAddress;
    
    @Value("${email.from.name:Waqiti Platform}")
    private String fromName;
    
    @Value("${email.enabled:true}")
    private boolean emailEnabled;
    
    @Value("${email.async:true}")
    private boolean asyncEnabled;
    
    private final Map<String, EmailTemplate> templateCache = new ConcurrentHashMap<>();
    private final Map<String, EmailStatistics> statisticsMap = new ConcurrentHashMap<>();
    
    /**
     * Send simple text email
     */
    public CompletableFuture<Boolean> sendEmail(String to, String subject, String body) {
        return sendEmail(EmailMessage.builder()
                .to(Collections.singletonList(to))
                .subject(subject)
                .body(body)
                .build());
    }
    
    /**
     * Send email with EmailMessage object
     */
    @Async
    public CompletableFuture<Boolean> sendEmail(EmailMessage message) {
        if (!emailEnabled) {
            log.info("Email service is disabled, skipping email to: {}", message.getTo());
            return CompletableFuture.completedFuture(true);
        }
        
        log.info("Sending email to: {} subject: {}", message.getTo(), message.getSubject());
        
        try {
            // Get mail session
            Session session = createMailSession();
            
            // Create message
            MimeMessage mimeMessage = createMimeMessage(session, message);
            
            // Send message
            Transport.send(mimeMessage);
            
            // Track statistics
            trackEmailSent(message);
            
            log.info("Email sent successfully to: {}", message.getTo());
            return CompletableFuture.completedFuture(true);
            
        } catch (Exception e) {
            log.error("Failed to send email to: {} error: {}", message.getTo(), e.getMessage(), e);
            trackEmailFailed(message, e);
            return CompletableFuture.completedFuture(false);
        }
    }
    
    /**
     * Send email using template
     */
    @Async
    public CompletableFuture<Boolean> sendTemplateEmail(String to, String templateName, Map<String, Object> variables) {
        log.info("Sending template email to: {} template: {}", to, templateName);
        
        EmailTemplate template = getTemplate(templateName);
        if (template == null) {
            log.error("Email template not found: {}", templateName);
            return CompletableFuture.completedFuture(false);
        }
        
        String subject = processTemplate(template.getSubject(), variables);
        String body = processTemplate(template.getBody(), variables);
        
        return sendEmail(EmailMessage.builder()
                .to(Collections.singletonList(to))
                .subject(subject)
                .body(body)
                .html(template.isHtml())
                .build());
    }
    
    /**
     * Send invoice email with PDF attachment
     */
    @Async
    public CompletableFuture<Boolean> sendInvoiceEmail(String to, String invoiceNumber, byte[] pdfBytes) {
        log.info("Sending invoice email to: {} invoice: {}", to, invoiceNumber);
        
        Map<String, Object> variables = new HashMap<>();
        variables.put("invoiceNumber", invoiceNumber);
        variables.put("customerEmail", to);
        
        EmailAttachment attachment = EmailAttachment.builder()
                .filename("Invoice-" + invoiceNumber + ".pdf")
                .content(pdfBytes)
                .contentType("application/pdf")
                .build();
        
        EmailMessage message = EmailMessage.builder()
                .to(Collections.singletonList(to))
                .subject("Invoice " + invoiceNumber)
                .body(processTemplate(getInvoiceEmailTemplate(), variables))
                .html(true)
                .attachments(Collections.singletonList(attachment))
                .build();
        
        return sendEmail(message);
    }
    
    /**
     * Send payment confirmation email
     */
    @Async
    public CompletableFuture<Boolean> sendPaymentConfirmation(String to, String paymentId, String amount) {
        log.info("Sending payment confirmation to: {} payment: {}", to, paymentId);
        
        Map<String, Object> variables = new HashMap<>();
        variables.put("paymentId", paymentId);
        variables.put("amount", amount);
        variables.put("timestamp", LocalDateTime.now());
        
        return sendTemplateEmail(to, "payment-confirmation", variables);
    }
    
    /**
     * Send payment reminder email
     */
    @Async
    public CompletableFuture<Boolean> sendPaymentReminder(String to, String invoiceNumber, String dueDate, String amount) {
        log.info("Sending payment reminder to: {} invoice: {}", to, invoiceNumber);
        
        Map<String, Object> variables = new HashMap<>();
        variables.put("invoiceNumber", invoiceNumber);
        variables.put("dueDate", dueDate);
        variables.put("amount", amount);
        
        return sendTemplateEmail(to, "payment-reminder", variables);
    }
    
    /**
     * Send bulk emails
     */
    @Async
    public CompletableFuture<BulkEmailResult> sendBulkEmails(List<EmailMessage> messages) {
        log.info("Sending bulk emails to {} recipients", messages.size());
        
        BulkEmailResult result = new BulkEmailResult();
        result.setTotal(messages.size());
        
        List<CompletableFuture<Boolean>> futures = new ArrayList<>();
        
        for (EmailMessage message : messages) {
            CompletableFuture<Boolean> future = sendEmail(message);
            futures.add(future);
        }
        
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenRun(() -> {
                    int successful = 0;
                    int failed = 0;
                    
                    for (CompletableFuture<Boolean> future : futures) {
                        try {
                            if (future.get()) {
                                successful++;
                            } else {
                                failed++;
                            }
                        } catch (Exception e) {
                            failed++;
                        }
                    }
                    
                    result.setSuccessful(successful);
                    result.setFailed(failed);
                });
        
        return CompletableFuture.completedFuture(result);
    }
    
    /**
     * Validate email address
     */
    public boolean isValidEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return false;
        }
        
        try {
            InternetAddress emailAddr = new InternetAddress(email);
            emailAddr.validate();
            return true;
        } catch (AddressException e) {
            return false;
        }
    }
    
    /**
     * Get email statistics
     */
    public EmailStatistics getStatistics(String category) {
        return statisticsMap.getOrDefault(category, new EmailStatistics());
    }
    
    /**
     * Register email template
     */
    public void registerTemplate(String name, EmailTemplate template) {
        templateCache.put(name, template);
        log.info("Registered email template: {}", name);
    }
    
    // Private helper methods
    
    private Session createMailSession() {
        Properties props = new Properties();
        props.put("mail.smtp.host", smtpHost);
        props.put("mail.smtp.port", smtpPort);
        props.put("mail.smtp.auth", smtpAuth);
        props.put("mail.smtp.starttls.enable", smtpStartTls);
        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout", "10000");
        props.put("mail.smtp.writetimeout", "10000");
        
        Authenticator auth = null;
        if (smtpAuth) {
            auth = new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(smtpUsername, smtpPassword);
                }
            };
        }
        
        return Session.getInstance(props, auth);
    }
    
    private MimeMessage createMimeMessage(Session session, EmailMessage message) throws MessagingException, IOException {
        MimeMessage mimeMessage = new MimeMessage(session);
        
        // Set from
        mimeMessage.setFrom(new InternetAddress(fromAddress, fromName));
        
        // Set to
        for (String to : message.getTo()) {
            mimeMessage.addRecipient(Message.RecipientType.TO, new InternetAddress(to));
        }
        
        // Set cc
        if (message.getCc() != null) {
            for (String cc : message.getCc()) {
                mimeMessage.addRecipient(Message.RecipientType.CC, new InternetAddress(cc));
            }
        }
        
        // Set bcc
        if (message.getBcc() != null) {
            for (String bcc : message.getBcc()) {
                mimeMessage.addRecipient(Message.RecipientType.BCC, new InternetAddress(bcc));
            }
        }
        
        // Set subject
        mimeMessage.setSubject(message.getSubject(), "UTF-8");
        
        // Set body and attachments
        if (message.getAttachments() != null && !message.getAttachments().isEmpty()) {
            MimeMultipart multipart = new MimeMultipart();
            
            // Add body
            MimeBodyPart textPart = new MimeBodyPart();
            if (message.isHtml()) {
                textPart.setContent(message.getBody(), "text/html; charset=UTF-8");
            } else {
                textPart.setText(message.getBody(), "UTF-8");
            }
            multipart.addBodyPart(textPart);
            
            // Add attachments
            for (EmailAttachment attachment : message.getAttachments()) {
                MimeBodyPart attachmentPart = new MimeBodyPart();
                attachmentPart.setFileName(attachment.getFilename());
                attachmentPart.setContent(attachment.getContent(), attachment.getContentType());
                multipart.addBodyPart(attachmentPart);
            }
            
            mimeMessage.setContent(multipart);
        } else {
            // Simple message without attachments
            if (message.isHtml()) {
                mimeMessage.setContent(message.getBody(), "text/html; charset=UTF-8");
            } else {
                mimeMessage.setText(message.getBody(), "UTF-8");
            }
        }
        
        // Set headers
        if (message.getHeaders() != null) {
            for (Map.Entry<String, String> header : message.getHeaders().entrySet()) {
                mimeMessage.addHeader(header.getKey(), header.getValue());
            }
        }
        
        // Set priority
        if (message.getPriority() != null) {
            mimeMessage.addHeader("X-Priority", String.valueOf(message.getPriority()));
        }
        
        return mimeMessage;
    }
    
    private String processTemplate(String template, Map<String, Object> variables) {
        if (template == null || variables == null) {
            return template;
        }
        
        String processed = template;
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            String placeholder = "{{" + entry.getKey() + "}}";
            String value = entry.getValue() != null ? entry.getValue().toString() : "";
            processed = processed.replace(placeholder, value);
        }
        
        return processed;
    }
    
    private EmailTemplate getTemplate(String name) {
        // Check cache first
        EmailTemplate template = templateCache.get(name);
        if (template != null) {
            return template;
        }
        
        // Load default templates
        loadDefaultTemplates();
        
        return templateCache.get(name);
    }
    
    private void loadDefaultTemplates() {
        // Payment confirmation template
        templateCache.putIfAbsent("payment-confirmation", EmailTemplate.builder()
                .name("payment-confirmation")
                .subject("Payment Confirmation - {{paymentId}}")
                .body("<html><body>" +
                     "<h2>Payment Confirmation</h2>" +
                     "<p>Your payment of {{amount}} has been successfully processed.</p>" +
                     "<p>Payment ID: {{paymentId}}</p>" +
                     "<p>Timestamp: {{timestamp}}</p>" +
                     "<p>Thank you for your payment!</p>" +
                     "</body></html>")
                .html(true)
                .build());
        
        // Payment reminder template
        templateCache.putIfAbsent("payment-reminder", EmailTemplate.builder()
                .name("payment-reminder")
                .subject("Payment Reminder - Invoice {{invoiceNumber}}")
                .body("<html><body>" +
                     "<h2>Payment Reminder</h2>" +
                     "<p>This is a reminder that invoice {{invoiceNumber}} is due on {{dueDate}}.</p>" +
                     "<p>Amount due: {{amount}}</p>" +
                     "<p>Please make your payment at your earliest convenience.</p>" +
                     "</body></html>")
                .html(true)
                .build());
    }
    
    private String getInvoiceEmailTemplate() {
        return "<html><body>" +
               "<h2>Invoice {{invoiceNumber}}</h2>" +
               "<p>Please find attached your invoice.</p>" +
               "<p>If you have any questions, please contact us.</p>" +
               "<p>Thank you for your business!</p>" +
               "</body></html>";
    }
    
    private void trackEmailSent(EmailMessage message) {
        String category = message.getCategory() != null ? message.getCategory() : "default";
        EmailStatistics stats = statisticsMap.computeIfAbsent(category, k -> new EmailStatistics());
        stats.incrementSent();
    }
    
    private void trackEmailFailed(EmailMessage message, Exception error) {
        String category = message.getCategory() != null ? message.getCategory() : "default";
        EmailStatistics stats = statisticsMap.computeIfAbsent(category, k -> new EmailStatistics());
        stats.incrementFailed();
        stats.addError(error.getMessage());
    }
    
    // Inner classes
    
    public static class EmailMessage {
        private List<String> to;
        private List<String> cc;
        private List<String> bcc;
        private String subject;
        private String body;
        private boolean html;
        private List<EmailAttachment> attachments;
        private Map<String, String> headers;
        private Integer priority;
        private String category;
        
        private EmailMessage(Builder builder) {
            this.to = builder.to;
            this.cc = builder.cc;
            this.bcc = builder.bcc;
            this.subject = builder.subject;
            this.body = builder.body;
            this.html = builder.html;
            this.attachments = builder.attachments;
            this.headers = builder.headers;
            this.priority = builder.priority;
            this.category = builder.category;
        }
        
        public static Builder builder() {
            return new Builder();
        }
        
        public static class Builder {
            private List<String> to;
            private List<String> cc;
            private List<String> bcc;
            private String subject;
            private String body;
            private boolean html = false;
            private List<EmailAttachment> attachments;
            private Map<String, String> headers;
            private Integer priority;
            private String category;
            
            public Builder to(List<String> to) {
                this.to = to;
                return this;
            }
            
            public Builder cc(List<String> cc) {
                this.cc = cc;
                return this;
            }
            
            public Builder bcc(List<String> bcc) {
                this.bcc = bcc;
                return this;
            }
            
            public Builder subject(String subject) {
                this.subject = subject;
                return this;
            }
            
            public Builder body(String body) {
                this.body = body;
                return this;
            }
            
            public Builder html(boolean html) {
                this.html = html;
                return this;
            }
            
            public Builder attachments(List<EmailAttachment> attachments) {
                this.attachments = attachments;
                return this;
            }
            
            public Builder headers(Map<String, String> headers) {
                this.headers = headers;
                return this;
            }
            
            public Builder priority(Integer priority) {
                this.priority = priority;
                return this;
            }
            
            public Builder category(String category) {
                this.category = category;
                return this;
            }
            
            public EmailMessage build() {
                return new EmailMessage(this);
            }
        }
        
        // Getters
        public List<String> getTo() { return to; }
        public List<String> getCc() { return cc; }
        public List<String> getBcc() { return bcc; }
        public String getSubject() { return subject; }
        public String getBody() { return body; }
        public boolean isHtml() { return html; }
        public List<EmailAttachment> getAttachments() { return attachments; }
        public Map<String, String> getHeaders() { return headers; }
        public Integer getPriority() { return priority; }
        public String getCategory() { return category; }
    }
    
    public static class EmailAttachment {
        private String filename;
        private byte[] content;
        private String contentType;
        
        private EmailAttachment(Builder builder) {
            this.filename = builder.filename;
            this.content = builder.content;
            this.contentType = builder.contentType;
        }
        
        public static Builder builder() {
            return new Builder();
        }
        
        public static class Builder {
            private String filename;
            private byte[] content;
            private String contentType;
            
            public Builder filename(String filename) {
                this.filename = filename;
                return this;
            }
            
            public Builder content(byte[] content) {
                this.content = content;
                return this;
            }
            
            public Builder contentType(String contentType) {
                this.contentType = contentType;
                return this;
            }
            
            public EmailAttachment build() {
                return new EmailAttachment(this);
            }
        }
        
        // Getters
        public String getFilename() { return filename; }
        public byte[] getContent() { return content; }
        public String getContentType() { return contentType; }
    }
    
    public static class EmailTemplate {
        private String name;
        private String subject;
        private String body;
        private boolean html;
        
        private EmailTemplate(Builder builder) {
            this.name = builder.name;
            this.subject = builder.subject;
            this.body = builder.body;
            this.html = builder.html;
        }
        
        public static Builder builder() {
            return new Builder();
        }
        
        public static class Builder {
            private String name;
            private String subject;
            private String body;
            private boolean html;
            
            public Builder name(String name) {
                this.name = name;
                return this;
            }
            
            public Builder subject(String subject) {
                this.subject = subject;
                return this;
            }
            
            public Builder body(String body) {
                this.body = body;
                return this;
            }
            
            public Builder html(boolean html) {
                this.html = html;
                return this;
            }
            
            public EmailTemplate build() {
                return new EmailTemplate(this);
            }
        }
        
        // Getters
        public String getName() { return name; }
        public String getSubject() { return subject; }
        public String getBody() { return body; }
        public boolean isHtml() { return html; }
    }
    
    public static class EmailStatistics {
        private long sent = 0;
        private long failed = 0;
        private List<String> errors = new ArrayList<>();
        
        public synchronized void incrementSent() {
            sent++;
        }
        
        public synchronized void incrementFailed() {
            failed++;
        }
        
        public synchronized void addError(String error) {
            errors.add(error);
            if (errors.size() > 100) {
                errors.remove(0);
            }
        }
        
        // Getters
        public long getSent() { return sent; }
        public long getFailed() { return failed; }
        public List<String> getErrors() { return new ArrayList<>(errors); }
        public double getSuccessRate() {
            long total = sent + failed;
            return total > 0 ? (double) sent / total * 100 : 0;
        }
    }
    
    public static class BulkEmailResult {
        private int total;
        private int successful;
        private int failed;
        
        // Getters and setters
        public int getTotal() { return total; }
        public void setTotal(int total) { this.total = total; }
        public int getSuccessful() { return successful; }
        public void setSuccessful(int successful) { this.successful = successful; }
        public int getFailed() { return failed; }
        public void setFailed(int failed) { this.failed = failed; }
        public double getSuccessRate() {
            return total > 0 ? (double) successful / total * 100 : 0;
        }
    }
}