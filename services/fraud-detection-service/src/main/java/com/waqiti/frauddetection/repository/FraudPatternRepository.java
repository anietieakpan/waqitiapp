package com.waqiti.frauddetection.repository;

import com.waqiti.frauddetection.model.FraudPattern;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Repository for FraudPattern entities with advanced pattern detection queries
 */
@Repository
public interface FraudPatternRepository extends JpaRepository<FraudPattern, UUID> {

    /**
     * Find patterns by user ID within time window
     */
    List<FraudPattern> findByUserIdAndDetectedAtAfter(String userId, LocalDateTime after);

    /**
     * Find patterns by pattern type
     */
    List<FraudPattern> findByPatternType(String patternType);

    /**
     * Find patterns by risk level
     */
    List<FraudPattern> findByRiskLevel(String riskLevel);

    /**
     * Find active patterns (not resolved)
     */
    List<FraudPattern> findByStatusAndDetectedAtAfter(String status, LocalDateTime after);

    /**
     * Find patterns affecting multiple users
     */
    @Query("SELECT fp FROM FraudPattern fp WHERE SIZE(fp.affectedUserIds) > :threshold")
    List<FraudPattern> findPatternsWithMultipleUsers(@Param("threshold") int threshold);

    /**
     * Find patterns by transaction amount range
     */
    @Query("SELECT fp FROM FraudPattern fp WHERE fp.minAmount <= :amount AND fp.maxAmount >= :amount")
    List<FraudPattern> findPatternsByAmountRange(@Param("amount") java.math.BigDecimal amount);

    /**
     * Find patterns with high confidence score
     */
    @Query("SELECT fp FROM FraudPattern fp WHERE fp.confidenceScore >= :threshold ORDER BY fp.confidenceScore DESC")
    List<FraudPattern> findHighConfidencePatterns(@Param("threshold") double threshold);

    /**
     * Find similar patterns using feature similarity
     */
    @Query("SELECT fp FROM FraudPattern fp WHERE " +
           "FUNCTION('JSON_EXTRACT', fp.patternFeatures, '$.device_fingerprint') = :deviceFingerprint OR " +
           "FUNCTION('JSON_EXTRACT', fp.patternFeatures, '$.ip_address') = :ipAddress")
    List<FraudPattern> findSimilarPatterns(@Param("deviceFingerprint") String deviceFingerprint, 
                                         @Param("ipAddress") String ipAddress);

    /**
     * Count patterns by type and time period
     */
    @Query("SELECT fp.patternType, COUNT(fp) FROM FraudPattern fp " +
           "WHERE fp.detectedAt >= :startTime " +
           "GROUP BY fp.patternType " +
           "ORDER BY COUNT(fp) DESC")
    List<Object[]> countPatternsByType(@Param("startTime") LocalDateTime startTime);

    /**
     * Find evolving patterns (patterns that have changed recently)
     */
    @Query("SELECT fp FROM FraudPattern fp WHERE fp.lastUpdated > fp.detectedAt")
    List<FraudPattern> findEvolvingPatterns();

    /**
     * Find patterns associated with specific merchant
     */
    List<FraudPattern> findByMerchantIdAndDetectedAtAfter(String merchantId, LocalDateTime after);

    /**
     * Find geographic pattern clusters
     */
    @Query("SELECT fp FROM FraudPattern fp WHERE " +
           "FUNCTION('JSON_EXTRACT', fp.patternFeatures, '$.country') = :country AND " +
           "FUNCTION('JSON_EXTRACT', fp.patternFeatures, '$.city') = :city")
    List<FraudPattern> findByGeographicCluster(@Param("country") String country, @Param("city") String city);

    /**
     * Find time-based patterns (specific hours/days)
     */
    @Query("SELECT fp FROM FraudPattern fp WHERE " +
           "CAST(FUNCTION('JSON_EXTRACT', fp.patternFeatures, '$.peak_hour') AS int) = :hour")
    List<FraudPattern> findByPeakHour(@Param("hour") int hour);

    /**
     * Find patterns with minimum transaction count
     */
    @Query("SELECT fp FROM FraudPattern fp WHERE fp.transactionCount >= :minCount")
    List<FraudPattern> findPatternsWithMinTransactions(@Param("minCount") int minCount);

    /**
     * Search patterns with complex criteria
     */
    @Query("SELECT fp FROM FraudPattern fp WHERE " +
           "(:patternType IS NULL OR fp.patternType = :patternType) AND " +
           "(:riskLevel IS NULL OR fp.riskLevel = :riskLevel) AND " +
           "(:minConfidence IS NULL OR fp.confidenceScore >= :minConfidence) AND " +
           "fp.detectedAt BETWEEN :startTime AND :endTime " +
           "ORDER BY fp.confidenceScore DESC")
    Page<FraudPattern> searchPatterns(
        @Param("patternType") String patternType,
        @Param("riskLevel") String riskLevel,
        @Param("minConfidence") Double minConfidence,
        @Param("startTime") LocalDateTime startTime,
        @Param("endTime") LocalDateTime endTime,
        Pageable pageable
    );

    /**
     * Find patterns that should be archived (old and resolved)
     */
    @Query("SELECT fp FROM FraudPattern fp WHERE " +
           "fp.status = 'RESOLVED' AND " +
           "fp.lastUpdated < :archiveThreshold")
    List<FraudPattern> findPatternsForArchiving(@Param("archiveThreshold") LocalDateTime archiveThreshold);

    /**
     * Update pattern status in batch
     */
    @Query("UPDATE FraudPattern fp SET fp.status = :status, fp.lastUpdated = :timestamp " +
           "WHERE fp.id IN :patternIds")
    void updatePatternStatusBatch(@Param("patternIds") List<UUID> patternIds, 
                                 @Param("status") String status, 
                                 @Param("timestamp") LocalDateTime timestamp);

    /**
     * Find patterns with specific features
     */
    @Query("SELECT fp FROM FraudPattern fp WHERE " +
           "FUNCTION('JSON_CONTAINS', fp.patternFeatures, :featureJson) = 1")
    List<FraudPattern> findPatternsWithFeatures(@Param("featureJson") String featureJson);

    /**
     * Get pattern statistics
     */
    @Query("SELECT " +
           "COUNT(fp) as totalPatterns, " +
           "AVG(fp.confidenceScore) as avgConfidence, " +
           "MAX(fp.transactionCount) as maxTransactions, " +
           "SUM(fp.affectedTransactionCount) as totalAffectedTransactions " +
           "FROM FraudPattern fp " +
           "WHERE fp.detectedAt >= :since")
    Object[] getPatternStatistics(@Param("since") LocalDateTime since);

    /**
     * Find cross-pattern correlations
     */
    @Query("SELECT fp1, fp2 FROM FraudPattern fp1, FraudPattern fp2 WHERE " +
           "fp1.id != fp2.id AND " +
           "SIZE(fp1.affectedUserIds) > 0 AND " +
           "SIZE(fp2.affectedUserIds) > 0 AND " +
           "EXISTS (SELECT au1 FROM fp1.affectedUserIds au1 WHERE au1 IN fp2.affectedUserIds)")
    List<Object[]> findCorrelatedPatterns();

    /**
     * Find seasonal patterns
     */
    @Query("SELECT fp FROM FraudPattern fp WHERE " +
           "EXTRACT(MONTH FROM fp.detectedAt) = :month AND " +
           "fp.patternType = :patternType")
    List<FraudPattern> findSeasonalPatterns(@Param("month") int month, @Param("patternType") String patternType);

    /**
     * Delete old patterns beyond retention period
     */
    @Query("DELETE FROM FraudPattern fp WHERE fp.detectedAt < :retentionThreshold")
    void deleteOldPatterns(@Param("retentionThreshold") LocalDateTime retentionThreshold);
}