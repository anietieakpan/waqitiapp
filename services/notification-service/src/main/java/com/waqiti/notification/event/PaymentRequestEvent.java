/**
 * File: src/main/java/com/waqiti/notification/event/PaymentRequestEvent.java
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
 * Event for payment requests
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class PaymentRequestEvent extends NotificationEvent {
    private UUID userId;
    private UUID requestId;
    private String status; // CREATED, APPROVED, REJECTED, CANCELED, EXPIRED
    private UUID requestorId;
    private String requestorName;
    private UUID recipientId;
    private String recipientName;
    private BigDecimal amount;
    private String currency;

    // Use setter method instead of direct field access
    public void initializeEventType() {
        this.setEventType("PAYMENT_REQUEST");
    }
}