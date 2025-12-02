package com.waqiti.security.repository;

import com.waqiti.security.domain.RegulatoryReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Repository for Regulatory Report entities
 */
@Repository
public interface RegulatoryReportRepository extends JpaRepository<RegulatoryReport, UUID> {

    /**
     * Find reports by type
     */
    List<RegulatoryReport> findByReportTypeOrderByGeneratedAtDesc(RegulatoryReport.ReportType reportType);

    /**
     * Find reports by status
     */
    List<RegulatoryReport> findByStatusOrderByGeneratedAtDesc(RegulatoryReport.ReportStatus status);

    /**
     * Find reports by date range
     */
    List<RegulatoryReport> findByGeneratedAtBetweenOrderByGeneratedAtDesc(LocalDateTime startDate, LocalDateTime endDate);

    /**
     * Find reports by reporting period
     */
    List<RegulatoryReport> findByReportingPeriodOrderByGeneratedAtDesc(LocalDateTime reportingPeriod);

    /**
     * Find reports by alert ID
     */
    List<RegulatoryReport> findByAlertId(UUID alertId);

    /**
     * Find reports by user ID
     */
    List<RegulatoryReport> findByUserIdOrderByGeneratedAtDesc(UUID userId);

    /**
     * Find reports by compliance officer
     */
    List<RegulatoryReport> findByComplianceOfficerIdOrderByGeneratedAtDesc(UUID complianceOfficerId);

    /**
     * Find reports by jurisdiction
     */
    List<RegulatoryReport> findByJurisdictionCodeOrderByGeneratedAtDesc(String jurisdictionCode);

    /**
     * Count CTRs by period
     */
    @Query("SELECT COUNT(r) FROM RegulatoryReport r WHERE r.reportType = 'CTR' AND r.generatedAt BETWEEN :startDate AND :endDate")
    long countCtrsByPeriod(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    /**
     * Count alerts by period
     */
    @Query("SELECT COUNT(r) FROM RegulatoryReport r WHERE r.reportType = 'AML_ALERT' AND r.generatedAt BETWEEN :startDate AND :endDate")
    long countAlertsByPeriod(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    /**
     * Find reports requiring follow-up
     */
    List<RegulatoryReport> findByFollowUpRequiredTrueAndFollowUpDateBefore(LocalDateTime date);

    /**
     * Find reports pending submission
     */
    List<RegulatoryReport> findByStatusInOrderByGeneratedAtAsc(List<RegulatoryReport.ReportStatus> statuses);

    /**
     * Get report statistics by type
     */
    @Query("SELECT r.reportType, COUNT(r) FROM RegulatoryReport r WHERE r.generatedAt BETWEEN :startDate AND :endDate GROUP BY r.reportType")
    List<Object[]> getReportStatisticsByType(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    /**
     * Get monthly report generation trends
     */
    @Query("SELECT YEAR(r.generatedAt), MONTH(r.generatedAt), r.reportType, COUNT(r) FROM RegulatoryReport r WHERE r.generatedAt >= :sinceDate GROUP BY YEAR(r.generatedAt), MONTH(r.generatedAt), r.reportType ORDER BY YEAR(r.generatedAt), MONTH(r.generatedAt)")
    List<Object[]> getMonthlyReportTrends(@Param("sinceDate") LocalDateTime sinceDate);

    /**
     * Find reports with validation errors
     */
    List<RegulatoryReport> findByValidationStatusNotNullOrderByGeneratedAtDesc();

    /**
     * Find acknowledged reports
     */
    List<RegulatoryReport> findByAcknowledgmentReceivedTrueOrderByAcknowledgmentDateDesc();

    /**
     * Find reports by regulatory authority
     */
    List<RegulatoryReport> findByRegulatoryAuthorityOrderBySubmittedAtDesc(String regulatoryAuthority);

    /**
     * Get compliance metrics for dashboard
     */
    @Query("SELECT " +
           "COUNT(CASE WHEN r.status = 'SUBMITTED' THEN 1 END) as submitted, " +
           "COUNT(CASE WHEN r.status = 'ACKNOWLEDGED' THEN 1 END) as acknowledged, " +
           "COUNT(CASE WHEN r.status = 'ERROR' THEN 1 END) as errors, " +
           "COUNT(CASE WHEN r.acknowledgmentReceived = true THEN 1 END) as totalAcknowledged " +
           "FROM RegulatoryReport r WHERE r.generatedAt BETWEEN :startDate AND :endDate")
    Object getComplianceMetrics(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    /**
     * Find overdue reports (not submitted within required timeframe)
     */
    @Query("SELECT r FROM RegulatoryReport r WHERE r.status = 'GENERATED' AND r.generatedAt < :overdueThreshold")
    List<RegulatoryReport> findOverdueReports(@Param("overdueThreshold") LocalDateTime overdueThreshold);

    /**
     * Delete old reports (for data retention)
     */
    void deleteByGeneratedAtBefore(LocalDateTime cutoffDate);
}