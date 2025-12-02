package com.waqiti.wallet.repository;

import com.waqiti.wallet.entity.WalletFreezeHistory;
import com.waqiti.wallet.enums.FreezeReason;
import com.waqiti.wallet.enums.FreezeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for wallet freeze history operations.
 * Provides queries for audit trail, compliance reporting, and analytics.
 *
 * @author Waqiti Engineering Team
 * @version 1.0
 * @since 2025-10-18
 */
@Repository
public interface WalletFreezeHistoryRepository extends JpaRepository<WalletFreezeHistory, UUID> {

    /**
     * Check if an event has already been processed (idempotency check)
     */
    boolean existsByEventId(UUID eventId);

    /**
     * Find freeze history by event ID (for idempotency verification)
     */
    Optional<WalletFreezeHistory> findByEventId(UUID eventId);

    /**
     * Get all freeze history for a specific wallet
     */
    List<WalletFreezeHistory> findByWalletIdOrderByFrozenAtDesc(UUID walletId);

    /**
     * Get all freeze history for a specific user
     */
    Page<WalletFreezeHistory> findByUserIdOrderByFrozenAtDesc(UUID userId, Pageable pageable);

    /**
     * Get active (unresolved) freezes for a wallet
     */
    @Query("SELECT f FROM WalletFreezeHistory f WHERE f.walletId = :walletId AND f.unfrozenAt IS NULL ORDER BY f.frozenAt DESC")
    List<WalletFreezeHistory> findActiveFreezesByWalletId(@Param("walletId") UUID walletId);

    /**
     * Get freeze history by freeze type
     */
    List<WalletFreezeHistory> findByFreezeTypeOrderByFrozenAtDesc(FreezeType freezeType);

    /**
     * Get freeze history by reason
     */
    List<WalletFreezeHistory> findByFreezeReasonOrderByFrozenAtDesc(FreezeReason freezeReason);

    /**
     * Find freeze history by correlation ID (for distributed tracing)
     */
    List<WalletFreezeHistory> findByCorrelationIdOrderByFrozenAtDesc(UUID correlationId);

    /**
     * Get freeze count for a wallet
     */
    @Query("SELECT COUNT(f) FROM WalletFreezeHistory f WHERE f.walletId = :walletId")
    long countByWalletId(@Param("walletId") UUID walletId);

    /**
     * Get freeze count for a user
     */
    @Query("SELECT COUNT(f) FROM WalletFreezeHistory f WHERE f.userId = :userId")
    long countByUserId(@Param("userId") UUID userId);

    /**
     * Get active freeze count for a wallet
     */
    @Query("SELECT COUNT(f) FROM WalletFreezeHistory f WHERE f.walletId = :walletId AND f.unfrozenAt IS NULL")
    long countActiveFreezesByWalletId(@Param("walletId") UUID walletId);

    /**
     * Find freezes within date range (for compliance reporting)
     */
    @Query("SELECT f FROM WalletFreezeHistory f WHERE f.frozenAt BETWEEN :startDate AND :endDate ORDER BY f.frozenAt DESC")
    List<WalletFreezeHistory> findByFrozenAtBetween(
        @Param("startDate") Instant startDate,
        @Param("endDate") Instant endDate
    );

    /**
     * Get statistics on freeze operations (for analytics)
     */
    @Query("""
        SELECT f.freezeReason,
               COUNT(f) as freezeCount,
               AVG(f.freezeDurationSeconds) as avgDurationSeconds,
               MAX(f.freezeDurationSeconds) as maxDurationSeconds
        FROM WalletFreezeHistory f
        WHERE f.frozenAt >= :startDate
        GROUP BY f.freezeReason
        """)
    List<Object[]> getFreezeStatistics(@Param("startDate") Instant startDate);

    /**
     * Find long-running freezes (frozen for more than specified hours)
     */
    @Query("""
        SELECT f FROM WalletFreezeHistory f
        WHERE f.unfrozenAt IS NULL
        AND f.frozenAt < :thresholdDate
        ORDER BY f.frozenAt ASC
        """)
    List<WalletFreezeHistory> findLongRunningFreezes(@Param("thresholdDate") Instant thresholdDate);

    /**
     * Get most recent freeze for a wallet
     */
    Optional<WalletFreezeHistory> findFirstByWalletIdOrderByFrozenAtDesc(UUID walletId);

    /**
     * Get unresolved freeze for a wallet
     */
    @Query("SELECT f FROM WalletFreezeHistory f WHERE f.walletId = :walletId AND f.unfrozenAt IS NULL ORDER BY f.frozenAt DESC")
    Optional<WalletFreezeHistory> findUnresolvedFreezeByWalletId(@Param("walletId") UUID walletId);

    /**
     * Compliance report: Get all freezes by user in date range
     */
    @Query("""
        SELECT f FROM WalletFreezeHistory f
        WHERE f.userId = :userId
        AND f.frozenAt BETWEEN :startDate AND :endDate
        ORDER BY f.frozenAt DESC
        """)
    List<WalletFreezeHistory> findUserFreezeHistory(
        @Param("userId") UUID userId,
        @Param("startDate") Instant startDate,
        @Param("endDate") Instant endDate
    );
}
