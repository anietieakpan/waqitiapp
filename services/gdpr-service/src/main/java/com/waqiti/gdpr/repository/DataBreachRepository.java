package com.waqiti.gdpr.repository;

import com.waqiti.gdpr.domain.BreachSeverity;
import com.waqiti.gdpr.domain.BreachStatus;
import com.waqiti.gdpr.domain.DataBreach;
import com.waqiti.gdpr.domain.RiskLevel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for DataBreach entities
 * Supports GDPR Articles 33-34 breach notification requirements
 */
@Repository
public interface DataBreachRepository extends JpaRepository<DataBreach, String> {

    /**
     * Find breaches by status
     */
    List<DataBreach> findByStatus(BreachStatus status);

    /**
     * Find breaches by severity
     */
    List<DataBreach> findBySeverity(BreachSeverity severity);

    /**
     * Find breaches requiring regulatory notification
     */
    List<DataBreach> findByRequiresRegulatoryNotificationTrueAndRegulatoryNotifiedAtIsNull();

    /**
     * Find breaches with regulatory notification deadline approaching or breached
     */
    @Query("SELECT b FROM DataBreach b WHERE b.requiresRegulatoryNotification = true " +
           "AND b.regulatoryNotifiedAt IS NULL " +
           "AND b.regulatoryNotificationDeadline <= :deadline")
    List<DataBreach> findRegulatoryNotificationsDue(@Param("deadline") LocalDateTime deadline);

    /**
     * Find breaches requiring user notification
     */
    List<DataBreach> findByRequiresUserNotificationTrueAndUsersNotifiedAtIsNull();

    /**
     * Find breaches with user notification deadline approaching or breached
     */
    @Query("SELECT b FROM DataBreach b WHERE b.requiresUserNotification = true " +
           "AND b.usersNotifiedAt IS NULL " +
           "AND b.userNotificationDeadline <= :deadline")
    List<DataBreach> findUserNotificationsDue(@Param("deadline") LocalDateTime deadline);

    /**
     * Find active breaches (not resolved or closed)
     */
    @Query("SELECT b FROM DataBreach b WHERE b.status NOT IN ('RESOLVED', 'CLOSED', 'FALSE_POSITIVE')")
    List<DataBreach> findActiveBreaches();

    /**
     * Find breaches by risk level
     */
    @Query("SELECT b FROM DataBreach b WHERE b.riskAssessment.riskLevel = :riskLevel")
    List<DataBreach> findByRiskLevel(@Param("riskLevel") RiskLevel riskLevel);

    /**
     * Find breaches discovered in date range
     */
    List<DataBreach> findByDiscoveredAtBetween(LocalDateTime start, LocalDateTime end);

    /**
     * Find breaches affecting specific user count threshold
     */
    List<DataBreach> findByAffectedUserCountGreaterThan(Integer threshold);

    /**
     * Count active breaches
     */
    @Query("SELECT COUNT(b) FROM DataBreach b WHERE b.status NOT IN ('RESOLVED', 'CLOSED', 'FALSE_POSITIVE')")
    long countActiveBreaches();

    /**
     * Count breaches by severity
     */
    long countBySeverity(BreachSeverity severity);

    /**
     * Find breaches without DPO notification
     */
    @Query("SELECT b FROM DataBreach b WHERE b.dpoNotifiedAt IS NULL " +
           "AND b.status != 'FALSE_POSITIVE' " +
           "AND b.discoveredAt < :threshold")
    List<DataBreach> findBreachesWithoutDpoNotification(@Param("threshold") LocalDateTime threshold);

    /**
     * Find breaches by investigation status
     */
    List<DataBreach> findByInvestigationStatus(String investigationStatus);

    /**
     * Find recent breaches (last N days)
     */
    @Query("SELECT b FROM DataBreach b WHERE b.discoveredAt >= :since ORDER BY b.discoveredAt DESC")
    List<DataBreach> findRecentBreaches(@Param("since") LocalDateTime since);

    /**
     * Find critical/high severity active breaches
     */
    @Query("SELECT b FROM DataBreach b WHERE b.severity IN ('CRITICAL', 'HIGH') " +
           "AND b.status NOT IN ('RESOLVED', 'CLOSED', 'FALSE_POSITIVE')")
    List<DataBreach> findCriticalActiveBreaches();
}
