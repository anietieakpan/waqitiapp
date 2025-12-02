package com.waqiti.compliance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * SAR Filing DTO
 *
 * Data Transfer Object for Suspicious Activity Report (SAR) filing responses
 * from FinCEN BSA E-Filing System.
 *
 * Used for:
 * - FinCEN SAR submission responses
 * - SAR status tracking
 * - Compliance reporting
 *
 * Compliance: FinCEN SAR Requirements (31 CFR 1020.320)
 *
 * @author Waqiti Compliance Team
 * @version 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SARFiling {

    /**
     * Filing status
     * Values: PENDING, SUBMITTED, ACKNOWLEDGED, FAILED, PENDING_RETRY
     */
    private String status;

    /**
     * FinCEN SAR reference number (BSA ID)
     * Unique identifier assigned by FinCEN upon successful submission
     */
    private String referenceNumber;

    /**
     * Response message from FinCEN
     */
    private String message;

    /**
     * Timestamp when SAR was filed
     */
    private LocalDateTime filedAt;

    /**
     * Timestamp when SAR was acknowledged by FinCEN
     */
    private LocalDateTime acknowledgedAt;

    /**
     * Whether SAR was acknowledged by FinCEN
     */
    @Builder.Default
    private boolean acknowledged = false;

    /**
     * Error code if filing failed
     */
    private String error;

    /**
     * Error details if filing failed
     */
    private String errorDetails;

    /**
     * Whether this is an emergency SAR
     */
    @Builder.Default
    private boolean emergency = false;

    /**
     * SAR type (e.g., "MONEY_LAUNDERING", "TERRORISM_FINANCING")
     */
    private String sarType;

    /**
     * Amount involved in suspicious activity (if applicable)
     */
    private String amount;

    /**
     * Currency code (e.g., "USD")
     */
    private String currency;

    /**
     * Number of transactions involved
     */
    private Integer transactionCount;

    /**
     * FinCEN filing ID (internal tracking)
     */
    private String filingId;

    /**
     * Check if SAR filing was successful
     *
     * @return true if acknowledged
     */
    public boolean isSuccess() {
        return acknowledged && "ACKNOWLEDGED".equals(status);
    }

    /**
     * Check if SAR filing failed
     *
     * @return true if failed
     */
    public boolean isFailed() {
        return "FAILED".equals(status) || error != null;
    }

    /**
     * Check if SAR filing is pending
     *
     * @return true if pending
     */
    public boolean isPending() {
        return "PENDING".equals(status) || "PENDING_RETRY".equals(status);
    }
}
