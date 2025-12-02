package com.waqiti.user.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for notification settings update requests
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationSettingsRequest {
    
    @NotNull(message = "Email notifications setting is required")
    private Boolean emailNotifications;
    
    @NotNull(message = "SMS notifications setting is required")
    private Boolean smsNotifications;
    
    @NotNull(message = "Push notifications setting is required")
    private Boolean pushNotifications;
    
    @NotNull(message = "Transaction notifications setting is required")
    private Boolean transactionNotifications;
    
    @NotNull(message = "Security notifications setting is required")
    private Boolean securityNotifications;
    
    @NotNull(message = "Marketing notifications setting is required")
    private Boolean marketingNotifications;
    
    private String preferredLanguage;
    private String timezone;
}