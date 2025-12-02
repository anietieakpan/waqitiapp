package com.waqiti.security.domain;

/**
 * Enumeration of AML alert severity levels
 */
public enum AmlSeverity {
    LOW,     // Informational, routine monitoring
    MEDIUM,  // Requires attention within 24-48 hours  
    HIGH,    // Requires immediate investigation
    CRITICAL // Requires immediate action and regulatory notification
}