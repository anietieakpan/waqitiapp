package com.waqiti.payment.dto.ledger;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * DTO for group payment ledger recording response
 * 
 * This response provides confirmation and details about the ledger entries
 * created for a group payment event, including:
 * - Entry IDs for audit tracking
 * - Balance confirmations
 * - Processing status
 * - Error information if applicable
 * 
 * CRITICAL IMPORTANCE:
 * - Confirms successful ledger recording
 * - Provides entry IDs for audit trails
 * - Reports any processing errors
 * - Validates balance consistency
 * 
 * @author Waqiti Platform Team
 * @version 1.0.0
 * @since 2025-09-27
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RecordGroupPaymentResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Unique identifier for the group payment that was recorded
     */
    private String groupPaymentId;

    /**
     * Primary ledger entry ID for the group payment
     */
    private String ledgerEntryId;

    /**
     * List of individual ledger entry IDs created for each participant
     */
    private List<ParticipantLedgerEntry> participantEntries;

    /**
     * Processing status of the ledger recording
     * Values: SUCCESS, PARTIAL_SUCCESS, FAILED
     */
    private String status;

    /**
     * Human-readable message about the processing result
     */
    private String message;

    /**
     * Total amount that was recorded in the ledger
     */
    private BigDecimal recordedAmount;

    /**
     * Currency of the recorded amount
     */
    private String currency;

    /**
     * Timestamp when the ledger entry was created
     */
    private Instant recordedAt;

    /**
     * Current balance summary after the ledger recording
     */
    private BalanceSummary balanceSummary;

    /**
     * Any warnings or non-critical issues encountered during processing
     */
    private List<String> warnings;

    /**
     * Detailed error information if processing failed
     */
    private ErrorDetails errorDetails;

    /**
     * Correlation ID for request tracing
     */
    private String correlationId;

    /**
     * Additional metadata from the ledger processing
     */
    private Map<String, Object> metadata;

    /**
     * Version of the ledger entry format
     */
    private String version;

    /**
     * Nested class for participant-specific ledger entries
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ParticipantLedgerEntry implements Serializable {
        private static final long serialVersionUID = 1L;

        /**
         * User ID of the participant
         */
        private String userId;

        /**
         * Ledger entry ID for this participant
         */
        private String ledgerEntryId;

        /**
         * Amount recorded for this participant
         */
        private BigDecimal amount;

        /**
         * Type of ledger entry (DEBIT, CREDIT, RESERVE, etc.)
         */
        private String entryType;

        /**
         * Processing status for this participant's entry
         */
        private String status;

        /**
         * Any error specific to this participant's entry
         */
        private String errorMessage;
    }

    /**
     * Nested class for balance information
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BalanceSummary implements Serializable {
        private static final long serialVersionUID = 1L;

        /**
         * Account ID for the balance
         */
        private String accountId;

        /**
         * Balance before the transaction
         */
        private BigDecimal previousBalance;

        /**
         * Balance after the transaction
         */
        private BigDecimal currentBalance;

        /**
         * Change in balance due to this transaction
         */
        private BigDecimal balanceChange;

        /**
         * Available balance (excluding reserved amounts)
         */
        private BigDecimal availableBalance;

        /**
         * Reserved balance amount
         */
        private BigDecimal reservedBalance;
    }

    /**
     * Nested class for error details
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ErrorDetails implements Serializable {
        private static final long serialVersionUID = 1L;

        /**
         * Error code for programmatic handling
         */
        private String errorCode;

        /**
         * Detailed error message
         */
        private String errorMessage;

        /**
         * Error category (VALIDATION, PROCESSING, SYSTEM, etc.)
         */
        private String errorCategory;

        /**
         * Whether the error is retryable
         */
        private boolean retryable;

        /**
         * Additional error context
         */
        private Map<String, Object> errorContext;

        /**
         * Recommended action to resolve the error
         */
        private String recommendedAction;
    }
}