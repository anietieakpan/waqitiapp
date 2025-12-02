/**
 * File: src/main/java/com/waqiti/notification/event/UserRegisteredEvent.java
 */
package com.waqiti.notification.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.EqualsAndHashCode;

import javax.annotation.PostConstruct;
import java.util.UUID;

/**
 * Event for user registration
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class UserRegisteredEvent extends NotificationEvent {
    private UUID userId;
    private String username;
    private String email;

    // You can add a method to set the event type after construction
    @PostConstruct
    private void init() {
        this.setEventType("USER_REGISTERED");
    }

    // For manually setting the event type when not using Spring
    public void initializeEventType() {
        this.setEventType("USER_REGISTERED");
    }
}