/**
 * File: src/main/java/com/waqiti/notification/event/SecurityEvent.java
 */
package com.waqiti.notification.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Event for security related notifications
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class SecurityEvent extends NotificationEvent {
    private UUID userId;
    private String securityEventType; // LOGIN, PASSWORD_CHANGED, DEVICE_ADDED, etc.
    private String ipAddress;
    private String deviceInfo;
    private String location;
    private LocalDateTime eventTime;
    private boolean suspicious;

    // Use setter method instead of direct field access
    public void initializeEventType() {
        this.setEventType("SECURITY");
    }
}