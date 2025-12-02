/**
 * File: src/main/java/com/waqiti/notification/event/ScheduledPaymentEvent.java
 */
package com.waqiti.notification.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Event for scheduled payments
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class ScheduledPaymentEvent extends NotificationEvent {
    private UUID userId;
    private UUID paymentId;
    private String status; // CREATED, EXECUTED, FAILED, COMPLETED, CANCELED
    private UUID senderId;
    private String senderName;
    private UUID recipientId;
    private String recipientName;
    private BigDecimal amount;
    private String currency;
    private LocalDateTime executionDate;

    // Use setter method instead of direct field access
    public void initializeEventType() {
        this.setEventType("SCHEDULED_PAYMENT");
    }
}