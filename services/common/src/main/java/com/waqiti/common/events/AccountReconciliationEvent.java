package com.waqiti.common.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Account Reconciliation Event
 *
 * Event published when an account reconciliation needs to be processed.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountReconciliationEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Unique event ID
     */
    private String eventId;

    /**
     * Reconciliation ID
     */
    private String reconciliationId;

    /**
     * Account ID to reconcile
     */
    private String accountId;

    /**
     * Type of reconciliation
     */
    private String reconciliationType;

    /**
     * Date of reconciliation
     */
    private LocalDate reconciliationDate;

    /**
     * Expected balance
     */
    private BigDecimal expectedBalance;

    /**
     * External reference for reconciliation
     */
    private String externalReference;

    /**
     * Timestamp when event was created
     */
    private Instant timestamp;

    /**
     * User who initiated the reconciliation
     */
    private String initiatedBy;

    /**
     * Additional metadata
     */
    private String metadata;

    /**
     * Correlation ID for tracking
     */
    private String correlationId;
}
