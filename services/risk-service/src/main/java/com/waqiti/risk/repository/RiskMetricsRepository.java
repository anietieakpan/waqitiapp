package com.waqiti.risk.repository;

import com.waqiti.risk.model.RiskMetrics;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Risk Metrics Repository
 *
 * MongoDB repository for managing risk metrics and analytics
 *
 * @author Waqiti Engineering Team
 * @version 1.0.0
 */
@Repository
public interface RiskMetricsRepository extends MongoRepository<RiskMetrics, String> {

    /**
     * Find metrics by type
     */
    List<RiskMetrics> findByMetricType(String metricType);

    /**
     * Find metrics by entity type
     */
    List<RiskMetrics> findByEntityType(String entityType);

    /**
     * Find metrics for specific entity
     */
    List<RiskMetrics> findByEntityId(String entityId);

    /**
     * Find global metrics
     */
    @Query("{'entityType': 'GLOBAL'}")
    List<RiskMetrics> findGlobalMetrics();

    /**
     * Find user-specific metrics
     */
    @Query("{'entityType': 'USER', 'entityId': ?0}")
    List<RiskMetrics> findUserMetrics(String userId);

    /**
     * Find merchant-specific metrics
     */
    @Query("{'entityType': 'MERCHANT', 'entityId': ?0}")
    List<RiskMetrics> findMerchantMetrics(String merchantId);

    /**
     * Find metrics by period
     */
    @Query("{'periodStart': {'$gte': ?0}, 'periodEnd': {'$lte': ?1}}")
    List<RiskMetrics> findByPeriod(LocalDateTime start, LocalDateTime end);

    /**
     * Find metrics by type and period
     */
    @Query("{'metricType': ?0, 'periodStart': {'$gte': ?1}, 'periodEnd': {'$lte': ?2}}")
    List<RiskMetrics> findByTypeAndPeriod(String metricType, LocalDateTime start, LocalDateTime end);

    /**
     * Find latest metrics for entity
     */
    @Query(value = "{'entityType': ?0, 'entityId': ?1}", sort = "{'periodEnd': -1}")
    Optional<RiskMetrics> findLatestForEntity(String entityType, String entityId);

    /**
     * Find hourly metrics
     */
    @Query("{'metricType': 'HOURLY', 'periodStart': {'$gte': ?0}}")
    List<RiskMetrics> findHourlyMetrics(LocalDateTime since);

    /**
     * Find daily metrics
     */
    @Query("{'metricType': 'DAILY', 'periodStart': {'$gte': ?0}}")
    List<RiskMetrics> findDailyMetrics(LocalDateTime since);

    /**
     * Find weekly metrics
     */
    @Query("{'metricType': 'WEEKLY', 'periodStart': {'$gte': ?0}}")
    List<RiskMetrics> findWeeklyMetrics(LocalDateTime since);

    /**
     * Find monthly metrics
     */
    @Query("{'metricType': 'MONTHLY', 'periodStart': {'$gte': ?0}}")
    List<RiskMetrics> findMonthlyMetrics(LocalDateTime since);

    /**
     * Find metrics with high block rate
     */
    @Query("{'blockedCount': {'$gte': ?0}}")
    List<RiskMetrics> findHighBlockRateMetrics(Long minBlocked);

    /**
     * Find metrics with high average risk score
     */
    @Query("{'averageRiskScore': {'$gte': ?0}}")
    List<RiskMetrics> findHighRiskScoreMetrics(Double threshold);

    /**
     * Find metrics with high false positive rate
     */
    @Query("{'falsePositiveRate': {'$gte': ?0}}")
    List<RiskMetrics> findHighFalsePositiveMetrics(Double threshold);

    /**
     * Find metrics with ML predictions
     */
    @Query("{'mlPredictionsCount': {'$gt': 0}}")
    List<RiskMetrics> findMetricsWithMLPredictions();

    /**
     * Find metrics by ML model version
     */
    List<RiskMetrics> findByMlModelVersion(String modelVersion);

    /**
     * Find metrics with high error count
     */
    @Query("{'errorCount': {'$gte': ?0}}")
    List<RiskMetrics> findHighErrorMetrics(Long threshold);

    /**
     * Find metrics with circuit breaker activations
     */
    @Query("{'circuitBreakerActivations': {'$gt': 0}}")
    List<RiskMetrics> findMetricsWithCircuitBreakerActivations();

    /**
     * Find metrics with velocity violations
     */
    @Query("{'velocityViolations': {'$gte': ?0}}")
    List<RiskMetrics> findVelocityViolationMetrics(Long threshold);

    /**
     * Find metrics with VPN detections
     */
    @Query("{'vpnDetections': {'$gte': ?0}}")
    List<RiskMetrics> findVpnDetectionMetrics(Long threshold);

    /**
     * Find metrics with emulator detections
     */
    @Query("{'emulatorDetections': {'$gte': ?0}}")
    List<RiskMetrics> findEmulatorDetectionMetrics(Long threshold);

    /**
     * Find metrics with card testing detections
     */
    @Query("{'cardTestingDetections': {'$gte': ?0}}")
    List<RiskMetrics> findCardTestingMetrics(Long threshold);

    /**
     * Find metrics with confirmed frauds
     */
    @Query("{'confirmedFrauds': {'$gt': 0}}")
    List<RiskMetrics> findMetricsWithConfirmedFrauds();

    /**
     * Find recently aggregated metrics
     */
    @Query("{'lastAggregatedAt': {'$gte': ?0}}")
    List<RiskMetrics> findRecentlyAggregated(LocalDateTime since);

    /**
     * Find stale metrics (need re-aggregation)
     */
    @Query("{'lastAggregatedAt': {'$lt': ?0}}")
    List<RiskMetrics> findStaleMetrics(LocalDateTime before);

    /**
     * Find metrics created between dates
     */
    List<RiskMetrics> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    /**
     * Count metrics by type
     */
    long countByMetricType(String metricType);

    /**
     * Find metrics ordered by period (descending)
     */
    List<RiskMetrics> findByEntityTypeAndEntityIdOrderByPeriodEndDesc(String entityType, String entityId);
}
