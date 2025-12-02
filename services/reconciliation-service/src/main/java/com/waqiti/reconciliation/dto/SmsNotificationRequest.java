package com.waqiti.reconciliation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SmsNotificationRequest {

    private String phoneNumber;
    
    private String message;
    
    private String sender;
    
    private SmsType smsType;
    
    private NotificationPriority priority;
    
    private String templateId;
    
    private Map<String, Object> templateVariables;
    
    @Builder.Default
    private LocalDateTime scheduledFor = LocalDateTime.now();
    
    private boolean deliveryReceipt;
    
    private String callbackUrl;
    
    private int validityPeriod; // in minutes
    
    private String encoding;
    
    private String messageClass;

    public enum SmsType {
        TRANSACTIONAL,
        PROMOTIONAL,
        OTP,
        ALERT,
        REMINDER
    }

    public enum NotificationPriority {
        LOW,
        NORMAL,
        HIGH,
        URGENT
    }

    public boolean hasTemplate() {
        return templateId != null && !templateId.isEmpty();
    }

    public boolean isScheduled() {
        return scheduledFor != null && scheduledFor.isAfter(LocalDateTime.now());
    }

    public boolean hasCallback() {
        return callbackUrl != null && !callbackUrl.isEmpty();
    }

    public boolean isOtp() {
        return SmsType.OTP.equals(smsType);
    }

    public boolean isValidPhoneNumber() {
        return phoneNumber != null && 
               phoneNumber.matches("^\\+?[1-9]\\d{1,14}$");
    }

    public int getMessageLength() {
        return message != null ? message.length() : 0;
    }

    public boolean isLongMessage() {
        return getMessageLength() > 160;
    }
}