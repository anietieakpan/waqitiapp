package com.waqiti.common.finance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Repository for fund reservations
 */
@Repository
public interface FundReservationRepository extends JpaRepository<FundReservationService.FundReservation, UUID> {

    /**
     * Find active reservations for a wallet
     */
    @Query("SELECT r FROM FundReservation r WHERE r.walletId = :walletId AND r.status = 'ACTIVE'")
    List<FundReservationService.FundReservation> findActiveReservations(@Param("walletId") UUID walletId);

    /**
     * Find expired reservations for auto-release
     */
    @Query("SELECT r FROM FundReservation r WHERE r.status = 'ACTIVE' AND r.expiresAt < :now")
    List<FundReservationService.FundReservation> findExpiredReservations(@Param("now") Instant now);

    /**
     * Find reservations by transaction
     */
    @Query("SELECT r FROM FundReservation r WHERE r.transactionId = :transactionId")
    List<FundReservationService.FundReservation> findByTransactionId(@Param("transactionId") UUID transactionId);
}
