package com.waqiti.tokenization.domain;

/**
 * Token Status Enumeration
 *
 * Defines the lifecycle state of a token
 *
 * @author Waqiti Platform Engineering
 */
public enum TokenStatus {
    /**
     * Token is active and can be used
     */
    ACTIVE,

    /**
     * Token has expired naturally (based on expiresAt timestamp)
     */
    EXPIRED,

    /**
     * Token has been manually revoked (security breach, user request, etc.)
     */
    REVOKED,

    /**
     * Token has been suspended temporarily (fraud alert, investigation, etc.)
     */
    SUSPENDED
}
