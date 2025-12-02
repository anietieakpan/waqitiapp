package com.waqiti.payment.entity;

/**
 * Check hold type enumeration
 * Represents different types of holds that can be placed on check deposits
 * based on risk assessment and regulatory requirements
 */
public enum CheckHoldType {
    NONE,               // No hold - funds immediately available
    NEXT_DAY,          // Next business day availability
    TWO_DAY,           // Two business day hold
    FIVE_DAY,          // Five business day hold (standard regulatory hold)
    SEVEN_DAY,         // Seven business day hold
    EXTENDED,          // Extended hold (case-by-case basis)
    PARTIAL           // Partial availability (some funds held, some released)
}