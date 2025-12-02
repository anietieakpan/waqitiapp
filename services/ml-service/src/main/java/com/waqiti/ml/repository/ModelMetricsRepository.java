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
 * Repository for ML model performance metrics
 */
@Repository
public interface ModelMetricsRepository extends JpaRepository<ModelPerformanceMetrics, UUID> {

    /**
     * Find latest metrics for a model
     */
    Optional<ModelPerformanceMetrics> findTopByModelNameOrderByCreatedAtDesc(String modelName);

    /**
     * Find metrics for a specific model and version
     */
    Optional<ModelPerformanceMetrics> findByModelNameAndModelVersion(String modelName, String modelVersion);

    /**
     * Find all metrics for a model ordered by creation date
     */
    List<ModelPerformanceMetrics> findByModelNameOrderByCreatedAtDesc(String modelName);

    /**
     * Find metrics within date range
     */
    @Query("SELECT m FROM ModelPerformanceMetrics m WHERE m.createdAt BETWEEN :startDate AND :endDate ORDER BY m.createdAt DESC")
    List<ModelPerformanceMetrics> findByDateRange(@Param("startDate") LocalDateTime startDate, 
                                                   @Param("endDate") LocalDateTime endDate);

    /**
     * Find models with poor performance
     */
    @Query("SELECT m FROM ModelPerformanceMetrics m WHERE m.accuracy < :accuracyThreshold OR (m.predictionCount > 0 AND m.errorCount * 1.0 / m.predictionCount > :errorRateThreshold)")
    List<ModelPerformanceMetrics> findPoorPerformingModels(@Param("accuracyThreshold") Double accuracyThreshold,
                                                           @Param("errorRateThreshold") Double errorRateThreshold);

    /**
     * Delete old metrics (cleanup)
     */
    void deleteByCreatedAtBefore(LocalDateTime cutoffDate);
}