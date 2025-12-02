package com.waqiti.discovery.repository;

import com.waqiti.discovery.domain.DiscoveryAnalytics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for DiscoveryAnalytics entity
 *
 * @author Discovery Service Team
 * @since 1.0.0
 */
@Repository
public interface DiscoveryAnalyticsRepository extends JpaRepository<DiscoveryAnalytics, UUID> {

    Optional<DiscoveryAnalytics> findByAnalyticsId(String analyticsId);

    List<DiscoveryAnalytics> findByPeriodEndAfter(Instant periodEnd);

    @Query("SELECT a FROM DiscoveryAnalytics a WHERE a.periodStart >= :startTime " +
        "AND a.periodEnd <= :endTime ORDER BY a.periodEnd DESC")
    List<DiscoveryAnalytics> findAnalyticsInTimeRange(
        @Param("startTime") Instant startTime,
        @Param("endTime") Instant endTime
    );

    @Query("SELECT a FROM DiscoveryAnalytics a ORDER BY a.periodEnd DESC")
    List<DiscoveryAnalytics> findAllOrderByPeriodEndDesc();

    Optional<DiscoveryAnalytics> findFirstByOrderByPeriodEndDesc();
}
