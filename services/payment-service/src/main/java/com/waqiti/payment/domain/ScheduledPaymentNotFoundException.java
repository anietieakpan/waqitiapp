package com.waqiti.payment.domain;

import java.util.UUID; /**
 * Thrown when a scheduled payment is not found
 */
public class ScheduledPaymentNotFoundException extends RuntimeException {
    public ScheduledPaymentNotFoundException(UUID id) {
        super("Scheduled payment not found with ID: " + id);
    }
}
