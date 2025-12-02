package com.waqiti.common.notification.sms.dto;

/**
 * SMS delivery states enumeration
 */
public enum DeliveryState {
    PENDING,        // Message queued for sending
    SENT,          // Message sent to provider
    DELIVERED,     // Message delivered to device
    READ,          // Message read by recipient (if supported)
    FAILED,        // Delivery failed
    REJECTED,      // Message rejected by carrier
    EXPIRED,       // Message expired before delivery
    UNKNOWN        // Unknown status
}