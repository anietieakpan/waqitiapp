package com.waqiti.wallet.repository;

import com.waqiti.wallet.domain.FundReservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FundReservationRepository extends JpaRepository<FundReservation, UUID> {
    
    /**
     * Find active reservations for a wallet
     */
    @Query("SELECT fr FROM FundReservation fr WHERE fr.walletId = :walletId AND fr.status = 'ACTIVE'")
    List<FundReservation> findActiveReservationsByWalletId(@Param("walletId") UUID walletId);
    
    /**
     * Find reservation by transaction ID
     */
    Optional<FundReservation> findByTransactionId(UUID transactionId);
    
    /**
     * Find expired active reservations
     */
    @Query("SELECT fr FROM FundReservation fr WHERE fr.status = 'ACTIVE' AND fr.expiresAt < :now")
    List<FundReservation> findExpiredActiveReservations(@Param("now") LocalDateTime now);
    
    /**
     * Find reservations by idempotency key
     */
    Optional<FundReservation> findByIdempotencyKey(String idempotencyKey);
    
    /**
     * Mark expired reservations
     */
    @Modifying
    @Query("UPDATE FundReservation fr SET fr.status = 'EXPIRED', fr.releasedAt = :now, fr.reason = 'Auto-expired' " +
           "WHERE fr.status = 'ACTIVE' AND fr.expiresAt < :now")
    int markExpiredReservations(@Param("now") LocalDateTime now);
    
    /**
     * Get total reserved amount for a wallet
     */
    @Query("SELECT COALESCE(SUM(fr.amount), 0) FROM FundReservation fr " +
           "WHERE fr.walletId = :walletId AND fr.status = 'ACTIVE'")
    java.math.BigDecimal getTotalReservedAmount(@Param("walletId") UUID walletId);
    
    /**
     * Find reservations expiring soon (for notifications)
     */
    @Query("SELECT fr FROM FundReservation fr WHERE fr.status = 'ACTIVE' " +
           "AND fr.expiresAt BETWEEN :start AND :end")
    List<FundReservation> findReservationsExpiringSoon(
        @Param("start") LocalDateTime start, 
        @Param("end") LocalDateTime end
    );
    
    /**
     * Delete old completed/expired reservations (for cleanup)
     */
    @Modifying
    @Query("DELETE FROM FundReservation fr WHERE fr.status IN ('CONFIRMED', 'RELEASED', 'EXPIRED') " +
           "AND (fr.confirmedAt < :cutoff OR fr.releasedAt < :cutoff)")
    int deleteOldReservations(@Param("cutoff") LocalDateTime cutoff);
}