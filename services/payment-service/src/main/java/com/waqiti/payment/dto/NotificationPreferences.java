package com.waqiti.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

/**
 * DTO for user notification preferences
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NotificationPreferences {
    
    private String userId;
    
    // Channel preferences
    @Builder.Default
    private boolean emailEnabled = true;
    
    @Builder.Default
    private boolean smsEnabled = true;
    
    @Builder.Default
    private boolean pushEnabled = true;
    
    // Specific notification types
    @Builder.Default
    private boolean paymentNotifications = true;
    
    @Builder.Default
    private boolean securityNotifications = true;
    
    @Builder.Default
    private boolean marketingNotifications = false;
    
    @Builder.Default
    private boolean p2pNotifications = true;
    
    @Builder.Default
    private boolean transactionNotifications = true;
    
    @Builder.Default
    private boolean failureNotifications = true;
    
    // Timing preferences
    private String quietHoursStart;
    private String quietHoursEnd;
    private String timezone;
    private boolean respectQuietHours;
    
    // Frequency preferences
    private String digestFrequency; // IMMEDIATE, HOURLY, DAILY, WEEKLY
    private boolean batchNotifications;
    
    // Language preferences
    private String language;
    private String locale;
    
    // Metadata
    private Instant createdAt;
    private Instant updatedAt;
    private Map<String, Object> customPreferences;
}