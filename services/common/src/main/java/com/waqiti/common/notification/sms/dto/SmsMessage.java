package com.waqiti.common.notification.sms.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * SMS message DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SmsMessage {
    
    private String messageId;
    private String phoneNumber;
    private String content;
    private String senderId;
    private String userId; // User ID for tracking
    private SmsType messageType;
    private SmsPriority priority;
    private Instant scheduledTime;
    private Instant sentTime;
    private String status;
    private Map<String, Object> metadata;
    private String templateId;
    private Map<String, String> templateVariables;
    private String countryCode;
    private String carrier;
    private boolean requiresDeliveryReceipt;
    private int maxRetries;
    private String correlationId;
    private String message;
    private String trackingId;
    private String type;
    private int retryAttempts;
    private int timeoutMs;
    private String transactionId; // Transaction ID for transaction-related SMS
    private int expirationMinutes; // Expiration time in minutes for OTP/verification codes
    
    /**
     * Create OTP SMS message
     */
    public static SmsMessage createOtp(String phoneNumber, String otpCode) {
        return SmsMessage.builder()
            .phoneNumber(phoneNumber)
            .content("Your verification code is: " + otpCode + ". Do not share this code.")
            .messageType(SmsType.OTP)
            .priority(SmsPriority.HIGH)
            .requiresDeliveryReceipt(true)
            .maxRetries(3)
            .build();
    }
    
    /**
     * Create security alert SMS
     */
    public static SmsMessage createSecurityAlert(String phoneNumber, String alertMessage) {
        return SmsMessage.builder()
            .phoneNumber(phoneNumber)
            .content("SECURITY ALERT: " + alertMessage)
            .messageType(SmsType.SECURITY_ALERT)
            .priority(SmsPriority.URGENT)
            .requiresDeliveryReceipt(true)
            .build();
    }
    
    /**
     * Create fraud alert SMS
     */
    public static SmsMessage createFraudAlert(String phoneNumber, String fraudDetails) {
        return SmsMessage.builder()
            .phoneNumber(phoneNumber)
            .content("FRAUD ALERT: " + fraudDetails + " Contact us immediately if this wasn't you.")
            .messageType(SmsType.FRAUD_ALERT)
            .priority(SmsPriority.URGENT)
            .requiresDeliveryReceipt(true)
            .build();
    }
    
    /**
     * Create transaction verification SMS
     */
    public static SmsMessage createTransactionVerification(String phoneNumber, String transactionDetails) {
        return SmsMessage.builder()
            .phoneNumber(phoneNumber)
            .content("Transaction verification: " + transactionDetails + " Reply YES to confirm.")
            .messageType(SmsType.TRANSACTION_VERIFICATION)
            .priority(SmsPriority.HIGH)
            .requiresDeliveryReceipt(true)
            .build();
    }
}