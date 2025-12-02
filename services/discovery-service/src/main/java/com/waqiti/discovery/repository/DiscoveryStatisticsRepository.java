package com.waqiti.discovery.repository;

import com.waqiti.discovery.domain.DiscoveryStatistics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for DiscoveryStatistics entity
 *
 * @author Discovery Service Team
 * @since 1.0.0
 */
@Repository
public interface DiscoveryStatisticsRepository extends JpaRepository<DiscoveryStatistics, UUID> {

    Optional<DiscoveryStatistics> findByPeriodStartAndPeriodEnd(LocalDate periodStart, LocalDate periodEnd);

    List<DiscoveryStatistics> findByPeriodEndAfter(LocalDate periodEnd);

    @Query("SELECT s FROM DiscoveryStatistics s WHERE s.periodStart >= :startDate " +
        "AND s.periodEnd <= :endDate ORDER BY s.periodEnd DESC")
    List<DiscoveryStatistics> findStatisticsInDateRange(
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    @Query("SELECT s FROM DiscoveryStatistics s ORDER BY s.periodEnd DESC")
    List<DiscoveryStatistics> findAllOrderByPeriodEndDesc();

    Optional<DiscoveryStatistics> findFirstByOrderByPeriodEndDesc();
}
