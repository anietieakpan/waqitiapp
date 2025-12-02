package com.waqiti.common.notification.sms.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Result of SMS sending operation
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SmsResult {
    
    private String messageId;
    private boolean success;
    private String status;
    private String errorCode;
    private String errorMessage;
    private Instant sentAt;
    private Instant deliveredAt;
    private String phoneNumber;
    private String providerId;
    private String providerMessageId;
    private double cost;
    private String currency;
    private int segmentCount;
    private String deliveryStatus;
    private Map<String, Object> providerResponse;
    private List<String> warnings;
    private SmsFailureReason failureReason;
    
    /**
     * Create successful SMS result
     */
    public static SmsResult success(String messageId, String phoneNumber, String providerId) {
        return SmsResult.builder()
            .messageId(messageId)
            .success(true)
            .status("SENT")
            .phoneNumber(phoneNumber)
            .providerId(providerId)
            .sentAt(Instant.now())
            .build();
    }
    
    /**
     * Create failed SMS result
     */
    public static SmsResult failure(String messageId, String phoneNumber, String errorCode, String errorMessage) {
        return SmsResult.builder()
            .messageId(messageId)
            .success(false)
            .status("FAILED")
            .phoneNumber(phoneNumber)
            .errorCode(errorCode)
            .errorMessage(errorMessage)
            .sentAt(Instant.now())
            .build();
    }
    
    /**
     * Create rate limited result
     */
    public static SmsResult rateLimited(String messageId, String phoneNumber) {
        return SmsResult.builder()
            .messageId(messageId)
            .success(false)
            .status("RATE_LIMITED")
            .phoneNumber(phoneNumber)
            .errorCode("RATE_LIMIT_EXCEEDED")
            .errorMessage("SMS rate limit exceeded")
            .failureReason(SmsFailureReason.RATE_LIMITED)
            .build();
    }
    
    /**
     * Create compliance violation result
     */
    public static SmsResult complianceViolation(String messageId, String phoneNumber, String violations) {
        return SmsResult.builder()
            .messageId(messageId)
            .success(false)
            .status("COMPLIANCE_VIOLATION")
            .phoneNumber(phoneNumber)
            .errorCode("COMPLIANCE_ERROR")
            .errorMessage("SMS compliance violation: " + violations)
            .failureReason(SmsFailureReason.COMPLIANCE_VIOLATION)
            .build();
    }
    
    /**
     * Create a blocked result
     */
    public static SmsResult blocked(String reason) {
        return SmsResult.builder()
            .success(false)
            .status("BLOCKED")
            .errorMessage(reason)
            .errorCode("BLOCKED")
            .sentAt(Instant.now())
            .build();
    }
    
    /**
     * Create an error result
     */
    public static SmsResult error(String errorMessage) {
        return SmsResult.builder()
            .success(false)
            .status("ERROR")
            .errorMessage(errorMessage)
            .errorCode("ERROR")
            .sentAt(Instant.now())
            .build();
    }
    
    /**
     * Create rate limited result with recipient and reason
     */
    public static SmsResult rateLimitedWithReason(String recipient, String reason) {
        return SmsResult.builder()
            .phoneNumber(recipient)
            .success(false)
            .status("RATE_LIMITED")
            .errorMessage("Rate limit exceeded: " + reason)
            .errorCode("RATE_LIMITED")
            .sentAt(Instant.now())
            .build();
    }
    
    /**
     * Check if SMS was delivered
     */
    public boolean isDelivered() {
        return "DELIVERED".equalsIgnoreCase(deliveryStatus);
    }
    
    /**
     * Check if SMS is still pending
     */
    public boolean isPending() {
        return "SENT".equalsIgnoreCase(status) && deliveryStatus == null;
    }
    
    /**
     * Get human-readable status
     */
    public String getStatusDescription() {
        if (success) {
            return isDelivered() ? "Message delivered successfully" : "Message sent successfully";
        } else {
            return errorMessage != null ? errorMessage : "Message failed to send";
        }
    }
}