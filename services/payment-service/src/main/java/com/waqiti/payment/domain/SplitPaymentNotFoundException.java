package com.waqiti.payment.domain;

import java.util.UUID; /**
 * Thrown when a split payment is not found
 */
public class SplitPaymentNotFoundException extends RuntimeException {
    public SplitPaymentNotFoundException(UUID id) {
        super("Split payment not found with ID: " + id);
    }
}
