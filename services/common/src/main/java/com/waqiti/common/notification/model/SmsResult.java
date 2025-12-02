package com.waqiti.common.notification.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * CRITICAL PRODUCTION FIX - SmsResult
 * Result object for SMS notification operations
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SmsResult {
    
    private String messageId;
    private String recipient;
    private NotificationStatus.Status status;
    private String message;
    private LocalDateTime sentAt;
    private LocalDateTime deliveredAt;
    private String providerMessageId;
    private String providerName;
    private String errorMessage;
    private String errorCode;
    private Double cost;
    private String currency;
    private Integer segments;
    private String direction;
    private DeliveryReport deliveryReport;
    
    /**
     * Check if the SMS was sent successfully
     */
    public boolean wasSuccessful() {
        return status == NotificationStatus.Status.SENT || status == NotificationStatus.Status.DELIVERED;
    }
    
    /**
     * Check if the SMS failed to send
     */
    public boolean hasFailed() {
        return status == NotificationStatus.Status.FAILED || status == NotificationStatus.Status.REJECTED;
    }
    
    /**
     * Check if the SMS is still being processed
     */
    public boolean isPending() {
        return status == NotificationStatus.Status.PENDING || status == NotificationStatus.Status.QUEUED;
    }
    
    /**
     * Get a human-readable status description
     */
    public String getStatusDescription() {
        if (status == null) return "Unknown";
        
        return switch (status) {
            case SENT -> "Message sent successfully";
            case DELIVERED -> "Message delivered to recipient";
            case FAILED -> "Message failed to send" + (errorMessage != null ? ": " + errorMessage : "");
            case REJECTED -> "Message rejected" + (errorMessage != null ? ": " + errorMessage : "");
            case PENDING -> "Message is being processed";
            case QUEUED -> "Message is queued for sending";
            default -> status.toString();
        };
    }
    
    /**
     * Create a successful result
     */
    public static SmsResult success(String messageId, String recipient, String providerMessageId, String providerName) {
        return SmsResult.builder()
            .messageId(messageId)
            .recipient(recipient)
            .status(NotificationStatus.Status.SENT)
            .providerMessageId(providerMessageId)
            .providerName(providerName)
            .sentAt(LocalDateTime.now())
            .build();
    }
    
    /**
     * Create a failed result
     */
    public static SmsResult failure(String messageId, String recipient, String errorMessage, String errorCode) {
        return SmsResult.builder()
            .messageId(messageId)
            .recipient(recipient)
            .status(NotificationStatus.Status.FAILED)
            .errorMessage(errorMessage)
            .errorCode(errorCode)
            .sentAt(LocalDateTime.now())
            .build();
    }
    
    /**
     * Create a pending result
     */
    public static SmsResult pending(String messageId, String recipient) {
        return SmsResult.builder()
            .messageId(messageId)
            .recipient(recipient)
            .status(NotificationStatus.Status.PENDING)
            .sentAt(LocalDateTime.now())
            .build();
    }
    
    /**
     * Update the result with delivery information
     */
    public SmsResult withDelivery(NotificationStatus.Status deliveryStatus, LocalDateTime deliveredAt) {
        this.status = deliveryStatus;
        this.deliveredAt = deliveredAt;
        return this;
    }
    
    /**
     * Update the result with cost information
     */
    public SmsResult withCost(Double cost, String currency) {
        this.cost = cost;
        this.currency = currency;
        return this;
    }
    
    /**
     * Update the result with segment information
     */
    public SmsResult withSegments(Integer segments) {
        this.segments = segments;
        return this;
    }
}