package com.waqiti.notification.event;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID; /**
 * Base class for all notification events
 */
@Data
public abstract class NotificationEvent {
    private String eventId;
    private String eventType;
    private LocalDateTime timestamp;
    private UUID userId;

    public NotificationEvent() {
        this.eventId = UUID.randomUUID().toString();
        this.timestamp = LocalDateTime.now();
    }
}
