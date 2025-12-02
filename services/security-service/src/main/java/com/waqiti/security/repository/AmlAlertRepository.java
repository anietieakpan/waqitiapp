package com.waqiti.security.repository;

import com.waqiti.security.domain.AmlAlert;
import com.waqiti.security.domain.AmlAlertType;
import com.waqiti.security.domain.AmlSeverity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Repository for AML Alert entities
 */
@Repository
public interface AmlAlertRepository extends JpaRepository<AmlAlert, UUID> {

    /**
     * Find alerts by status
     */
    List<AmlAlert> findByStatusOrderByCreatedAtDesc(AmlAlert.AlertStatus status);

    /**
     * Find alerts by user ID
     */
    List<AmlAlert> findByUserIdOrderByCreatedAtDesc(UUID userId);

    /**
     * Find alerts by transaction ID
     */
    List<AmlAlert> findByTransactionId(UUID transactionId);

    /**
     * Find alerts by severity
     */
    List<AmlAlert> findBySeverityOrderByCreatedAtDesc(AmlSeverity severity);

    /**
     * Find alerts by alert type
     */
    List<AmlAlert> findByAlertTypeOrderByCreatedAtDesc(AmlAlertType alertType);

    /**
     * Find open alerts requiring investigation
     */
    @Query("SELECT a FROM AmlAlert a WHERE a.status = 'OPEN' AND a.requiresInvestigation = true ORDER BY a.severity DESC, a.createdAt ASC")
    List<AmlAlert> findOpenAlertsRequiringInvestigation();

    /**
     * Find alerts by date range
     */
    List<AmlAlert> findByCreatedAtBetweenOrderByCreatedAtDesc(LocalDateTime startDate, LocalDateTime endDate);

    /**
     * Find alerts by user and date range
     */
    List<AmlAlert> findByUserIdAndCreatedAtBetweenOrderByCreatedAtDesc(
        UUID userId, LocalDateTime startDate, LocalDateTime endDate);

    /**
     * Find high severity unresolved alerts
     */
    @Query("SELECT a FROM AmlAlert a WHERE a.severity IN ('HIGH', 'CRITICAL') AND a.status NOT IN ('RESOLVED', 'CLOSED') ORDER BY a.createdAt ASC")
    List<AmlAlert> findHighSeverityUnresolvedAlerts();

    /**
     * Count alerts by status
     */
    long countByStatus(AmlAlert.AlertStatus status);

    /**
     * Count alerts by severity and date range
     */
    long countBySeverityAndCreatedAtBetween(AmlSeverity severity, LocalDateTime startDate, LocalDateTime endDate);

    /**
     * Find alerts escalated to specific user
     */
    List<AmlAlert> findByEscalatedToOrderByEscalatedAtDesc(UUID escalatedTo);

    /**
     * Find alerts resolved by specific user
     */
    List<AmlAlert> findByResolvedByOrderByResolvedAtDesc(UUID resolvedBy);

    /**
     * Find suspicious activity confirmed alerts
     */
    List<AmlAlert> findBySuspiciousActivityTrueOrderByResolvedAtDesc();

    /**
     * Get alert statistics by type for reporting
     */
    @Query("SELECT a.alertType, COUNT(a) FROM AmlAlert a WHERE a.createdAt BETWEEN :startDate AND :endDate GROUP BY a.alertType")
    List<Object[]> getAlertStatisticsByType(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    /**
     * Get alert statistics by severity for reporting
     */
    @Query("SELECT a.severity, COUNT(a) FROM AmlAlert a WHERE a.createdAt BETWEEN :startDate AND :endDate GROUP BY a.severity")
    List<Object[]> getAlertStatisticsBySeverity(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    /**
     * Find alerts requiring regulatory reporting
     */
    @Query("SELECT a FROM AmlAlert a WHERE a.regulatoryReported IS NULL OR a.regulatoryReported = false AND a.severity IN ('HIGH', 'CRITICAL')")
    List<AmlAlert> findAlertsRequiringRegulatoryReporting();

    /**
     * Find overdue alerts (not resolved within SLA)
     */
    @Query("SELECT a FROM AmlAlert a WHERE a.status = 'OPEN' AND a.createdAt < :slaThreshold")
    List<AmlAlert> findOverdueAlerts(@Param("slaThreshold") LocalDateTime slaThreshold);

    /**
     * Get user risk profile based on alert history
     */
    @Query("SELECT COUNT(a), AVG(a.riskScore), MAX(a.severity) FROM AmlAlert a WHERE a.userId = :userId AND a.createdAt >= :sinceDate")
    Object[] getUserRiskProfile(@Param("userId") UUID userId, @Param("sinceDate") LocalDateTime sinceDate);

    /**
     * Find users with multiple alerts in time period
     */
    @Query("SELECT a.userId, COUNT(a) as alertCount FROM AmlAlert a WHERE a.createdAt BETWEEN :startDate AND :endDate GROUP BY a.userId HAVING COUNT(a) >= :minAlerts ORDER BY alertCount DESC")
    List<Object[]> findUsersWithMultipleAlerts(@Param("startDate") LocalDateTime startDate, 
                                              @Param("endDate") LocalDateTime endDate, 
                                              @Param("minAlerts") long minAlerts);

    /**
     * Get monthly alert trends
     */
    @Query("SELECT YEAR(a.createdAt), MONTH(a.createdAt), COUNT(a) FROM AmlAlert a WHERE a.createdAt >= :sinceDate GROUP BY YEAR(a.createdAt), MONTH(a.createdAt) ORDER BY YEAR(a.createdAt), MONTH(a.createdAt)")
    List<Object[]> getMonthlyAlertTrends(@Param("sinceDate") LocalDateTime sinceDate);

    /**
     * Check if alert exists for transaction
     */
    boolean existsByTransactionId(UUID transactionId);

    /**
     * Find alerts with pagination
     */
    Page<AmlAlert> findByStatusOrderByCreatedAtDesc(AmlAlert.AlertStatus status, Pageable pageable);

    /**
     * Find alerts by multiple criteria with pagination
     */
    @Query("SELECT a FROM AmlAlert a WHERE " +
           "(:status IS NULL OR a.status = :status) AND " +
           "(:severity IS NULL OR a.severity = :severity) AND " +
           "(:alertType IS NULL OR a.alertType = :alertType) AND " +
           "(:userId IS NULL OR a.userId = :userId) AND " +
           "a.createdAt BETWEEN :startDate AND :endDate " +
           "ORDER BY a.createdAt DESC")
    Page<AmlAlert> findAlertsByCriteria(@Param("status") AmlAlert.AlertStatus status,
                                       @Param("severity") AmlSeverity severity,
                                       @Param("alertType") AmlAlertType alertType,
                                       @Param("userId") UUID userId,
                                       @Param("startDate") LocalDateTime startDate,
                                       @Param("endDate") LocalDateTime endDate,
                                       Pageable pageable);

    /**
     * Delete old alerts (for data retention)
     */
    void deleteByCreatedAtBefore(LocalDateTime cutoffDate);
}