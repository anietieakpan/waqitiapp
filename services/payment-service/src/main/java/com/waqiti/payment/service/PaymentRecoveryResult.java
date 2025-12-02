package com.waqiti.payment.service;

import lombok.Builder;
import lombok.Data;

/**
 * Result of payment recovery attempt
 */
@Data
@Builder
public class PaymentRecoveryResult {

    /**
     * Whether payment was successfully recovered
     */
    private boolean recovered;

    /**
     * Whether recovery can be retried
     */
    private boolean retriable;

    /**
     * Reason for failure (if not recovered)
     */
    private String failureReason;

    /**
     * Action taken during recovery
     */
    private String recoveryAction;

    /**
     * Additional recovery details
     */
    private String details;
}
