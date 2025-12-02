package com.waqiti.common.security.awareness.model;

/**
 * Phishing Result Enum
 *
 * Employee result for phishing simulation test.
 */
public enum PhishingResult {
    PENDING,        // Email sent, awaiting response
    OPENED,         // Email opened but no action taken
    CLICKED,        // Phishing link clicked (failed test)
    FAILED,         // Credentials/data submitted (critical failure)
    REPORTED        // Phishing email reported (passed test)
}