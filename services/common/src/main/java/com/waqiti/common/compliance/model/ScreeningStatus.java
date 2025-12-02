package com.waqiti.common.compliance.model;

/**
 * Screening status enumeration
 */
public enum ScreeningStatus {
    CLEAR,                      // No matches found
    NO_MATCH,                  // No matches found (alias for CLEAR)
    POTENTIAL_MATCH,           // Potential match requiring review
    MATCH_FOUND,               // Match found
    CONFIRMED_MATCH,           // Confirmed sanctions match
    FALSE_POSITIVE,            // Cleared as false positive
    PENDING_REVIEW,            // Awaiting manual review
    ERROR,                     // Screening error
    NO_PROVIDERS_AVAILABLE,    // No screening providers available
    EMERGENCY_OVERRIDE         // Emergency override applied
}