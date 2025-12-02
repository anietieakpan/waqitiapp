package com.waqiti.payment.repository;

import com.waqiti.payment.domain.BalanceUpdate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BalanceUpdateRepository extends JpaRepository<BalanceUpdate, UUID> {

    /**
     * Find all balance updates for an account
     */
    List<BalanceUpdate> findByAccountIdOrderByProcessedAtDesc(String accountId);

    /**
     * Find balance update by transaction ID
     */
    Optional<BalanceUpdate> findByTransactionId(String transactionId);

    /**
     * Find balance updates by update type
     */
    List<BalanceUpdate> findByUpdateType(String updateType);

    /**
     * Find balance updates for an account within a date range
     */
    @Query("SELECT bu FROM BalanceUpdate bu WHERE bu.accountId = :accountId " +
           "AND bu.processedAt BETWEEN :startDate AND :endDate ORDER BY bu.processedAt DESC")
    List<BalanceUpdate> findByAccountIdAndDateRange(
        @Param("accountId") String accountId,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );

    /**
     * Find balance updates by correlation ID
     */
    List<BalanceUpdate> findByCorrelationId(String correlationId);

    /**
     * Count balance updates by account and update type
     */
    long countByAccountIdAndUpdateType(String accountId, String updateType);

    /**
     * Find recent balance updates for an account
     */
    @Query("SELECT bu FROM BalanceUpdate bu WHERE bu.accountId = :accountId " +
           "ORDER BY bu.processedAt DESC")
    List<BalanceUpdate> findRecentByAccountId(@Param("accountId") String accountId);
}
