package com.waqiti.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

/**
 * Email Request DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailRequest {
    
    @NotBlank(message = "Request ID is required")
    private String requestId;
    
    @Email(message = "Valid recipient email is required")
    @NotBlank(message = "Recipient email is required")
    private String recipient;
    
    // Optional CC and BCC recipients
    private List<String> ccRecipients;
    private List<String> bccRecipients;
    
    @NotBlank(message = "Email subject is required")
    @Size(max = 998, message = "Subject cannot exceed 998 characters")
    private String subject;
    
    @NotBlank(message = "Email body is required")
    private String body;
    
    // Optional HTML body for rich content
    private String htmlBody;
    
    // Sender information
    @Email(message = "Valid sender email is required")
    private String senderEmail;
    
    private String senderName;
    
    // Template information
    private String templateName;
    private Map<String, Object> templateData;
    
    // Attachments
    private List<EmailAttachment> attachments;
    
    // Priority
    @Builder.Default
    private EmailPriority priority = EmailPriority.NORMAL;
    
    // Custom headers
    private Map<String, String> headers;
    
    // Tracking options
    @Builder.Default
    private boolean trackOpens = true;
    
    @Builder.Default
    private boolean trackClicks = true;
    
    // Scheduling
    private java.time.Instant scheduledAt;
    
    // Tags for categorization
    private List<String> tags;
    
    // Metadata for tracking
    private Map<String, String> metadata;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EmailAttachment {
        private String filename;
        private String contentType;
        private byte[] content;
        private String contentId; // For inline attachments
        private boolean inline = false;
    }
    
    public enum EmailPriority {
        LOW(5),
        NORMAL(3),
        HIGH(1);
        
        private final int value;
        
        EmailPriority(int value) {
            this.value = value;
        }
        
        public int getValue() {
            return value;
        }
    }
}