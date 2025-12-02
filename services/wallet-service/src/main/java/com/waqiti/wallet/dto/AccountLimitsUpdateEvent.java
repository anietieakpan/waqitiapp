package com.waqiti.wallet.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;

/**
 * Account Limits Update Event DTO
 *
 * Represents an event when account limits are updated.
 * Used for Kafka event processing.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountLimitsUpdateEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * User ID whose limits are being updated
     */
    private String userId;

    /**
     * Type of limit being updated
     * (DAILY_TRANSACTION_LIMIT, MONTHLY_TRANSACTION_LIMIT, SINGLE_TRANSACTION_LIMIT,
     *  WITHDRAWAL_LIMIT, DEPOSIT_LIMIT, ACCOUNT_BALANCE_LIMIT, VELOCITY_LIMIT)
     */
    private String limitType;

    /**
     * New limit value
     */
    private Object newLimitValue;

    /**
     * Old limit value (for audit purposes)
     */
    private Object oldLimitValue;

    /**
     * Reason for the limit update
     */
    private String reason;

    /**
     * Who triggered the update (user ID or system identifier)
     */
    private String updatedBy;

    /**
     * Time window for velocity limits (e.g., "1H", "24H", "1D")
     */
    private String timeWindow;

    /**
     * Event timestamp
     */
    private Instant timestamp;

    /**
     * Event ID for tracking
     */
    private String eventId;

    /**
     * Source system that triggered the update
     */
    private String sourceSystem;

    /**
     * Whether this requires compliance review
     */
    private Boolean requiresComplianceReview;

    /**
     * Correlation ID for event tracking
     */
    private String correlationId;
}
