package com.waqiti.frauddetection.repository;

import com.waqiti.frauddetection.model.ModelPerformance;
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
 * Repository for ModelPerformance entities with ML model tracking capabilities
 */
@Repository
public interface ModelPerformanceRepository extends JpaRepository<ModelPerformance, UUID> {

    /**
     * Find latest performance metrics for a model
     */
    Optional<ModelPerformance> findTopByModelNameOrderByEvaluatedAtDesc(String modelName);

    /**
     * Find performance metrics by model name and version
     */
    List<ModelPerformance> findByModelNameAndModelVersion(String modelName, String modelVersion);

    /**
     * Find performance metrics within time range
     */
    List<ModelPerformance> findByEvaluatedAtBetweenOrderByEvaluatedAtDesc(LocalDateTime startTime, LocalDateTime endTime);

    /**
     * Find models with performance above threshold
     */
    @Query("SELECT mp FROM ModelPerformance mp WHERE " +
           "mp.accuracy >= :accuracyThreshold AND " +
           "mp.f1Score >= :f1Threshold " +
           "ORDER BY mp.evaluatedAt DESC")
    List<ModelPerformance> findHighPerformingModels(@Param("accuracyThreshold") double accuracyThreshold,
                                                   @Param("f1Threshold") double f1Threshold);

    /**
     * Find models with declining performance
     */
    @Query("SELECT mp1 FROM ModelPerformance mp1 WHERE EXISTS (" +
           "SELECT mp2 FROM ModelPerformance mp2 WHERE " +
           "mp2.modelName = mp1.modelName AND " +
           "mp2.evaluatedAt > mp1.evaluatedAt AND " +
           "mp2.accuracy > mp1.accuracy + 0.05)")
    List<ModelPerformance> findDecliningModels();

    /**
     * Get performance history for a specific model
     */
    @Query("SELECT mp FROM ModelPerformance mp WHERE " +
           "mp.modelName = :modelName AND " +
           "mp.evaluatedAt >= :since " +
           "ORDER BY mp.evaluatedAt DESC")
    List<ModelPerformance> getModelPerformanceHistory(@Param("modelName") String modelName, 
                                                     @Param("since") LocalDateTime since);

    /**
     * Find best performing model at a given time
     */
    @Query("SELECT mp FROM ModelPerformance mp WHERE " +
           "mp.evaluatedAt = (SELECT MAX(mp2.evaluatedAt) FROM ModelPerformance mp2 WHERE mp2.evaluatedAt <= :timestamp) " +
           "ORDER BY mp.f1Score DESC")
    List<ModelPerformance> findBestModelAt(@Param("timestamp") LocalDateTime timestamp);

    /**
     * Get model comparison metrics
     */
    @Query("SELECT mp.modelName, AVG(mp.accuracy), AVG(mp.precision), AVG(mp.recall), AVG(mp.f1Score) " +
           "FROM ModelPerformance mp WHERE mp.evaluatedAt >= :since " +
           "GROUP BY mp.modelName " +
           "ORDER BY AVG(mp.f1Score) DESC")
    List<Object[]> getModelComparisonMetrics(@Param("since") LocalDateTime since);

    /**
     * Find models requiring retraining based on performance degradation
     */
    @Query("SELECT DISTINCT mp.modelName FROM ModelPerformance mp WHERE " +
           "mp.accuracy < :minAccuracy OR " +
           "mp.f1Score < :minF1Score OR " +
           "mp.evaluatedAt < :staleThreshold")
    List<String> findModelsRequiringRetraining(@Param("minAccuracy") double minAccuracy,
                                             @Param("minF1Score") double minF1Score,
                                             @Param("staleThreshold") LocalDateTime staleThreshold);

    /**
     * Get performance trends for dashboard
     */
    @Query("SELECT DATE(mp.evaluatedAt) as evalDate, " +
           "mp.modelName, " +
           "AVG(mp.accuracy) as avgAccuracy, " +
           "AVG(mp.f1Score) as avgF1Score " +
           "FROM ModelPerformance mp WHERE mp.evaluatedAt >= :since " +
           "GROUP BY DATE(mp.evaluatedAt), mp.modelName " +
           "ORDER BY evalDate DESC")
    List<Object[]> getPerformanceTrends(@Param("since") LocalDateTime since);

    /**
     * Find models with consistent performance
     */
    @Query("SELECT mp.modelName, " +
           "STDDEV(mp.accuracy) as accuracyStdDev, " +
           "STDDEV(mp.f1Score) as f1StdDev " +
           "FROM ModelPerformance mp WHERE mp.evaluatedAt >= :since " +
           "GROUP BY mp.modelName " +
           "HAVING STDDEV(mp.accuracy) < :maxStdDev AND STDDEV(mp.f1Score) < :maxStdDev")
    List<Object[]> findConsistentModels(@Param("since") LocalDateTime since, @Param("maxStdDev") double maxStdDev);

    /**
     * Get latest metrics for all active models
     */
    @Query("SELECT mp FROM ModelPerformance mp WHERE mp.id IN (" +
           "SELECT MAX(mp2.id) FROM ModelPerformance mp2 " +
           "WHERE mp2.evaluatedAt >= :recentThreshold " +
           "GROUP BY mp2.modelName)")
    List<ModelPerformance> getLatestMetricsForAllModels(@Param("recentThreshold") LocalDateTime recentThreshold);

    /**
     * Count evaluations by model and time period
     */
    @Query("SELECT mp.modelName, COUNT(mp) FROM ModelPerformance mp " +
           "WHERE mp.evaluatedAt >= :since " +
           "GROUP BY mp.modelName")
    List<Object[]> countEvaluationsByModel(@Param("since") LocalDateTime since);

    /**
     * Find performance outliers (unusually good or bad)
     */
    @Query("SELECT mp FROM ModelPerformance mp WHERE " +
           "ABS(mp.accuracy - (SELECT AVG(mp2.accuracy) FROM ModelPerformance mp2 WHERE mp2.modelName = mp.modelName)) > :threshold")
    List<ModelPerformance> findPerformanceOutliers(@Param("threshold") double threshold);

    /**
     * Get model performance statistics
     */
    @Query("SELECT " +
           "COUNT(DISTINCT mp.modelName) as uniqueModels, " +
           "COUNT(mp) as totalEvaluations, " +
           "AVG(mp.accuracy) as overallAccuracy, " +
           "AVG(mp.f1Score) as overallF1Score, " +
           "MAX(mp.accuracy) as bestAccuracy, " +
           "MIN(mp.accuracy) as worstAccuracy " +
           "FROM ModelPerformance mp WHERE mp.evaluatedAt >= :since")
    Object[] getOverallStatistics(@Param("since") LocalDateTime since);

    /**
     * Find models with improving performance trend
     */
    @Query("SELECT mp.modelName FROM ModelPerformance mp " +
           "WHERE mp.evaluatedAt >= :since " +
           "GROUP BY mp.modelName " +
           "HAVING COUNT(mp) >= 3 AND " +
           "CORR(CAST(mp.evaluatedAt AS long), mp.f1Score) > 0.5")
    List<String> findImprovingModels(@Param("since") LocalDateTime since);

    /**
     * Search performance records with filters
     */
    @Query("SELECT mp FROM ModelPerformance mp WHERE " +
           "(:modelName IS NULL OR mp.modelName = :modelName) AND " +
           "(:minAccuracy IS NULL OR mp.accuracy >= :minAccuracy) AND " +
           "(:minF1Score IS NULL OR mp.f1Score >= :minF1Score) AND " +
           "mp.evaluatedAt BETWEEN :startTime AND :endTime " +
           "ORDER BY mp.evaluatedAt DESC")
    Page<ModelPerformance> searchPerformanceRecords(
        @Param("modelName") String modelName,
        @Param("minAccuracy") Double minAccuracy,
        @Param("minF1Score") Double minF1Score,
        @Param("startTime") LocalDateTime startTime,
        @Param("endTime") LocalDateTime endTime,
        Pageable pageable
    );

    /**
     * Calculate model stability score
     */
    @Query("SELECT mp.modelName, " +
           "1 - (STDDEV(mp.accuracy) / AVG(mp.accuracy)) as stabilityScore " +
           "FROM ModelPerformance mp " +
           "WHERE mp.evaluatedAt >= :since AND AVG(mp.accuracy) > 0 " +
           "GROUP BY mp.modelName " +
           "ORDER BY stabilityScore DESC")
    List<Object[]> calculateModelStabilityScores(@Param("since") LocalDateTime since);

    /**
     * Find models evaluated on specific dataset size
     */
    List<ModelPerformance> findByTestSampleSizeGreaterThan(int minSamples);

    /**
     * Get confusion matrix statistics
     */
    @Query("SELECT mp.modelName, " +
           "AVG(mp.truePositives) as avgTp, " +
           "AVG(mp.falsePositives) as avgFp, " +
           "AVG(mp.trueNegatives) as avgTn, " +
           "AVG(mp.falseNegatives) as avgFn " +
           "FROM ModelPerformance mp WHERE mp.evaluatedAt >= :since " +
           "GROUP BY mp.modelName")
    List<Object[]> getConfusionMatrixStatistics(@Param("since") LocalDateTime since);

    /**
     * Delete old performance records for cleanup
     */
    @Query("DELETE FROM ModelPerformance mp WHERE mp.evaluatedAt < :retentionThreshold")
    void deleteOldPerformanceRecords(@Param("retentionThreshold") LocalDateTime retentionThreshold);

    /**
     * Update model status based on performance
     */
    @Query("UPDATE ModelPerformance mp SET mp.status = :status " +
           "WHERE mp.accuracy < :minAccuracy OR mp.f1Score < :minF1Score")
    void updateUnderperformingModelStatus(@Param("status") String status,
                                        @Param("minAccuracy") double minAccuracy,
                                        @Param("minF1Score") double minF1Score);

    /**
     * Get champion/challenger model comparison
     */
    @Query("SELECT mp FROM ModelPerformance mp WHERE " +
           "mp.modelName IN :modelNames AND " +
           "mp.evaluatedAt = (SELECT MAX(mp2.evaluatedAt) FROM ModelPerformance mp2 WHERE mp2.modelName = mp.modelName) " +
           "ORDER BY mp.f1Score DESC")
    List<ModelPerformance> getChampionChallengerComparison(@Param("modelNames") List<String> modelNames);
}