package com.waqiti.corebanking.repository;

import com.waqiti.corebanking.domain.FundReservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * PRODUCTION-GRADE Fund Reservation Repository
 * Handles all database operations for fund reservations with atomic guarantees
 */
@Repository
public interface FundReservationRepository extends JpaRepository<FundReservation, UUID> {
    
    /**
     * Find active reservation by transaction ID with pessimistic lock
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT fr FROM FundReservation fr WHERE fr.transactionId = :transactionId AND fr.status = 'ACTIVE'")
    Optional<FundReservation> findActiveByTransactionIdWithLock(@Param("transactionId") String transactionId);
    
    /**
     * Find active reservation by transaction ID (read-only)
     */
    @Query("SELECT fr FROM FundReservation fr WHERE fr.transactionId = :transactionId AND fr.status = 'ACTIVE'")
    Optional<FundReservation> findActiveByTransactionId(@Param("transactionId") String transactionId);
    
    /**
     * Find all active reservations for an account
     */
    @Query("SELECT fr FROM FundReservation fr WHERE fr.accountId = :accountId AND fr.status = 'ACTIVE'")
    List<FundReservation> findActiveByAccountId(@Param("accountId") String accountId);
    
    /**
     * Calculate total reserved amount for an account
     */
    @Query("SELECT COALESCE(SUM(fr.reservedAmount), 0) FROM FundReservation fr " +
           "WHERE fr.accountId = :accountId AND fr.status = 'ACTIVE'")
    BigDecimal getTotalReservedAmount(@Param("accountId") String accountId);
    
    /**
     * Get reserved amount for a specific transaction
     */
    @Query("SELECT COALESCE(fr.reservedAmount, 0) FROM FundReservation fr " +
           "WHERE fr.transactionId = :transactionId AND fr.status = 'ACTIVE'")
    BigDecimal getReservedAmountForTransaction(@Param("transactionId") String transactionId);
    
    /**
     * Find expired reservations
     */
    @Query("SELECT fr FROM FundReservation fr WHERE fr.status = 'ACTIVE' AND fr.expiresAt < :currentTime")
    List<FundReservation> findExpiredReservations(@Param("currentTime") LocalDateTime currentTime);
    
    /**
     * Bulk expire reservations
     */
    @Modifying
    @Query("UPDATE FundReservation fr SET fr.status = 'EXPIRED', " +
           "fr.releasedAt = :currentTime, fr.releasedBy = 'SYSTEM', " +
           "fr.releaseReason = 'AUTOMATIC_EXPIRATION', fr.updatedAt = :currentTime " +
           "WHERE fr.status = 'ACTIVE' AND fr.expiresAt < :currentTime")
    int expireReservations(@Param("currentTime") LocalDateTime currentTime);
    
    /**
     * CRITICAL SECURITY: Atomic reserve funds operation
     * Creates reservation only if account has sufficient available balance
     */
    @Modifying
    @Query(value = """
        INSERT INTO fund_reservations (id, account_id, transaction_id, reserved_amount, currency, 
                                     status, reservation_reason, expires_at, reserved_by, 
                                     reserved_for_service, created_at, updated_at, version)
        SELECT gen_random_uuid(), :accountId, :transactionId, :amount, :currency,
               'ACTIVE', :reason, :expiresAt, :reservedBy, :service, :createdAt, :createdAt, 0
        WHERE (SELECT balance - COALESCE((SELECT SUM(reserved_amount) 
                                        FROM fund_reservations 
                                        WHERE account_id = :accountId AND status = 'ACTIVE'), 0)
               FROM accounts WHERE account_number = :accountId) >= :amount
        """, nativeQuery = true)
    int atomicReserveFunds(@Param("accountId") String accountId,
                          @Param("transactionId") String transactionId,
                          @Param("amount") BigDecimal amount,
                          @Param("currency") String currency,
                          @Param("reason") String reason,
                          @Param("expiresAt") LocalDateTime expiresAt,
                          @Param("reservedBy") String reservedBy,
                          @Param("service") String service,
                          @Param("createdAt") LocalDateTime createdAt);
    
    /**
     * Release reservation by transaction ID
     */
    @Modifying
    @Query("UPDATE FundReservation fr SET fr.status = 'RELEASED', " +
           "fr.releasedAt = :releasedAt, fr.releasedBy = :releasedBy, " +
           "fr.releaseReason = :reason, fr.updatedAt = :releasedAt " +
           "WHERE fr.transactionId = :transactionId AND fr.status = 'ACTIVE'")
    int releaseByTransactionId(@Param("transactionId") String transactionId,
                              @Param("releasedAt") LocalDateTime releasedAt,
                              @Param("releasedBy") String releasedBy,
                              @Param("reason") String reason);
    
    /**
     * Use reservation (mark as used for completed transaction)
     */
    @Modifying
    @Query("UPDATE FundReservation fr SET fr.status = 'USED', " +
           "fr.releasedAt = :usedAt, fr.releasedBy = :usedBy, " +
           "fr.releaseReason = 'TRANSACTION_COMPLETED', fr.updatedAt = :usedAt " +
           "WHERE fr.transactionId = :transactionId AND fr.status = 'ACTIVE'")
    int useReservation(@Param("transactionId") String transactionId,
                      @Param("usedAt") LocalDateTime usedAt,
                      @Param("usedBy") String usedBy);
    
    /**
     * Find reservations expiring soon (for monitoring/alerts)
     */
    @Query("SELECT fr FROM FundReservation fr " +
           "WHERE fr.status = 'ACTIVE' AND fr.expiresAt BETWEEN :currentTime AND :alertTime")
    List<FundReservation> findReservationsExpiringSoon(@Param("currentTime") LocalDateTime currentTime,
                                                      @Param("alertTime") LocalDateTime alertTime);
    
    /**
     * Get reservation statistics for monitoring
     */
    @Query("SELECT fr.status as status, COUNT(fr) as count, COALESCE(SUM(fr.reservedAmount), 0) as totalAmount " +
           "FROM FundReservation fr GROUP BY fr.status")
    List<ReservationStatistics> getReservationStatistics();
    
    /**
     * Custom projection for statistics
     */
    interface ReservationStatistics {
        String getStatus();
        Long getCount();
        BigDecimal getTotalAmount();
    }
}