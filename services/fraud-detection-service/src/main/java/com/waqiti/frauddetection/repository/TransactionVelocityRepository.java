package com.waqiti.frauddetection.repository;

import com.waqiti.frauddetection.entity.TransactionVelocity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for transaction velocity tracking
 * Monitors transaction frequency and patterns for fraud detection
 */
@Repository
public interface TransactionVelocityRepository extends JpaRepository<TransactionVelocity, UUID> {

    /**
     * Find velocity record for user
     */
    Optional<TransactionVelocity> findByUserId(UUID userId);

    /**
     * Find users exceeding velocity thresholds
     * Uses composite index: idx_transaction_velocity_hourly_count
     */
    @Query("SELECT tv FROM TransactionVelocity tv WHERE tv.transactionsLastHour > :threshold")
    List<TransactionVelocity> findUsersExceedingHourlyThreshold(@Param("threshold") Integer threshold);

    /**
     * Find users with suspicious daily patterns
     */
    @Query("SELECT tv FROM TransactionVelocity tv WHERE tv.transactionsLast24Hours > :dailyThreshold " +
           "AND tv.totalAmountLast24Hours > :amountThreshold")
    List<TransactionVelocity> findSuspiciousDailyPatterns(
            @Param("dailyThreshold") Integer dailyThreshold,
            @Param("amountThreshold") BigDecimal amountThreshold);

    /**
     * Find recently updated velocity records
     */
    @Query("SELECT tv FROM TransactionVelocity tv WHERE tv.lastUpdated >= :since " +
           "ORDER BY tv.lastUpdated DESC")
    List<TransactionVelocity> findRecentlyActive(@Param("since") LocalDateTime since);

    /**
     * Count users with high velocity
     */
    @Query("SELECT COUNT(tv) FROM TransactionVelocity tv WHERE tv.transactionsLastHour >= :threshold")
    long countHighVelocityUsers(@Param("threshold") Integer threshold);

    /**
     * Find users with sudden velocity spikes
     */
    @Query("SELECT tv FROM TransactionVelocity tv WHERE " +
           "(tv.transactionsLastHour > tv.avgTransactionsPerHour * :multiplier) " +
           "OR (tv.totalAmountLastHour > tv.avgAmountPerHour * :multiplier)")
    List<TransactionVelocity> findVelocitySpikes(@Param("multiplier") Double multiplier);
}
