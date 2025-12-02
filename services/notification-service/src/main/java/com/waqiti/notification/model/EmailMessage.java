package com.waqiti.notification.model;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Model representing an email message to be sent
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailMessage implements Comparable<EmailMessage> {
    
    private String messageId;
    private String recipientEmail;
    private String senderEmail;
    private String senderName;
    private String subject;
    private String htmlBody;
    private String textBody;
    private int priority; // 1=high, 3=normal, 5=low
    private String category;
    private String templateName;
    private String batchId;
    private String campaignId;
    private String referenceId;
    private JsonNode headers;
    private JsonNode attachments;
    private List<String> tags;
    private Map<String, Object> metadata;
    private long timestamp;
    private Instant scheduledAt;
    private Instant expiresAt;
    private boolean requiresDeliveryReceipt;
    private boolean requiresReadReceipt;
    private String unsubscribeLink;
    private String trackingPixelUrl;
    
    @Override
    public int compareTo(EmailMessage other) {
        // Higher priority (lower number) comes first
        int priorityComparison = Integer.compare(this.priority, other.priority);
        if (priorityComparison != 0) {
            return priorityComparison;
        }
        
        // Then by timestamp (newer first)
        return Long.compare(other.timestamp, this.timestamp);
    }
    
    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }
    
    public boolean isScheduled() {
        return scheduledAt != null && Instant.now().isBefore(scheduledAt);
    }
    
    public String getPriorityLabel() {
        switch (priority) {
            case 1: return "HIGH";
            case 2: return "MEDIUM_HIGH";
            case 3: return "NORMAL";
            case 4: return "MEDIUM_LOW";
            case 5: return "LOW";
            default: return "NORMAL";
        }
    }
    
    public boolean hasAttachments() {
        return attachments != null && attachments.isArray() && attachments.size() > 0;
    }
    
    public String getCategory() {
        if (category != null) {
            return category;
        }
        
        // Infer category based on priority and content
        if (priority <= 2) {
            return "transactional";
        } else if (campaignId != null) {
            return "marketing";
        } else if (referenceId != null) {
            return "transactional";
        } else {
            return "general";
        }
    }
}