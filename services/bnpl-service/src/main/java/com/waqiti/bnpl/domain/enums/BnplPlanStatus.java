package com.waqiti.bnpl.domain.enums;

/**
 * Status of a BNPL plan
 */
public enum BnplPlanStatus {
    PENDING_APPROVAL,   // Initial state, awaiting credit check
    APPROVED,          // Credit approved, awaiting activation
    ACTIVE,            // Plan is active and payments are being collected
    COMPLETED,         // All payments have been made
    CANCELLED,         // Plan was cancelled
    DEFAULTED,         // Customer has defaulted on payments
    SUSPENDED          // Plan is temporarily suspended
}