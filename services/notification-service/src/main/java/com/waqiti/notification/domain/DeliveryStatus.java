/**
 * File: src/main/java/com/waqiti/notification/domain/DeliveryStatus.java
 */

package com.waqiti.notification.domain;

/**
 * Represents the delivery status of a notification
 */
public enum DeliveryStatus {
    PENDING,    // Notification is waiting to be delivered
    SENT,       // Notification has been sent
    DELIVERED,  // Notification has been delivered
    FAILED,      // Notification delivery failed
    EXPIRED     // Notification has expired
}
