package com.waqiti.notification.domain;

import lombok.Data;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class SmsMessage {
    private final String messageId;
    private final String recipientNumber;
    private final String senderNumber;
    private final String messageText;
    private final MessageType type;
    private final MessagePriority priority;
    private final String templateId;
    private final Map<String, Object> templateVariables;
    private final LocalDateTime scheduledAt;
    private final LocalDateTime expiresAt;
    private final Integer maxRetries;
    private final String callbackUrl;
    private final Map<String, String> customHeaders;
    private final List<String> tags;
    private final String userId;
    private final String transactionId;
    private final boolean trackDelivery;
    private final boolean requestDeliveryReceipt;
    
    public enum MessageType {
        TRANSACTIONAL,
        PROMOTIONAL,
        OTP,
        ALERT,
        REMINDER,
        MARKETING
    }
    
    public enum MessagePriority {
        LOW,
        NORMAL,
        HIGH,
        URGENT
    }
    
    public boolean isOtp() {
        return type == MessageType.OTP;
    }
    
    public boolean isHighPriority() {
        return priority == MessagePriority.HIGH || priority == MessagePriority.URGENT;
    }
    
    public boolean isScheduled() {
        return scheduledAt != null && scheduledAt.isAfter(LocalDateTime.now());
    }
    
    public boolean isExpired() {
        return expiresAt != null && expiresAt.isBefore(LocalDateTime.now());
    }
}