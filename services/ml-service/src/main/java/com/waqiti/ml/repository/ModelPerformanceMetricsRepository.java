package com.waqiti.ml.repository;

import com.waqiti.ml.entity.ModelPerformanceMetrics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for ModelPerformanceMetrics entity
 */
@Repository
public interface ModelPerformanceMetricsRepository extends JpaRepository<ModelPerformanceMetrics, UUID> {

    /**
     * Find the latest metrics for a specific model and version
     */
    Optional<ModelPerformanceMetrics> findTopByModelNameAndModelVersionOrderByCreatedAtDesc(
        String modelName, String modelVersion);

    /**
     * Find all metrics for a model within a date range
     */
    List<ModelPerformanceMetrics> findByModelNameAndModelVersionAndCreatedAtBetweenOrderByCreatedAt(
        String modelName, String modelVersion, LocalDateTime startDate, LocalDateTime endDate);

    /**
     * Find all metrics for a model
     */
    List<ModelPerformanceMetrics> findByModelNameAndModelVersionOrderByCreatedAtDesc(
        String modelName, String modelVersion);

    /**
     * Find metrics with low accuracy (below threshold)
     */
    @Query("SELECT m FROM ModelPerformanceMetrics m WHERE m.accuracy < :threshold ORDER BY m.createdAt DESC")
    List<ModelPerformanceMetrics> findByAccuracyLessThan(@Param("threshold") Double threshold);

    /**
     * Find metrics with high error rate (above threshold)
     */
    @Query("SELECT m FROM ModelPerformanceMetrics m WHERE (m.errorCount * 1.0 / m.predictionCount) > :threshold ORDER BY m.createdAt DESC")
    List<ModelPerformanceMetrics> findByErrorRateGreaterThan(@Param("threshold") Double threshold);

    /**
     * Find metrics with high drift score
     */
    @Query("SELECT m FROM ModelPerformanceMetrics m WHERE m.driftScore > :threshold ORDER BY m.createdAt DESC")
    List<ModelPerformanceMetrics> findByDriftScoreGreaterThan(@Param("threshold") Double threshold);

    /**
     * Find all unique model names
     */
    @Query("SELECT DISTINCT m.modelName FROM ModelPerformanceMetrics m")
    List<String> findDistinctModelNames();

    /**
     * Find all versions for a model
     */
    @Query("SELECT DISTINCT m.modelVersion FROM ModelPerformanceMetrics m WHERE m.modelName = :modelName")
    List<String> findDistinctVersionsByModelName(@Param("modelName") String modelName);

    /**
     * Get latest metrics for all models
     */
    @Query("SELECT m FROM ModelPerformanceMetrics m WHERE m.id IN " +
           "(SELECT m2.id FROM ModelPerformanceMetrics m2 WHERE m2.modelName = m.modelName AND m2.modelVersion = m.modelVersion " +
           "ORDER BY m2.createdAt DESC LIMIT 1)")
    List<ModelPerformanceMetrics> findLatestMetricsForAllModels();

    /**
     * Delete old metrics (older than specified date)
     */
    void deleteByCreatedAtBefore(LocalDateTime cutoffDate);

    /**
     * Count total predictions across all models
     */
    @Query("SELECT SUM(m.predictionCount) FROM ModelPerformanceMetrics m")
    Long getTotalPredictionCount();

    /**
     * Get average accuracy across all models
     */
    @Query("SELECT AVG(m.accuracy) FROM ModelPerformanceMetrics m WHERE m.accuracy IS NOT NULL")
    Double getAverageAccuracy();
}