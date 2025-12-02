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
 * Reconciliation Completed Event
 *
 * Event published when a reconciliation process has completed.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReconciliationCompletedEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Event ID
     */
    private String eventId;

    /**
     * Reconciliation ID
     */
    private String reconciliationId;

    /**
     * Account ID
     */
    private String accountId;

    /**
     * Reconciliation date
     */
    private LocalDate reconciliationDate;

    /**
     * Final status
     */
    private String status;

    /**
     * Expected balance
     */
    private BigDecimal expectedBalance;

    /**
     * Actual balance
     */
    private BigDecimal actualBalance;

    /**
     * Discrepancy amount
     */
    private BigDecimal discrepancyAmount;

    /**
     * Number of discrepancies found
     */
    private Integer discrepancyCount;

    /**
     * Transactions matched
     */
    private Integer transactionsMatched;

    /**
     * Transactions unmatched
     */
    private Integer transactionsUnmatched;

    /**
     * Whether manual review is required
     */
    private Boolean manualReviewRequired;

    /**
     * Timestamp
     */
    private Instant timestamp;

    /**
     * Reconciled by
     */
    private String reconciledBy;

    /**
     * Correlation ID
     */
    private String correlationId;
}
