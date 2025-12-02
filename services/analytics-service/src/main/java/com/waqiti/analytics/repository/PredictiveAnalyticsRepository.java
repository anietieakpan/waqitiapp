package com.waqiti.analytics.repository;

import com.waqiti.analytics.entity.PredictiveAnalytics;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Predictive analytics repository with forecasting and ML model queries
 */
@Repository
public interface PredictiveAnalyticsRepository extends JpaRepository<PredictiveAnalytics, UUID> {
    
    /**
     * Find predictions by user
     */
    Page<PredictiveAnalytics> findByUserId(UUID userId, Pageable pageable);
    
    /**
     * Find active predictions for user
     */
    List<PredictiveAnalytics> findByUserIdAndStatusAndTargetDateAfter(
        UUID userId, PredictiveAnalytics.PredictionStatus status, LocalDateTime afterDate);
    
    /**
     * Find predictions by type and category
     */
    List<PredictiveAnalytics> findByUserIdAndPredictionTypeAndPredictionCategory(
        UUID userId, PredictiveAnalytics.PredictionType type, String category);
    
    /**
     * Find predictions requiring validation
     */
    @Query("SELECT pa FROM PredictiveAnalytics pa " +
           "WHERE pa.status = 'PENDING' " +
           "AND pa.targetDate <= :currentDate " +
           "ORDER BY pa.targetDate ASC")
    List<PredictiveAnalytics> findPredictionsRequiringValidation(
        @Param("currentDate") LocalDateTime currentDate);
    
    /**
     * Calculate model accuracy
     */
    @Query("SELECT pa.modelType, " +
           "COUNT(pa), " +
           "AVG(pa.errorPercentage), " +
           "MIN(pa.errorPercentage), " +
           "MAX(pa.errorPercentage) " +
           "FROM PredictiveAnalytics pa " +
           "WHERE pa.status IN ('ACCURATE', 'INACCURATE') " +
           "AND pa.modelType = :modelType " +
           "GROUP BY pa.modelType")
    Optional<Object[]> calculateModelAccuracy(
        @Param("modelType") PredictiveAnalytics.ModelType modelType);
    
    /**
     * Find high confidence predictions
     */
    List<PredictiveAnalytics> findByUserIdAndConfidenceLevelGreaterThanAndStatus(
        UUID userId, Double minConfidence, PredictiveAnalytics.PredictionStatus status);
    
    /**
     * Get spending forecasts
     */
    @Query("SELECT pa FROM PredictiveAnalytics pa " +
           "WHERE pa.userId = :userId " +
           "AND pa.predictionType = 'SPENDING_FORECAST' " +
           "AND pa.targetDate BETWEEN :startDate AND :endDate " +
           "ORDER BY pa.targetDate ASC")
    List<PredictiveAnalytics> getSpendingForecasts(
        @Param("userId") UUID userId,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate);
    
    /**
     * Find fraud predictions
     */
    @Query("SELECT pa FROM PredictiveAnalytics pa " +
           "WHERE pa.userId = :userId " +
           "AND pa.predictionType = 'FRAUD_PROBABILITY' " +
           "AND pa.riskScore > :riskThreshold " +
           "AND pa.status = 'PENDING'")
    List<PredictiveAnalytics> findHighRiskFraudPredictions(
        @Param("userId") UUID userId,
        @Param("riskThreshold") Double riskThreshold);
    
    /**
     * Get cashflow projections
     */
    @Query("SELECT pa FROM PredictiveAnalytics pa " +
           "WHERE pa.userId = :userId " +
           "AND pa.predictionType = 'CASHFLOW_PROJECTION' " +
           "AND pa.targetDate >= :currentDate " +
           "ORDER BY pa.targetDate ASC")
    List<PredictiveAnalytics> getCashflowProjections(
        @Param("userId") UUID userId,
        @Param("currentDate") LocalDateTime currentDate);
    
    /**
     * Find churn risk predictions
     */
    List<PredictiveAnalytics> findByPredictionTypeAndRiskScoreGreaterThanAndStatus(
        PredictiveAnalytics.PredictionType predictionType,
        Double riskScore,
        PredictiveAnalytics.PredictionStatus status);
    
    /**
     * Update prediction with actual value
     */
    @Modifying
    @Query("UPDATE PredictiveAnalytics pa SET " +
           "pa.actualValue = :actualValue, " +
           "pa.predictionError = :predictionError, " +
           "pa.errorPercentage = :errorPercentage, " +
           "pa.status = :status " +
           "WHERE pa.id = :predictionId")
    void updatePredictionWithActual(
        @Param("predictionId") UUID predictionId,
        @Param("actualValue") BigDecimal actualValue,
        @Param("predictionError") BigDecimal predictionError,
        @Param("errorPercentage") Double errorPercentage,
        @Param("status") PredictiveAnalytics.PredictionStatus status);
    
    /**
     * Get prediction performance by category
     */
    @Query("SELECT pa.predictionCategory, " +
           "COUNT(pa), " +
           "AVG(pa.accuracy), " +
           "AVG(pa.confidenceLevel) " +
           "FROM PredictiveAnalytics pa " +
           "WHERE pa.userId = :userId " +
           "AND pa.status IN ('ACCURATE', 'INACCURATE') " +
           "GROUP BY pa.predictionCategory")
    List<Object[]> getPredictionPerformanceByCategory(@Param("userId") UUID userId);
    
    /**
     * Find predictions with user feedback
     */
    Page<PredictiveAnalytics> findByUserFeedbackNotNullOrderByCreatedAtDesc(Pageable pageable);
    
    /**
     * Get investment return predictions
     */
    List<PredictiveAnalytics> findByUserIdAndPredictionTypeAndTargetDateBetween(
        UUID userId,
        PredictiveAnalytics.PredictionType predictionType,
        LocalDateTime startDate,
        LocalDateTime endDate);
    
    /**
     * Delete old predictions
     */
    void deleteByTargetDateBeforeAndStatusIn(
        LocalDateTime cutoffDate,
        List<PredictiveAnalytics.PredictionStatus> statuses);
}