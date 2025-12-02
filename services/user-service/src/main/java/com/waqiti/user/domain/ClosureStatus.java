package com.waqiti.user.domain;

/**
 * Enumeration of account closure statuses
 */
public enum ClosureStatus {
    INITIATED,
    PROCESSING,
    PARTIALLY_COMPLETED,
    COMPLETED,
    IMMEDIATE,
    FAILED,
    CANCELLED
}