package com.waqiti.common.finance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for wallet balance operations with fund reservation support
 */
@Repository
public interface WalletBalanceRepository extends JpaRepository<WalletBalance, UUID> {

    /**
     * Find wallet with pessimistic write lock
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM WalletBalance w WHERE w.id = :id")
    Optional<WalletBalance> findByIdWithLock(@Param("id") UUID id);

    /**
     * CRITICAL: Atomic fund reservation operation
     *
     * Only reserves funds if available balance >= amount
     * Updates reserved_amount field atomically
     *
     * @return Number of rows updated (1 if successful, 0 if failed)
     */
    @Modifying
    @Query(value = """
        UPDATE wallet_balances SET
            reserved_amount = COALESCE(reserved_amount, 0) + :amount,
            updated_at = CURRENT_TIMESTAMP,
            version = version + 1
        WHERE id = :walletId
        AND (balance - COALESCE(reserved_amount, 0)) >= :amount
        AND status = 'ACTIVE'
        """, nativeQuery = true)
    int atomicReserveFunds(@Param("walletId") UUID walletId,
                          @Param("amount") BigDecimal amount,
                          @Param("reservationId") UUID reservationId);

    /**
     * CRITICAL: Atomic reservation commit
     *
     * Deducts balance and reduces reserved amount
     *
     * @return Number of rows updated
     */
    @Modifying
    @Query(value = """
        UPDATE wallet_balances SET
            balance = balance - :amount,
            reserved_amount = COALESCE(reserved_amount, 0) - :amount,
            updated_at = CURRENT_TIMESTAMP,
            version = version + 1
        WHERE id = :walletId
        AND COALESCE(reserved_amount, 0) >= :amount
        """, nativeQuery = true)
    int atomicCommitReservation(@Param("walletId") UUID walletId,
                               @Param("amount") BigDecimal amount,
                               @Param("reservationId") UUID reservationId);

    /**
     * CRITICAL: Atomic reservation release
     *
     * Reduces reserved amount without changing balance
     *
     * @return Number of rows updated
     */
    @Modifying
    @Query(value = """
        UPDATE wallet_balances SET
            reserved_amount = GREATEST(COALESCE(reserved_amount, 0) - :amount, 0),
            updated_at = CURRENT_TIMESTAMP,
            version = version + 1
        WHERE id = :walletId
        """, nativeQuery = true)
    int atomicReleaseReservation(@Param("walletId") UUID walletId,
                                @Param("amount") BigDecimal amount,
                                @Param("reservationId") UUID reservationId);

    /**
     * Get available balance (balance - reserved)
     */
    @Query(value = """
        SELECT (balance - COALESCE(reserved_amount, 0)) as available_balance
        FROM wallet_balances
        WHERE id = :walletId
        """, nativeQuery = true)
    BigDecimal getAvailableBalance(@Param("walletId") UUID walletId);
}
