package com.waqiti.notification.tracker;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Represents an email event (open, click, bounce, etc.)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailEvent {
    
    private String eventId;
    private String emailId;
    private String messageId;
    private String recipientEmail;
    private String userId;
    
    // Event details
    private String eventType; // sent, delivered, opened, clicked, bounced, complained, unsubscribed
    private LocalDateTime eventTimestamp;
    private String eventSource; // webhook, api, manual
    
    // Provider information
    private String provider;
    private String providerEventId;
    private Map<String, Object> providerData;
    
    // Context information
    private String ipAddress;
    private String userAgent;
    private String deviceType;
    private String operatingSystem;
    private String browser;
    private String location;
    
    // Click-specific data
    private String clickedUrl;
    private String linkId;
    private String linkCategory;
    
    // Bounce-specific data
    private String bounceType;
    private String bounceReason;
    private boolean permanentFailure;
    
    // Complaint-specific data
    private String complaintType;
    private String complaintFeedback;
    
    // Processing
    private LocalDateTime receivedAt;
    private LocalDateTime processedAt;
    private boolean processed;
    
    // Metadata
    private Map<String, Object> metadata;
}