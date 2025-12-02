package com.waqiti.compliance.repository;

import com.waqiti.compliance.entity.RegulatoryReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Regulatory Report Repository
 *
 * Data access layer for regulatory reports (FinCEN SAR, FBI, SEC)
 * with custom queries for compliance tracking and audit.
 *
 * @author Waqiti Compliance Team
 * @version 1.0
 */
@Repository
public interface RegulatoryReportRepository extends JpaRepository<RegulatoryReport, String> {

    /**
     * Find all reports by crime case ID
     *
     * @param crimeCaseId crime case ID
     * @return list of reports
     */
    List<RegulatoryReport> findByCrimeCaseId(String crimeCaseId);

    /**
     * Find all reports by report type
     *
     * @param reportType report type (e.g., "SAR", "FBI_IC3", "SEC_TCR")
     * @return list of reports
     */
    List<RegulatoryReport> findByReportType(String reportType);

    /**
     * Find all reports by agency
     *
     * @param agency agency name (e.g., "FINCEN", "FBI", "SEC")
     * @return list of reports
     */
    List<RegulatoryReport> findByAgency(String agency);

    /**
     * Find all reports by status
     *
     * @param status status (e.g., "PENDING", "SUBMITTED", "ACKNOWLEDGED")
     * @return list of reports
     */
    List<RegulatoryReport> findByStatus(String status);

    /**
     * Find report by reference number
     *
     * @param referenceNumber agency reference number
     * @return optional report
     */
    Optional<RegulatoryReport> findByReferenceNumber(String referenceNumber);

    /**
     * Find all pending reports (not yet submitted)
     *
     * @return list of pending reports
     */
    @Query("SELECT r FROM RegulatoryReport r WHERE r.status = 'PENDING' ORDER BY r.createdAt ASC")
    List<RegulatoryReport> findPendingReports();

    /**
     * Find all submitted reports awaiting acknowledgment
     *
     * @return list of submitted reports
     */
    @Query("SELECT r FROM RegulatoryReport r WHERE r.status = 'SUBMITTED' AND r.acknowledgedAt IS NULL")
    List<RegulatoryReport> findSubmittedReportsAwaitingAcknowledgment();

    /**
     * Find all reports filed within date range
     *
     * @param startDate start date
     * @param endDate end date
     * @return list of reports
     */
    @Query("SELECT r FROM RegulatoryReport r WHERE r.filedAt BETWEEN :startDate AND :endDate ORDER BY r.filedAt DESC")
    List<RegulatoryReport> findReportsByFilingDateRange(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );

    /**
     * Find all SAR reports
     *
     * @return list of SAR reports
     */
    @Query("SELECT r FROM RegulatoryReport r WHERE r.reportType = 'SAR'")
    List<RegulatoryReport> findAllSARReports();

    /**
     * Find all emergency SAR reports
     *
     * @return list of emergency SARs
     */
    @Query("SELECT r FROM RegulatoryReport r WHERE r.reportType = 'SAR' AND r.isEmergency = true")
    List<RegulatoryReport> findEmergencySARReports();

    /**
     * Find all reports filed by user
     *
     * @param filedBy user ID who filed the report
     * @return list of reports
     */
    List<RegulatoryReport> findByFiledByOrderByFiledAtDesc(String filedBy);

    /**
     * Count reports by type
     *
     * @param reportType report type
     * @return count of reports
     */
    long countByReportType(String reportType);

    /**
     * Count reports by agency
     *
     * @param agency agency name
     * @return count of reports
     */
    long countByAgency(String agency);

    /**
     * Find overdue reports (filed but not acknowledged within expected timeframe)
     *
     * @param cutoffDate cutoff date for acknowledgment
     * @return list of overdue reports
     */
    @Query("SELECT r FROM RegulatoryReport r WHERE r.status = 'SUBMITTED' AND r.acknowledgedAt IS NULL AND r.filedAt < :cutoffDate")
    List<RegulatoryReport> findOverdueReports(@Param("cutoffDate") LocalDateTime cutoffDate);

    /**
     * Find all reports requiring follow-up
     *
     * @return list of reports requiring follow-up
     */
    @Query("SELECT r FROM RegulatoryReport r WHERE r.followUpRequired = true AND r.status != 'CLOSED'")
    List<RegulatoryReport> findReportsRequiringFollowUp();
}
