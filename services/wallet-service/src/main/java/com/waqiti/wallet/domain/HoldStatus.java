package com.waqiti.wallet.domain;

/**
 * Enum representing the status of a wallet hold/reservation.
 */
public enum HoldStatus {
    ACTIVE,      // Hold is currently active
    CAPTURED,    // Hold has been captured (converted to actual transaction)
    RELEASED,    // Hold has been released (funds returned to available)
    EXPIRED,     // Hold has expired automatically
    CANCELLED,   // Hold was cancelled manually
    PARTIAL      // Hold was partially captured
}