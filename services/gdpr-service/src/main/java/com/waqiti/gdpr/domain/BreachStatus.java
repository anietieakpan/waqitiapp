package com.waqiti.gdpr.domain;

/**
 * Status of data breach incident handling
 */
public enum BreachStatus {
    /**
     * Breach has been reported and is under initial assessment
     */
    REPORTED,

    /**
     * Breach is being investigated to determine scope and impact
     */
    INVESTIGATING,

    /**
     * Breach has been contained and no longer spreading
     */
    CONTAINED,

    /**
     * Systems have been recovered and normal operations resumed
     */
    RECOVERED,

    /**
     * Breach incident has been fully resolved
     */
    RESOLVED,

    /**
     * Breach was a false positive or not confirmed
     */
    FALSE_POSITIVE,

    /**
     * Breach investigation closed with findings documented
     */
    CLOSED
}
