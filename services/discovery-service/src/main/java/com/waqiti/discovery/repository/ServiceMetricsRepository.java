package com.waqiti.discovery.repository;

import com.waqiti.discovery.domain.ServiceMetrics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for ServiceMetrics entity
 *
 * @author Discovery Service Team
 * @since 1.0.0
 */
@Repository
public interface ServiceMetricsRepository extends JpaRepository<ServiceMetrics, UUID> {

    /**
     * Find metrics by metric ID
     *
     * @param metricId metric ID
     * @return optional service metrics
     */
    Optional<ServiceMetrics> findByMetricId(String metricId);

    /**
     * Find all metrics by service ID
     *
     * @param serviceId service ID
     * @return list of service metrics
     */
    List<ServiceMetrics> findByServiceId(String serviceId);

    /**
     * Find all metrics by instance ID
     *
     * @param instanceId instance ID
     * @return list of service metrics
     */
    List<ServiceMetrics> findByInstanceId(String instanceId);

    /**
     * Find latest metrics by service ID
     *
     * @param serviceId service ID
     * @return optional service metrics
     */
    Optional<ServiceMetrics> findFirstByServiceIdOrderByTimestampDesc(String serviceId);

    /**
     * Find metrics after timestamp
     *
     * @param serviceId service ID
     * @param timestamp start timestamp
     * @return list of service metrics
     */
    List<ServiceMetrics> findByServiceIdAndTimestampAfter(String serviceId, Instant timestamp);

    /**
     * Find metrics by service ID and metric name
     *
     * @param serviceId service ID
     * @param metricName metric name
     * @return list of service metrics
     */
    List<ServiceMetrics> findByServiceIdAndMetricName(String serviceId, String metricName);

    /**
     * Find metrics in time range
     *
     * @param serviceId service ID
     * @param startTime start time
     * @param endTime end time
     * @return list of service metrics
     */
    @Query("SELECT m FROM ServiceMetrics m WHERE m.serviceId = :serviceId " +
        "AND m.timestamp BETWEEN :startTime AND :endTime ORDER BY m.timestamp ASC")
    List<ServiceMetrics> findMetricsInTimeRange(
        @Param("serviceId") String serviceId,
        @Param("startTime") Instant startTime,
        @Param("endTime") Instant endTime
    );

    /**
     * Delete old metrics
     *
     * @param cutoffTime cutoff time
     * @return number of deleted records
     */
    @Modifying
    @Query("DELETE FROM ServiceMetrics m WHERE m.timestamp < :cutoffTime")
    int deleteByTimestampBefore(@Param("cutoffTime") Instant cutoffTime);

    /**
     * Calculate average response time for service
     *
     * @param serviceId service ID
     * @param startTime start time
     * @return average response time
     */
    @Query("SELECT AVG(m.averageResponseTime) FROM ServiceMetrics m " +
        "WHERE m.serviceId = :serviceId AND m.timestamp >= :startTime")
    Double calculateAverageResponseTime(
        @Param("serviceId") String serviceId,
        @Param("startTime") Instant startTime
    );

    /**
     * Find services with high error rate
     *
     * @param errorRateThreshold error rate threshold
     * @param startTime start time
     * @return list of service IDs
     */
    @Query("SELECT DISTINCT m.serviceId FROM ServiceMetrics m " +
        "WHERE m.timestamp >= :startTime " +
        "AND (CAST(m.errorCount AS double) / CAST(m.requestCount AS double) * 100) > :threshold")
    List<String> findServicesWithHighErrorRate(
        @Param("threshold") double errorRateThreshold,
        @Param("startTime") Instant startTime
    );
}
