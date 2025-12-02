package com.waqiti.gdpr.domain;

/**
 * Conclusion of Data Privacy Impact Assessment
 */
public enum DpiaConclusion {
    /**
     * Processing may proceed without additional measures
     */
    PROCEED_WITHOUT_CONDITIONS,

    /**
     * Processing may proceed with specified conditions
     */
    PROCEED_WITH_CONDITIONS,

    /**
     * Processing requires additional safeguards before proceeding
     */
    REQUIRES_ADDITIONAL_SAFEGUARDS,

    /**
     * Processing requires supervisory authority consultation before proceeding
     */
    REQUIRES_AUTHORITY_CONSULTATION,

    /**
     * Processing should not proceed due to high unmitigated risks
     */
    DO_NOT_PROCEED,

    /**
     * Processing should be redesigned to reduce risks
     */
    REQUIRES_REDESIGN,

    /**
     * Inconclusive - needs further assessment
     */
    INCONCLUSIVE
}
