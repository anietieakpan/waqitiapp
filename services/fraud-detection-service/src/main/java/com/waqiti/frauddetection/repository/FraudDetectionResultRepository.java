package com.waqiti.frauddetection.repository;

import com.waqiti.frauddetection.model.FraudDetectionResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
 * Repository for FraudDetectionResult entities with comprehensive fraud tracking
 */
@Repository
public interface FraudDetectionResultRepository extends JpaRepository<FraudDetectionResult, UUID> {

    /**
     * Find fraud detection result by transaction ID
     */
    Optional<FraudDetectionResult> findByTransactionId(String transactionId);

    /**
     * Find all results for a user
     */
    List<FraudDetectionResult> findByUserIdOrderByDetectedAtDesc(String userId);

    /**
     * Find results by risk level
     */
    List<FraudDetectionResult> findByRiskLevelOrderByDetectedAtDesc(String riskLevel);

    /**
     * Find high-risk detections for review
     */
    @Query("SELECT fdr FROM FraudDetectionResult fdr WHERE " +
           "fdr.riskScore >= :threshold AND " +
           "fdr.status = 'PENDING_REVIEW' " +
           "ORDER BY fdr.riskScore DESC, fdr.detectedAt DESC")
    List<FraudDetectionResult> findHighRiskPendingReview(@Param("threshold") double threshold);

    /**
     * Find results within time range
     */
    List<FraudDetectionResult> findByDetectedAtBetweenOrderByDetectedAtDesc(LocalDateTime startTime, LocalDateTime endTime);

    /**
     * Find results by model version
     */
    List<FraudDetectionResult> findByModelVersionOrderByDetectedAtDesc(String modelVersion);

    /**
     * Find confirmed fraud cases for training
     */
    @Query("SELECT fdr FROM FraudDetectionResult fdr WHERE " +
           "fdr.actualFraudStatus = 'CONFIRMED_FRAUD' AND " +
           "fdr.verifiedAt >= :since " +
           "ORDER BY fdr.detectedAt DESC")
    List<FraudDetectionResult> findConfirmedFraudCases(@Param("since") LocalDateTime since);

    /**
     * Find false positive cases for model improvement
     */
    @Query("SELECT fdr FROM FraudDetectionResult fdr WHERE " +
           "fdr.riskLevel IN ('HIGH', 'MEDIUM') AND " +
           "fdr.actualFraudStatus = 'CONFIRMED_LEGITIMATE' " +
           "ORDER BY fdr.riskScore DESC")
    List<FraudDetectionResult> findFalsePositiveCases();

    /**
     * Find missed fraud cases (false negatives)
     */
    @Query("SELECT fdr FROM FraudDetectionResult fdr WHERE " +
           "fdr.riskLevel = 'LOW' AND " +
           "fdr.actualFraudStatus = 'CONFIRMED_FRAUD' " +
           "ORDER BY fdr.detectedAt DESC")
    List<FraudDetectionResult> findMissedFraudCases();

    /**
     * Count detections by risk level and time period
     */
    @Query("SELECT fdr.riskLevel, COUNT(fdr) FROM FraudDetectionResult fdr " +
           "WHERE fdr.detectedAt >= :since " +
           "GROUP BY fdr.riskLevel")
    List<Object[]> countDetectionsByRiskLevel(@Param("since") LocalDateTime since);

    /**
     * Find results with specific features
     */
    @Query("SELECT fdr FROM FraudDetectionResult fdr WHERE " +
           "FUNCTION('JSON_EXTRACT', fdr.features, :featurePath) = :featureValue")
    List<FraudDetectionResult> findByFeature(@Param("featurePath") String featurePath, 
                                           @Param("featureValue") String featureValue);

    /**
     * Find results by transaction amount range
     */
    @Query("SELECT fdr FROM FraudDetectionResult fdr WHERE " +
           "fdr.transactionAmount BETWEEN :minAmount AND :maxAmount " +
           "ORDER BY fdr.transactionAmount DESC")
    List<FraudDetectionResult> findByAmountRange(@Param("minAmount") BigDecimal minAmount, 
                                                @Param("maxAmount") BigDecimal maxAmount);

    /**
     * Get model performance statistics
     */
    @Query("SELECT " +
           "COUNT(fdr) as totalDetections, " +
           "AVG(fdr.riskScore) as avgRiskScore, " +
           "COUNT(CASE WHEN fdr.riskLevel = 'HIGH' THEN 1 END) as highRiskCount, " +
           "COUNT(CASE WHEN fdr.actualFraudStatus = 'CONFIRMED_FRAUD' THEN 1 END) as confirmedFraudCount, " +
           "COUNT(CASE WHEN fdr.riskLevel = 'HIGH' AND fdr.actualFraudStatus = 'CONFIRMED_FRAUD' THEN 1 END) as truePositives " +
           "FROM FraudDetectionResult fdr WHERE fdr.detectedAt >= :since")
    Object[] getPerformanceStatistics(@Param("since") LocalDateTime since);

    /**
     * Find results requiring manual review
     */
    @Query("SELECT fdr FROM FraudDetectionResult fdr WHERE " +
           "(fdr.riskScore >= :highThreshold OR " +
           " SIZE(fdr.triggeredRules) >= :ruleThreshold OR " +
           " SIZE(fdr.anomalyFlags) >= :anomalyThreshold) AND " +
           "fdr.status = 'PENDING_REVIEW' " +
           "ORDER BY fdr.riskScore DESC")
    List<FraudDetectionResult> findResultsRequiringReview(@Param("highThreshold") double highThreshold,
                                                         @Param("ruleThreshold") int ruleThreshold,
                                                         @Param("anomalyThreshold") int anomalyThreshold);

    /**
     * Find results by specific rules triggered
     */
    @Query("SELECT fdr FROM FraudDetectionResult fdr WHERE " +
           "FUNCTION('JSON_CONTAINS', fdr.triggeredRules, :ruleName) = 1")
    List<FraudDetectionResult> findByTriggeredRule(@Param("ruleName") String ruleName);

    /**
     * Find users with multiple high-risk detections
     */
    @Query("SELECT fdr.userId, COUNT(fdr) as detectionCount FROM FraudDetectionResult fdr " +
           "WHERE fdr.riskLevel = 'HIGH' AND fdr.detectedAt >= :since " +
           "GROUP BY fdr.userId " +
           "HAVING COUNT(fdr) >= :threshold " +
           "ORDER BY COUNT(fdr) DESC")
    List<Object[]> findUsersWithMultipleHighRiskDetections(@Param("since") LocalDateTime since, 
                                                          @Param("threshold") int threshold);

    /**
     * Find results by confidence score range
     */
    List<FraudDetectionResult> findByConfidenceScoreBetweenOrderByConfidenceScoreDesc(double minConfidence, double maxConfidence);

    /**
     * Search results with complex criteria
     */
    @Query("SELECT fdr FROM FraudDetectionResult fdr WHERE " +
           "(:userId IS NULL OR fdr.userId = :userId) AND " +
           "(:riskLevel IS NULL OR fdr.riskLevel = :riskLevel) AND " +
           "(:minRiskScore IS NULL OR fdr.riskScore >= :minRiskScore) AND " +
           "(:minAmount IS NULL OR fdr.transactionAmount >= :minAmount) AND " +
           "(:maxAmount IS NULL OR fdr.transactionAmount <= :maxAmount) AND " +
           "fdr.detectedAt BETWEEN :startTime AND :endTime " +
           "ORDER BY fdr.detectedAt DESC")
    Page<FraudDetectionResult> searchResults(
        @Param("userId") String userId,
        @Param("riskLevel") String riskLevel,
        @Param("minRiskScore") Double minRiskScore,
        @Param("minAmount") BigDecimal minAmount,
        @Param("maxAmount") BigDecimal maxAmount,
        @Param("startTime") LocalDateTime startTime,
        @Param("endTime") LocalDateTime endTime,
        Pageable pageable
    );

    /**
     * Find trending fraud patterns
     */
    @Query("SELECT " +
           "DATE(fdr.detectedAt) as detectionDate, " +
           "fdr.riskLevel, " +
           "COUNT(fdr) as count " +
           "FROM FraudDetectionResult fdr " +
           "WHERE fdr.detectedAt >= :since " +
           "GROUP BY DATE(fdr.detectedAt), fdr.riskLevel " +
           "ORDER BY detectionDate DESC")
    List<Object[]> getFraudTrends(@Param("since") LocalDateTime since);

    /**
     * Find results with processing errors
     */
    @Query("SELECT fdr FROM FraudDetectionResult fdr WHERE " +
           "fdr.processingErrors IS NOT NULL AND SIZE(fdr.processingErrors) > 0 " +
           "ORDER BY fdr.detectedAt DESC")
    List<FraudDetectionResult> findResultsWithErrors();

    /**
     * Update verification status in batch
     */
    @Query("UPDATE FraudDetectionResult fdr SET " +
           "fdr.actualFraudStatus = :fraudStatus, " +
           "fdr.verifiedAt = :timestamp, " +
           "fdr.verifiedBy = :verifiedBy " +
           "WHERE fdr.id IN :resultIds")
    void batchUpdateVerificationStatus(@Param("resultIds") List<UUID> resultIds,
                                     @Param("fraudStatus") String fraudStatus,
                                     @Param("timestamp") LocalDateTime timestamp,
                                     @Param("verifiedBy") String verifiedBy);

    /**
     * Find stale unverified results
     */
    @Query("SELECT fdr FROM FraudDetectionResult fdr WHERE " +
           "fdr.actualFraudStatus IS NULL AND " +
           "fdr.detectedAt < :staleThreshold AND " +
           "fdr.riskLevel IN ('HIGH', 'MEDIUM') " +
           "ORDER BY fdr.detectedAt ASC")
    List<FraudDetectionResult> findStaleUnverifiedResults(@Param("staleThreshold") LocalDateTime staleThreshold);

    /**
     * Calculate model accuracy metrics
     */
    @Query("SELECT " +
           "fdr.modelVersion, " +
           "COUNT(fdr) as totalPredictions, " +
           "COUNT(CASE WHEN fdr.riskLevel = 'HIGH' AND fdr.actualFraudStatus = 'CONFIRMED_FRAUD' THEN 1 END) as truePositives, " +
           "COUNT(CASE WHEN fdr.riskLevel = 'HIGH' AND fdr.actualFraudStatus = 'CONFIRMED_LEGITIMATE' THEN 1 END) as falsePositives, " +
           "COUNT(CASE WHEN fdr.riskLevel != 'HIGH' AND fdr.actualFraudStatus = 'CONFIRMED_FRAUD' THEN 1 END) as falseNegatives, " +
           "COUNT(CASE WHEN fdr.riskLevel != 'HIGH' AND fdr.actualFraudStatus = 'CONFIRMED_LEGITIMATE' THEN 1 END) as trueNegatives " +
           "FROM FraudDetectionResult fdr " +
           "WHERE fdr.actualFraudStatus IS NOT NULL " +
           "GROUP BY fdr.modelVersion")
    List<Object[]> calculateModelAccuracyMetrics();

    /**
     * Find results similar to a given result (for pattern analysis)
     */
    @Query("SELECT fdr FROM FraudDetectionResult fdr WHERE " +
           "fdr.id != :excludeId AND " +
           "ABS(fdr.riskScore - :riskScore) <= :scoreTolerance AND " +
           "fdr.detectedAt >= :since " +
           "ORDER BY ABS(fdr.riskScore - :riskScore)")
    List<FraudDetectionResult> findSimilarResults(@Param("excludeId") UUID excludeId,
                                                 @Param("riskScore") double riskScore,
                                                 @Param("scoreTolerance") double scoreTolerance,
                                                 @Param("since") LocalDateTime since);

    /**
     * Get hourly detection statistics
     */
    @Query("SELECT " +
           "EXTRACT(HOUR FROM fdr.detectedAt) as hour, " +
           "COUNT(fdr) as detectionCount, " +
           "AVG(fdr.riskScore) as avgRiskScore " +
           "FROM FraudDetectionResult fdr " +
           "WHERE fdr.detectedAt >= :since " +
           "GROUP BY EXTRACT(HOUR FROM fdr.detectedAt) " +
           "ORDER BY hour")
    List<Object[]> getHourlyDetectionStatistics(@Param("since") LocalDateTime since);

    /**
     * Find results that need feature recalculation
     */
    @Query("SELECT fdr FROM FraudDetectionResult fdr WHERE " +
           "fdr.features IS NULL OR " +
           "FUNCTION('JSON_LENGTH', fdr.features) < :minFeatureCount " +
           "ORDER BY fdr.detectedAt DESC")
    List<FraudDetectionResult> findResultsNeedingFeatureRecalculation(@Param("minFeatureCount") int minFeatureCount);

    /**
     * Archive old verified results
     */
    @Query("UPDATE FraudDetectionResult fdr SET fdr.archived = true " +
           "WHERE fdr.verifiedAt < :archiveThreshold AND fdr.actualFraudStatus IS NOT NULL")
    void archiveOldVerifiedResults(@Param("archiveThreshold") LocalDateTime archiveThreshold);

    /**
     * Delete archived results beyond retention period
     */
    @Query("DELETE FROM FraudDetectionResult fdr WHERE " +
           "fdr.archived = true AND fdr.detectedAt < :retentionThreshold")
    void deleteArchivedResults(@Param("retentionThreshold") LocalDateTime retentionThreshold);
}