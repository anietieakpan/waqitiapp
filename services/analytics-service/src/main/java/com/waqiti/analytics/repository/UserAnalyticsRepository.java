package com.waqiti.analytics.repository;

import com.waqiti.analytics.entity.UserAnalytics;
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
 * User Analytics Repository
 *
 * Data access for user behavior and engagement analytics.
 *
 * @author Waqiti Platform Team
 * @version 1.0.0-PRODUCTION
 * @since 2025-11-10
 */
@Repository
public interface UserAnalyticsRepository extends JpaRepository<UserAnalytics, UUID> {

    /**
     * Find analytics by user and date
     */
    Optional<UserAnalytics> findByUserIdAndAnalysisDateAndPeriodType(
        UUID userId,
        LocalDateTime analysisDate,
        UserAnalytics.PeriodType periodType
    );

    /**
     * Find user analytics within date range
     */
    @Query("SELECT ua FROM UserAnalytics ua WHERE " +
           "ua.userId = :userId " +
           "AND ua.analysisDate BETWEEN :startDate AND :endDate " +
           "AND ua.periodType = :periodType " +
           "ORDER BY ua.analysisDate DESC")
    List<UserAnalytics> findByUserIdAndDateRange(
        @Param("userId") UUID userId,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate,
        @Param("periodType") UserAnalytics.PeriodType periodType
    );

    /**
     * Find high-risk users
     */
    @Query("SELECT ua FROM UserAnalytics ua WHERE " +
           "ua.riskScore >= :riskThreshold " +
           "AND ua.analysisDate BETWEEN :startDate AND :endDate " +
           "ORDER BY ua.riskScore DESC")
    Page<UserAnalytics> findHighRiskUsers(
        @Param("riskThreshold") java.math.BigDecimal riskThreshold,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate,
        Pageable pageable
    );

    /**
     * Find users at churn risk
     */
    @Query("SELECT ua FROM UserAnalytics ua WHERE " +
           "ua.churnProbability >= :churnThreshold " +
           "AND ua.analysisDate BETWEEN :startDate AND :endDate " +
           "ORDER BY ua.churnProbability DESC")
    Page<UserAnalytics> findChurnRiskUsers(
        @Param("churnThreshold") java.math.BigDecimal churnThreshold,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate,
        Pageable pageable
    );

    /**
     * Find most engaged users
     */
    @Query("SELECT ua FROM UserAnalytics ua WHERE " +
           "ua.analysisDate BETWEEN :startDate AND :endDate " +
           "AND ua.engagementScore IS NOT NULL " +
           "ORDER BY ua.engagementScore DESC")
    Page<UserAnalytics> findMostEngagedUsers(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate,
        Pageable pageable
    );
}
