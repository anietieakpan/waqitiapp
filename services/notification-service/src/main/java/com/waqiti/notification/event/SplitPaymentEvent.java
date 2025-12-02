/**
 * File: src/main/java/com/waqiti/notification/event/SplitPaymentEvent.java
 */
package com.waqiti.notification.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Event for split payments
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class SplitPaymentEvent extends NotificationEvent {
    private UUID userId;
    private UUID paymentId;
    private String status; // CREATED, PARTICIPANT_ADDED, PARTICIPANT_PAID, COMPLETED, CANCELED
    private UUID organizerId;
    private String organizerName;
    private String title;
    private BigDecimal totalAmount;
    private BigDecimal userAmount;
    private String currency;
    private UUID participantId;
    private String participantName;

    // Use setter method instead of direct field access
    public void initializeEventType() {
        this.setEventType("SPLIT_PAYMENT");
    }
}