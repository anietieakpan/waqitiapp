package com.waqiti.lending.domain.enums;

/**
 * Interest Rate Type
 */
public enum InterestType {
    FIXED,              // Fixed interest rate for entire term
    VARIABLE,           // Variable/adjustable rate
    FLOATING,           // Floating rate tied to index
    HYBRID,             // Fixed initial period, then variable
    ZERO,               // Zero interest (promotional)
    SIMPLE,             // Simple interest calculation
    COMPOUND            // Compound interest calculation
}
