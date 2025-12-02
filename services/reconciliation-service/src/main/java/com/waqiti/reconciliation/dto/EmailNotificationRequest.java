package com.waqiti.reconciliation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailNotificationRequest {

    private String to;
    
    private List<String> cc;
    
    private List<String> bcc;
    
    private String subject;
    
    private String body;
    
    private boolean isHtml;
    
    private NotificationPriority priority;
    
    private String templateId;
    
    private Map<String, Object> templateVariables;
    
    private List<EmailAttachment> attachments;
    
    private String sender;
    
    private String replyTo;
    
    @Builder.Default
    private LocalDateTime scheduledFor = LocalDateTime.now();
    
    private boolean trackOpens;
    
    private boolean trackClicks;
    
    private String campaignId;

    public enum NotificationPriority {
        LOW,
        NORMAL,
        HIGH,
        URGENT
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EmailAttachment {
        private String fileName;
        private String contentType;
        private byte[] content;
        private boolean isInline;
        private String contentId;
    }

    public boolean hasTemplate() {
        return templateId != null && !templateId.isEmpty();
    }

    public boolean hasAttachments() {
        return attachments != null && !attachments.isEmpty();
    }

    public boolean isScheduled() {
        return scheduledFor != null && scheduledFor.isAfter(LocalDateTime.now());
    }

    public boolean hasTracking() {
        return trackOpens || trackClicks;
    }
}