package com.waqiti.notification.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * MongoDB document for email notifications
 */
@Document(collection = "email_notifications")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailNotification {
    
    @Id
    private String id;
    
    @Indexed
    private String userId;
    
    @Indexed
    private String recipientEmail;
    
    private String subject;
    private String body;
    private String htmlBody;
    
    @Indexed
    private String status; // pending, sent, failed, bounced, delivered
    
    private String templateId;
    private Map<String, Object> templateVariables;
    
    private String fromEmail;
    private String fromName;
    private String replyTo;
    
    private List<String> toEmails;
    private List<String> ccEmails;
    private List<String> bccEmails;
    
    private List<Attachment> attachments;
    
    @Indexed
    private LocalDateTime createdAt;
    private LocalDateTime sentAt;
    private LocalDateTime deliveredAt;
    private LocalDateTime openedAt;
    private LocalDateTime clickedAt;
    
    private String messageId;
    private String providerMessageId;
    
    private Map<String, String> headers;
    private Map<String, Object> metadata;
    
    private String priority; // low, normal, high
    private String category;
    private List<String> tags;
    
    // Tracking
    private boolean trackOpens;
    private boolean trackClicks;
    private int openCount;
    private int clickCount;
    
    // Error handling
    private String errorMessage;
    private int retryCount;
    private LocalDateTime nextRetryAt;
    
    // Compliance
    private boolean unsubscribeLink;
    private String unsubscribeUrl;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Attachment {
        private String filename;
        private String contentType;
        private String content; // Base64 encoded
        private long size;
        private String contentId; // For inline attachments
    }
}