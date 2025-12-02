package com.waqiti.ml.repository;

import com.waqiti.ml.entity.TransactionPattern;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Production-ready repository for Transaction Pattern operations.
 * Optimized queries for ML analysis and behavioral pattern detection.
 */
@Repository
public interface TransactionPatternRepository extends JpaRepository<TransactionPattern, String> {

    /**
     * Find patterns by user ID and time range
     */
    @Query("SELECT tp FROM TransactionPattern tp WHERE tp.userId = :userId AND tp.timestamp >= :startTime ORDER BY tp.timestamp DESC")
    List<TransactionPattern> findByUserIdAndTimestampAfter(@Param("userId") String userId, 
                                                          @Param("startTime") LocalDateTime startTime);

    /**
     * Find patterns by user ID and time range
     */
    @Query("SELECT tp FROM TransactionPattern tp WHERE tp.userId = :userId AND tp.timestamp BETWEEN :startTime AND :endTime ORDER BY tp.timestamp DESC")
    List<TransactionPattern> findByUserIdAndTimestampBetween(@Param("userId") String userId,
                                                            @Param("startTime") LocalDateTime startTime,
                                                            @Param("endTime") LocalDateTime endTime);

    /**
     * Count transactions by user ID and time range
     */
    @Query("SELECT COUNT(tp) FROM TransactionPattern tp WHERE tp.userId = :userId AND tp.timestamp BETWEEN :startTime AND :endTime")
    Long countByUserIdAndTimestampBetween(@Param("userId") String userId,
                                         @Param("startTime") LocalDateTime startTime,
                                         @Param("endTime") LocalDateTime endTime);

    /**
     * Count transactions by user ID after specific time
     */
    @Query("SELECT COUNT(tp) FROM TransactionPattern tp WHERE tp.userId = :userId AND tp.timestamp >= :startTime")
    Long countByUserIdAndTimestampAfter(@Param("userId") String userId, @Param("startTime") LocalDateTime startTime);

    /**
     * Find frequent recipients for a user
     */
    @Query("SELECT tp.targetAccount FROM TransactionPattern tp WHERE tp.userId = :userId AND tp.timestamp >= :startTime " +
           "GROUP BY tp.targetAccount HAVING COUNT(tp) >= :minTransactions ORDER BY COUNT(tp) DESC")
    List<String> findFrequentRecipients(@Param("userId") String userId, 
                                       @Param("startTime") LocalDateTime startTime,
                                       @Param("minTransactions") Integer minTransactions);

    /**
     * Check if user has transacted with specific account before
     */
    @Query("SELECT COUNT(tp) > 0 FROM TransactionPattern tp WHERE tp.userId = :userId AND tp.targetAccount = :targetAccount")
    Boolean existsByUserIdAndTargetAccount(@Param("userId") String userId, @Param("targetAccount") String targetAccount);

    /**
     * Find recent transaction timestamps for burst detection
     */
    @Query("SELECT tp.timestamp FROM TransactionPattern tp WHERE tp.userId = :userId AND tp.timestamp >= :startTime ORDER BY tp.timestamp DESC")
    List<LocalDateTime> findRecentTransactionTimes(@Param("userId") String userId, @Param("startTime") LocalDateTime startTime);

    /**
     * Find patterns by risk score range
     */
    @Query("SELECT tp FROM TransactionPattern tp WHERE tp.riskScore BETWEEN :minScore AND :maxScore ORDER BY tp.riskScore DESC")
    List<TransactionPattern> findByRiskScoreRange(@Param("minScore") Double minScore, @Param("maxScore") Double maxScore);

    /**
     * Find high-risk transactions for review
     */
    @Query("SELECT tp FROM TransactionPattern tp WHERE tp.flaggedForReview = true OR tp.riskScore >= :riskThreshold ORDER BY tp.riskScore DESC, tp.timestamp DESC")
    List<TransactionPattern> findHighRiskTransactions(@Param("riskThreshold") Double riskThreshold);

    /**
     * Find patterns by transaction type and time range
     */
    @Query("SELECT tp FROM TransactionPattern tp WHERE tp.transactionType = :transactionType AND tp.timestamp >= :startTime")
    List<TransactionPattern> findByTransactionTypeAndTimestampAfter(@Param("transactionType") String transactionType,
                                                                   @Param("startTime") LocalDateTime startTime);

    /**
     * Find international transactions by user
     */
    @Query("SELECT tp FROM TransactionPattern tp WHERE tp.userId = :userId AND tp.isInternational = true ORDER BY tp.timestamp DESC")
    List<TransactionPattern> findInternationalTransactionsByUser(@Param("userId") String userId);

    /**
     * Find high-value transactions by user
     */
    @Query("SELECT tp FROM TransactionPattern tp WHERE tp.userId = :userId AND tp.isHighValue = true ORDER BY tp.amount DESC")
    List<TransactionPattern> findHighValueTransactionsByUser(@Param("userId") String userId);

    /**
     * Find transactions by device ID
     */
    @Query("SELECT tp FROM TransactionPattern tp WHERE tp.deviceId = :deviceId ORDER BY tp.timestamp DESC")
    List<TransactionPattern> findByDeviceId(@Param("deviceId") String deviceId);

    /**
     * Find transactions from suspicious IPs
     */
    @Query("SELECT tp FROM TransactionPattern tp WHERE tp.networkRiskScore >= :riskThreshold ORDER BY tp.networkRiskScore DESC")
    List<TransactionPattern> findSuspiciousNetworkTransactions(@Param("riskThreshold") Double riskThreshold);

    /**
     * Find round amount transactions (potential structuring)
     */
    @Query("SELECT tp FROM TransactionPattern tp WHERE tp.isRoundAmount = true AND tp.amount >= :minAmount ORDER BY tp.timestamp DESC")
    List<TransactionPattern> findRoundAmountTransactions(@Param("minAmount") BigDecimal minAmount);

    /**
     * Find velocity patterns for user
     */
    @Query("SELECT tp FROM TransactionPattern tp WHERE tp.userId = :userId AND tp.velocityScore >= :velocityThreshold ORDER BY tp.velocityScore DESC")
    List<TransactionPattern> findHighVelocityTransactions(@Param("userId") String userId, @Param("velocityThreshold") Double velocityThreshold);

    /**
     * Find anomalous transactions
     */
    @Query("SELECT tp FROM TransactionPattern tp WHERE tp.anomalyScore >= :anomalyThreshold ORDER BY tp.anomalyScore DESC, tp.timestamp DESC")
    List<TransactionPattern> findAnomalousTransactions(@Param("anomalyThreshold") Double anomalyThreshold);

    /**
     * Get transaction statistics for user
     */
    @Query("SELECT " +
           "COUNT(tp) as totalTransactions, " +
           "SUM(tp.amount) as totalAmount, " +
           "AVG(tp.amount) as averageAmount, " +
           "MIN(tp.amount) as minAmount, " +
           "MAX(tp.amount) as maxAmount, " +
           "AVG(tp.riskScore) as averageRiskScore " +
           "FROM TransactionPattern tp WHERE tp.userId = :userId AND tp.timestamp >= :startTime")
    Object getTransactionStatistics(@Param("userId") String userId, @Param("startTime") LocalDateTime startTime);

    /**
     * Find transactions with compliance flags
     */
    @Query("SELECT tp FROM TransactionPattern tp WHERE tp.amlFlag = true OR tp.pepScreening = true OR tp.sanctionsScreening = true")
    List<TransactionPattern> findComplianceFlaggedTransactions();

    /**
     * Find failed ML processing transactions
     */
    @Query("SELECT tp FROM TransactionPattern tp WHERE tp.processedByMl = false OR tp.mlModelVersion IS NULL")
    List<TransactionPattern> findUnprocessedByML();

    /**
     * Get hourly transaction distribution for user
     */
    @Query("SELECT tp.hourOfDay, COUNT(tp) FROM TransactionPattern tp WHERE tp.userId = :userId GROUP BY tp.hourOfDay ORDER BY tp.hourOfDay")
    List<Object[]> getHourlyDistribution(@Param("userId") String userId);

    /**
     * Get daily transaction distribution for user
     */
    @Query("SELECT tp.dayOfWeek, COUNT(tp) FROM TransactionPattern tp WHERE tp.userId = :userId GROUP BY tp.dayOfWeek ORDER BY tp.dayOfWeek")
    List<Object[]> getDailyDistribution(@Param("userId") String userId);

    /**
     * Find recurring transaction patterns
     */
    @Query("SELECT tp.targetAccount, COUNT(tp) as frequency FROM TransactionPattern tp " +
           "WHERE tp.userId = :userId AND tp.timestamp >= :startTime " +
           "GROUP BY tp.targetAccount HAVING COUNT(tp) >= :minFrequency " +
           "ORDER BY COUNT(tp) DESC")
    List<Object[]> findRecurringTransactionPatterns(@Param("userId") String userId,
                                                   @Param("startTime") LocalDateTime startTime,
                                                   @Param("minFrequency") Long minFrequency);

    /**
     * Find merchant transaction patterns
     */
    @Query("SELECT tp.merchantId, tp.merchantCategory, COUNT(tp) as frequency, SUM(tp.amount) as totalAmount " +
           "FROM TransactionPattern tp WHERE tp.userId = :userId AND tp.merchantId IS NOT NULL " +
           "GROUP BY tp.merchantId, tp.merchantCategory ORDER BY COUNT(tp) DESC")
    List<Object[]> findMerchantTransactionPatterns(@Param("userId") String userId);

    /**
     * Find location-based patterns
     */
    @Query("SELECT tp.countryCode, tp.city, COUNT(tp) as frequency FROM TransactionPattern tp " +
           "WHERE tp.userId = :userId AND tp.countryCode IS NOT NULL " +
           "GROUP BY tp.countryCode, tp.city ORDER BY COUNT(tp) DESC")
    List<Object[]> findLocationPatterns(@Param("userId") String userId);

    /**
     * Find device usage patterns
     */
    @Query("SELECT tp.deviceId, tp.deviceType, COUNT(tp) as frequency, MAX(tp.timestamp) as lastUsed " +
           "FROM TransactionPattern tp WHERE tp.userId = :userId AND tp.deviceId IS NOT NULL " +
           "GROUP BY tp.deviceId, tp.deviceType ORDER BY COUNT(tp) DESC")
    List<Object[]> findDeviceUsagePatterns(@Param("userId") String userId);

    /**
     * Get monthly transaction trends
     */
    @Query("SELECT EXTRACT(YEAR FROM tp.timestamp) as year, EXTRACT(MONTH FROM tp.timestamp) as month, " +
           "COUNT(tp) as transactionCount, SUM(tp.amount) as totalAmount " +
           "FROM TransactionPattern tp WHERE tp.userId = :userId " +
           "GROUP BY EXTRACT(YEAR FROM tp.timestamp), EXTRACT(MONTH FROM tp.timestamp) " +
           "ORDER BY year DESC, month DESC")
    List<Object[]> getMonthlyTransactionTrends(@Param("userId") String userId);

    /**
     * Find correlated users based on shared recipients
     */
    @Query("SELECT tp2.userId, COUNT(DISTINCT tp1.targetAccount) as sharedRecipients " +
           "FROM TransactionPattern tp1, TransactionPattern tp2 " +
           "WHERE tp1.userId = :userId AND tp2.userId != :userId " +
           "AND tp1.targetAccount = tp2.targetAccount " +
           "GROUP BY tp2.userId HAVING COUNT(DISTINCT tp1.targetAccount) >= :minSharedRecipients " +
           "ORDER BY COUNT(DISTINCT tp1.targetAccount) DESC")
    List<Object[]> findCorrelatedUsers(@Param("userId") String userId, @Param("minSharedRecipients") Integer minSharedRecipients);

    /**
     * Performance optimization: Batch delete old patterns
     */
    @Query("DELETE FROM TransactionPattern tp WHERE tp.timestamp < :cutoffDate")
    int deleteOldPatterns(@Param("cutoffDate") LocalDateTime cutoffDate);
}