package com.waqiti.frauddetection.repository;

import com.waqiti.frauddetection.model.TransactionFeature;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for TransactionFeature entities with ML feature management
 */
@Repository
public interface TransactionFeatureRepository extends JpaRepository<TransactionFeature, UUID> {

    /**
     * Find features by transaction ID
     */
    Optional<TransactionFeature> findByTransactionId(String transactionId);

    /**
     * Find features by user ID
     */
    List<TransactionFeature> findByUserIdOrderByCreatedAtDesc(String userId);

    /**
     * Find features within time range
     */
    List<TransactionFeature> findByCreatedAtBetweenOrderByCreatedAtDesc(LocalDateTime startTime, LocalDateTime endTime);

    /**
     * Find features by feature version (for model compatibility)
     */
    List<TransactionFeature> findByFeatureVersionOrderByCreatedAtDesc(String featureVersion);

    /**
     * Find features that need recalculation
     */
    @Query("SELECT tf FROM TransactionFeature tf WHERE " +
           "tf.lastUpdated < :threshold OR " +
           "tf.featureVersion != :currentVersion")
    List<TransactionFeature> findFeaturesNeedingUpdate(@Param("threshold") LocalDateTime threshold,
                                                      @Param("currentVersion") String currentVersion);

    /**
     * Find features with specific feature values
     */
    @Query("SELECT tf FROM TransactionFeature tf WHERE " +
           "FUNCTION('JSON_EXTRACT', tf.features, :featurePath) = :featureValue")
    List<TransactionFeature> findByFeatureValue(@Param("featurePath") String featurePath,
                                              @Param("featureValue") String featureValue);

    /**
     * Find features with missing critical features
     */
    @Query("SELECT tf FROM TransactionFeature tf WHERE " +
           "FUNCTION('JSON_EXTRACT', tf.features, '$.amount') IS NULL OR " +
           "FUNCTION('JSON_EXTRACT', tf.features, '$.user_behavior_score') IS NULL OR " +
           "FUNCTION('JSON_EXTRACT', tf.features, '$.velocity_score') IS NULL")
    List<TransactionFeature> findFeaturesWithMissingCriticalData();

    /**
     * Find features by computation status
     */
    List<TransactionFeature> findByComputationStatusOrderByCreatedAtDesc(String computationStatus);

    /**
     * Find features with high anomaly scores
     */
    @Query("SELECT tf FROM TransactionFeature tf WHERE tf.anomalyScore >= :threshold ORDER BY tf.anomalyScore DESC")
    List<TransactionFeature> findHighAnomalyScoreFeatures(@Param("threshold") double threshold);

    /**
     * Find features for model training
     */
    @Query("SELECT tf FROM TransactionFeature tf WHERE " +
           "tf.labelAvailable = true AND " +
           "tf.computationStatus = 'COMPLETED' AND " +
           "tf.createdAt >= :since " +
           "ORDER BY tf.createdAt DESC")
    List<TransactionFeature> findFeaturesForTraining(@Param("since") LocalDateTime since);

    /**
     * Find features with computation errors
     */
    @Query("SELECT tf FROM TransactionFeature tf WHERE " +
           "tf.computationStatus = 'ERROR' OR " +
           "tf.errorMessage IS NOT NULL " +
           "ORDER BY tf.createdAt DESC")
    List<TransactionFeature> findFeaturesWithErrors();

    /**
     * Count features by user and time period
     */
    @Query("SELECT tf.userId, COUNT(tf) FROM TransactionFeature tf " +
           "WHERE tf.createdAt >= :since " +
           "GROUP BY tf.userId " +
           "ORDER BY COUNT(tf) DESC")
    List<Object[]> countFeaturesByUser(@Param("since") LocalDateTime since);

    /**
     * Find features for specific feature engineering pipeline
     */
    @Query("SELECT tf FROM TransactionFeature tf WHERE " +
           "tf.pipelineId = :pipelineId AND " +
           "tf.computationStatus = 'COMPLETED' " +
           "ORDER BY tf.createdAt DESC")
    List<TransactionFeature> findByPipeline(@Param("pipelineId") String pipelineId);

    /**
     * Search features with complex criteria
     */
    @Query("SELECT tf FROM TransactionFeature tf WHERE " +
           "(:userId IS NULL OR tf.userId = :userId) AND " +
           "(:featureVersion IS NULL OR tf.featureVersion = :featureVersion) AND " +
           "(:status IS NULL OR tf.computationStatus = :status) AND " +
           "(:minAnomalyScore IS NULL OR tf.anomalyScore >= :minAnomalyScore) AND " +
           "tf.createdAt BETWEEN :startTime AND :endTime " +
           "ORDER BY tf.createdAt DESC")
    Page<TransactionFeature> searchFeatures(
        @Param("userId") String userId,
        @Param("featureVersion") String featureVersion,
        @Param("status") String status,
        @Param("minAnomalyScore") Double minAnomalyScore,
        @Param("startTime") LocalDateTime startTime,
        @Param("endTime") LocalDateTime endTime,
        Pageable pageable
    );

    /**
     * Find similar feature vectors for nearest neighbor analysis
     */
    @Query("SELECT tf FROM TransactionFeature tf WHERE " +
           "tf.id != :excludeId AND " +
           "tf.featureVersion = :featureVersion AND " +
           "tf.computationStatus = 'COMPLETED' " +
           "ORDER BY tf.createdAt DESC")
    List<TransactionFeature> findSimilarFeatures(@Param("excludeId") UUID excludeId,
                                                @Param("featureVersion") String featureVersion,
                                                Pageable pageable);

    /**
     * Get feature statistics for monitoring
     */
    @Query("SELECT " +
           "COUNT(tf) as totalFeatures, " +
           "COUNT(CASE WHEN tf.computationStatus = 'COMPLETED' THEN 1 END) as completedFeatures, " +
           "COUNT(CASE WHEN tf.computationStatus = 'ERROR' THEN 1 END) as errorFeatures, " +
           "AVG(tf.computationTimeMs) as avgComputationTime, " +
           "AVG(tf.anomalyScore) as avgAnomalyScore " +
           "FROM TransactionFeature tf WHERE tf.createdAt >= :since")
    Object[] getFeatureStatistics(@Param("since") LocalDateTime since);

    /**
     * Find features with specific dimensionality
     */
    @Query("SELECT tf FROM TransactionFeature tf WHERE " +
           "FUNCTION('JSON_LENGTH', tf.features) = :featureCount")
    List<TransactionFeature> findByFeatureCount(@Param("featureCount") int featureCount);

    /**
     * Find features computed by specific algorithm
     */
    List<TransactionFeature> findByComputationAlgorithmOrderByCreatedAtDesc(String computationAlgorithm);

    /**
     * Find outdated features that should be recomputed
     */
    @Query("SELECT tf FROM TransactionFeature tf WHERE " +
           "tf.lastUpdated < :staleThreshold AND " +
           "tf.computationStatus = 'COMPLETED' " +
           "ORDER BY tf.lastUpdated ASC")
    List<TransactionFeature> findStaleFeatures(@Param("staleThreshold") LocalDateTime staleThreshold);

    /**
     * Update computation status in batch
     */
    @Query("UPDATE TransactionFeature tf SET " +
           "tf.computationStatus = :status, " +
           "tf.lastUpdated = :timestamp " +
           "WHERE tf.id IN :featureIds")
    void updateComputationStatusBatch(@Param("featureIds") List<UUID> featureIds,
                                    @Param("status") String status,
                                    @Param("timestamp") LocalDateTime timestamp);

    /**
     * Find features for A/B testing
     */
    @Query("SELECT tf FROM TransactionFeature tf WHERE " +
           "tf.testGroup IS NOT NULL AND " +
           "tf.testGroup = :testGroup AND " +
           "tf.createdAt >= :since")
    List<TransactionFeature> findByTestGroup(@Param("testGroup") String testGroup,
                                           @Param("since") LocalDateTime since);

    /**
     * Get feature distribution statistics
     */
    @Query("SELECT " +
           "tf.featureVersion, " +
           "COUNT(tf) as count, " +
           "AVG(tf.anomalyScore) as avgAnomalyScore, " +
           "MIN(tf.createdAt) as firstCreated, " +
           "MAX(tf.createdAt) as lastCreated " +
           "FROM TransactionFeature tf " +
           "WHERE tf.createdAt >= :since " +
           "GROUP BY tf.featureVersion")
    List<Object[]> getFeatureDistributionStats(@Param("since") LocalDateTime since);

    /**
     * Find features with high computation time (performance issues)
     */
    @Query("SELECT tf FROM TransactionFeature tf WHERE " +
           "tf.computationTimeMs > :threshold " +
           "ORDER BY tf.computationTimeMs DESC")
    List<TransactionFeature> findSlowComputationFeatures(@Param("threshold") long threshold);

    /**
     * Find correlated features for feature selection
     */
    @Query("SELECT tf1, tf2 FROM TransactionFeature tf1, TransactionFeature tf2 WHERE " +
           "tf1.userId = tf2.userId AND " +
           "tf1.id != tf2.id AND " +
           "ABS(tf1.anomalyScore - tf2.anomalyScore) < :correlationThreshold AND " +
           "tf1.createdAt >= :since AND tf2.createdAt >= :since")
    List<Object[]> findCorrelatedFeatures(@Param("correlationThreshold") double correlationThreshold,
                                        @Param("since") LocalDateTime since);

    /**
     * Archive old features beyond retention period
     */
    @Query("UPDATE TransactionFeature tf SET tf.archived = true " +
           "WHERE tf.createdAt < :archiveThreshold AND tf.labelAvailable = false")
    void archiveOldUnlabeledFeatures(@Param("archiveThreshold") LocalDateTime archiveThreshold);

    /**
     * Delete archived features
     */
    @Query("DELETE FROM TransactionFeature tf WHERE " +
           "tf.archived = true AND tf.createdAt < :deleteThreshold")
    void deleteArchivedFeatures(@Param("deleteThreshold") LocalDateTime deleteThreshold);

    /**
     * Find features used in active models
     */
    @Query("SELECT DISTINCT tf FROM TransactionFeature tf WHERE " +
           "tf.featureVersion IN :activeVersions AND " +
           "tf.computationStatus = 'COMPLETED'")
    List<TransactionFeature> findFeaturesInActiveModels(@Param("activeVersions") List<String> activeVersions);

    /**
     * Get feature quality metrics
     */
    @Query("SELECT " +
           "tf.featureVersion, " +
           "COUNT(CASE WHEN tf.computationStatus = 'COMPLETED' THEN 1 END) * 100.0 / COUNT(tf) as successRate, " +
           "AVG(tf.computationTimeMs) as avgComputationTime, " +
           "COUNT(CASE WHEN tf.errorMessage IS NOT NULL THEN 1 END) * 100.0 / COUNT(tf) as errorRate " +
           "FROM TransactionFeature tf " +
           "WHERE tf.createdAt >= :since " +
           "GROUP BY tf.featureVersion")
    List<Object[]> getFeatureQualityMetrics(@Param("since") LocalDateTime since);

    /**
     * Find features with specific error patterns
     */
    @Query("SELECT tf FROM TransactionFeature tf WHERE " +
           "tf.errorMessage IS NOT NULL AND " +
           "LOWER(tf.errorMessage) LIKE LOWER(CONCAT('%', :errorPattern, '%'))")
    List<TransactionFeature> findByErrorPattern(@Param("errorPattern") String errorPattern);

    /**
     * Count daily feature generation
     */
    @Query("SELECT DATE(tf.createdAt) as date, COUNT(tf) as count " +
           "FROM TransactionFeature tf " +
           "WHERE tf.createdAt >= :since " +
           "GROUP BY DATE(tf.createdAt) " +
           "ORDER BY date DESC")
    List<Object[]> countDailyFeatureGeneration(@Param("since") LocalDateTime since);
}