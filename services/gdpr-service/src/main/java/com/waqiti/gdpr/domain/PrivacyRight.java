package com.waqiti.gdpr.domain;

/**
 * GDPR Data Subject Rights (Chapter III, Articles 12-23)
 */
public enum PrivacyRight {
    /**
     * Right of access by the data subject (Article 15)
     * - Confirm whether personal data is being processed
     * - Obtain copy of personal data
     * - Receive supplementary information
     */
    ACCESS,

    /**
     * Right to data portability (Article 20)
     * - Receive personal data in structured, commonly used format
     * - Transmit data to another controller
     */
    PORTABILITY,

    /**
     * Right to rectification (Article 16)
     * - Correction of inaccurate personal data
     * - Completion of incomplete personal data
     */
    RECTIFICATION,

    /**
     * Right to erasure / "Right to be forgotten" (Article 17)
     * - Deletion of personal data when no longer necessary
     * - Withdrawal of consent
     * - Objection to processing
     */
    ERASURE,

    /**
     * Right to restriction of processing (Article 18)
     * - Limit processing pending verification or legal proceedings
     */
    RESTRICTION,

    /**
     * Right to object (Article 21)
     * - Object to processing based on legitimate interests
     * - Object to direct marketing
     * - Object to profiling
     */
    OBJECTION,

    /**
     * Rights related to automated decision making and profiling (Article 22)
     * - Not subject to solely automated decisions with legal/significant effects
     * - Human intervention in automated processes
     */
    AUTOMATED_DECISION_OBJECTION,

    /**
     * Right to be informed (Articles 13-14)
     * - Transparent information about data processing
     */
    INFORMATION,

    /**
     * Right to lodge a complaint (Article 77)
     * - Complain to supervisory authority
     */
    COMPLAINT,

    /**
     * Right to an effective judicial remedy (Articles 78-79)
     * - Legal remedy against controller or processor
     */
    JUDICIAL_REMEDY
}
