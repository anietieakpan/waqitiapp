package com.waqiti.common.events.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;

/**
 * Account Lock Event
 *
 * Event published when an account needs to be locked for security reasons.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class AccountLockEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Unique event ID
     */
    private String eventId;

    /**
     * Account ID to be locked
     */
    private String accountId;

    /**
     * User ID associated with the account
     */
    private String userId;

    /**
     * Type of lock (TEMPORARY, PERMANENT, CONDITIONAL, etc.)
     */
    private String lockType;

    /**
     * Reason for the lock
     */
    private String lockReason;

    /**
     * Detailed description of why the lock is being applied
     */
    private String description;

    /**
     * Who or what triggered the lock (user ID, system, fraud-service, etc.)
     */
    private String triggeredBy;

    /**
     * Source system that initiated the lock
     */
    private String sourceSystem;

    /**
     * IP address associated with the lock trigger
     */
    private String ipAddress;

    /**
     * Device ID if applicable
     */
    private String deviceId;

    /**
     * Number of failed attempts (for FAILED_LOGIN_ATTEMPTS reason)
     */
    private Integer failedAttempts;

    /**
     * Duration in minutes for temporary locks
     */
    private Integer unlockAfterMinutes;

    /**
     * Whether to lock related accounts (family accounts, linked accounts, etc.)
     */
    @Builder.Default
    private boolean lockRelatedAccounts = false;

    /**
     * Type of relationship for related account locking
     */
    private String relationshipType;

    /**
     * Parent lock ID if this is a related account lock
     */
    private String parentLockId;

    /**
     * Verification token for user-requested locks
     */
    private String verificationToken;

    /**
     * Event timestamp
     */
    private Instant timestamp;

    /**
     * Correlation ID for tracking related events
     */
    private String correlationId;

    /**
     * Priority level (HIGH, MEDIUM, LOW)
     */
    private String priority;

    /**
     * Whether this requires immediate processing
     */
    @Builder.Default
    private boolean requiresImmediateAction = false;

    /**
     * Additional metadata as JSON string
     */
    private String metadata;
}
