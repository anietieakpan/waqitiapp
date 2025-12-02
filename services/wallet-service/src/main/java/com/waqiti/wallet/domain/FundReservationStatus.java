package com.waqiti.wallet.domain;

/**
 * Fund Reservation Status Enum
 *
 * ACTIVE: Reservation is active and funds are reserved
 * CONFIRMED: Reservation confirmed, funds permanently debited
 * RELEASED: Reservation cancelled, funds returned to available balance
 * EXPIRED: Reservation expired and auto-released by cleanup job
 *
 * @author Waqiti Engineering Team
 * @version 2.0
 * @since 2025-11-01
 */
public enum FundReservationStatus {
    /**
     * Reservation is active - funds are reserved and not available
     */
    ACTIVE,

    /**
     * Reservation confirmed - transaction completed successfully,
     * funds have been permanently debited from wallet balance
     */
    CONFIRMED,

    /**
     * Reservation released - transaction failed or cancelled,
     * funds have been returned to available balance
     */
    RELEASED,

    /**
     * Reservation expired - TTL exceeded, funds auto-released by cleanup job
     */
    EXPIRED
}
