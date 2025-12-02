package com.waqiti.notification.tracker;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Tracks bounce events for notifications
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BounceTracker {
    
    private String bounceId;
    private String notificationId;
    private String recipientEmail;
    private String recipientId;
    
    // Bounce details
    private String bounceType; // hard, soft, general, complaint
    private String bounceSubType; // general, no-email, suppressed, mailbox-full, etc.
    private String bounceReason;
    private LocalDateTime bouncedAt;
    
    // Provider information
    private String provider;
    private String providerMessageId;
    private Map<String, Object> providerDetails;
    
    // Diagnostic information
    private String diagnosticCode;
    private String feedbackId;
    private String remoteMta;
    private String reportingMta;
    
    // Action taken
    private String action; // failed, delayed, delivered, expanded, relayed
    private boolean permanentFailure;
    private boolean shouldSuppress;
    
    // Timestamps
    private LocalDateTime receivedAt;
    private LocalDateTime processedAt;
    
    // Metadata
    private Map<String, Object> metadata;
}