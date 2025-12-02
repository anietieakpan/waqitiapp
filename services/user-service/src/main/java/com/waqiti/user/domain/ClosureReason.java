package com.waqiti.user.domain;

/**
 * Enumeration of account closure reasons
 */
public enum ClosureReason {
    USER_REQUEST,
    INACTIVITY,
    COMPLIANCE_VIOLATION,
    FRAUD,
    AML_VIOLATION,
    DECEASED,
    BUSINESS_CLOSURE,
    REGULATORY_REQUIREMENT,
    SYSTEM_MIGRATION,
    DUPLICATE_ACCOUNT
}