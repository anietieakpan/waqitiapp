package com.waqiti.common.notification.model;

import lombok.AllArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.Builder;

import java.util.Map;

/**
 * SMS notification request
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class SmsNotificationRequest extends NotificationRequest {
    
    /**
     * Recipient phone number (E.164 format)
     */
    private String phoneNumber;
    
    /**
     * SMS message content
     */
    private String message;
    
    /**
     * Sender ID or phone number
     */
    private String senderId;
    
    /**
     * SMS type
     */
    @Builder.Default
    private SmsType smsType = SmsType.TRANSACTIONAL;
    
    /**
     * Whether to use unicode encoding
     */
    private boolean unicode;
    
    /**
     * Template ID for templated SMS
     */
    private String templateId;
    
    /**
     * Template variables
     */
    private Map<String, Object> templateVariables;
    
    /**
     * Whether this is a flash SMS
     */
    private boolean flash;
    
    /**
     * Validity period in minutes
     */
    @Builder.Default
    private int validityPeriodMinutes = 1440; // 24 hours
    
    /**
     * Delivery report URL
     */
    private String deliveryReportUrl;
    
    /**
     * Whether to validate phone number
     */
    @Builder.Default
    private boolean validatePhoneNumber = true;
    
    /**
     * Country code for validation
     */
    private String countryCode;
    
    /**
     * SMS route preference
     */
    private SmsRoute route;
    
    /**
     * Custom provider settings
     */
    private Map<String, String> providerSettings;
    
    public enum SmsType {
        TRANSACTIONAL,
        PROMOTIONAL,
        OTP
    }
    
    public enum SmsRoute {
        STANDARD,
        PREMIUM,
        OTP_PRIORITY
    }
}