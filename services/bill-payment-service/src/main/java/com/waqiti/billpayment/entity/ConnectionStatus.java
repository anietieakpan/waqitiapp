package com.waqiti.billpayment.entity;

/**
 * Status values for biller connections
 */
public enum ConnectionStatus {
    /**
     * Connection is active and working
     */
    ACTIVE,

    /**
     * Connection is pending initial verification
     */
    PENDING_VERIFICATION,

    /**
     * Connection requires re-authentication (credentials expired/changed)
     */
    REAUTH_REQUIRED,

    /**
     * Connection is temporarily suspended
     */
    SUSPENDED,

    /**
     * Connection has been disconnected by user
     */
    DISCONNECTED,

    /**
     * Connection failed due to errors
     */
    ERROR
}
