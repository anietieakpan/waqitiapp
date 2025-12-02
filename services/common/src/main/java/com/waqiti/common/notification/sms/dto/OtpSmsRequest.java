package com.waqiti.common.notification.sms.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Request DTO for sending OTP SMS
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OtpSmsRequest {
    
    @NotBlank(message = "User ID is required")
    private String userId;
    
    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^\\+[1-9]\\d{1,14}$", message = "Invalid phone number format")
    private String phoneNumber;
    
    @NotBlank(message = "OTP code is required")
    @Pattern(regexp = "^\\d{4,8}$", message = "OTP code must be 4-8 digits")
    private String otpCode;
    
    @NotBlank(message = "OTP purpose is required")
    private String purpose;
    
    @Min(value = 1, message = "Validity must be at least 1 minute")
    @Max(value = 30, message = "Validity cannot exceed 30 minutes")
    @Builder.Default
    private int validityMinutes = 5;
    
    @Builder.Default
    private String applicationName = "Waqiti";
    
    @Builder.Default
    private boolean isResend = false;
    
    private String sessionId;
    private Instant expiryTime;
    
    @Builder.Default
    private String language = "en";
    
    private String countryCode;
    
    @Builder.Default
    private SmsPriority priority = SmsPriority.HIGH;
    
    @Builder.Default
    private SmsType type = SmsType.OTP;
    
    private String referenceId;
    private Map<String, Object> context;
    private Map<String, String> templateVariables;
    
    @Builder.Default
    private boolean highValue = false;
    
    /**
     * Get expiration time in minutes
     */
    public int getExpirationMinutes() {
        return validityMinutes;
    }
    
    private String originIpAddress;
    private String deviceFingerprint;
    
    @Builder.Default
    private LocalDateTime requestTime = LocalDateTime.now();
    
    /**
     * Generate OTP message content
     */
    public String generateOtpContent() {
        StringBuilder content = new StringBuilder();
        
        if (applicationName != null) {
            content.append(applicationName).append(": ");
        }
        
        content.append("Your verification code is: ").append(otpCode);
        
        if (validityMinutes > 0) {
            content.append(". Valid for ").append(validityMinutes).append(" minutes.");
        }
        
        content.append(" Do not share this code with anyone.");
        
        if (purpose != null) {
            switch (purpose.toUpperCase()) {
                case "LOGIN":
                    content.append(" This is for login verification.");
                    break;
                case "TRANSACTION":
                    content.append(" This is for transaction confirmation.");
                    break;
                case "PASSWORD_RESET":
                    content.append(" This is for password reset.");
                    break;
                case "REGISTRATION":
                    content.append(" This is for account registration.");
                    break;
            }
        }
        
        return content.toString();
    }
    
    /**
     * Check if OTP is expired
     */
    public boolean isExpired() {
        if (expiryTime == null) {
            return false;
        }
        return Instant.now().isAfter(expiryTime);
    }
    
    /**
     * Get remaining validity time in seconds
     */
    public long getRemainingValiditySeconds() {
        if (expiryTime == null) {
            return validityMinutes * 60L;
        }
        
        long remaining = expiryTime.getEpochSecond() - Instant.now().getEpochSecond();
        return Math.max(0, remaining);
    }
}