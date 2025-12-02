package com.waqiti.security.repository;

import com.waqiti.security.domain.SuspiciousActivityReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Suspicious Activity Report entities
 */
@Repository
public interface SuspiciousActivityReportRepository extends JpaRepository<SuspiciousActivityReport, UUID> {

    /**
     * Find SAR by report number
     */
    Optional<SuspiciousActivityReport> findByReportNumber(String reportNumber);

    /**
     * Find SARs by status
     */
    List<SuspiciousActivityReport> findByStatusOrderByFiledDateAsc(SuspiciousActivityReport.SarStatus status);

    /**
     * Find SARs by user ID
     */
    List<SuspiciousActivityReport> findByUserIdOrderByFiledDateDesc(UUID userId);

    /**
     * Find SARs by transaction ID
     */
    List<SuspiciousActivityReport> findByTransactionId(UUID transactionId);

    /**
     * Check if SAR exists for transaction
     */
    boolean existsByTransactionId(UUID transactionId);

    /**
     * Find SARs by date range
     */
    List<SuspiciousActivityReport> findByFiledDateBetweenOrderByFiledDateDesc(
        LocalDateTime startDate, LocalDateTime endDate);

    /**
     * Count SARs by date range
     */
    long countByFiledDateBetween(LocalDateTime startDate, LocalDateTime endDate);

    /**
     * Find SARs requiring immediate attention
     */
    List<SuspiciousActivityReport> findByRequiresImmediateAttentionTrueAndStatusIn(
        List<SuspiciousActivityReport.SarStatus> statuses);

    /**
     * Find SARs reviewed by specific user
     */
    List<SuspiciousActivityReport> findByReviewedByOrderByReviewedAtDesc(UUID reviewedBy);

    /**
     * Find overdue SARs for review
     */
    @Query("SELECT s FROM SuspiciousActivityReport s WHERE s.status = 'PENDING_REVIEW' AND s.filedDate < :overdueThreshold")
    List<SuspiciousActivityReport> findOverdueSarsForReview(@Param("overdueThreshold") LocalDateTime overdueThreshold);

    /**
     * Find SARs requiring follow-up
     */
    List<SuspiciousActivityReport> findByFollowUpRequiredTrueAndFollowUpDateBefore(LocalDateTime date);

    /**
     * Get SAR statistics by status
     */
    @Query("SELECT s.status, COUNT(s) FROM SuspiciousActivityReport s WHERE s.filedDate BETWEEN :startDate AND :endDate GROUP BY s.status")
    List<Object[]> getSarStatisticsByStatus(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    /**
     * Get monthly SAR filing trends
     */
    @Query("SELECT YEAR(s.filedDate), MONTH(s.filedDate), COUNT(s) FROM SuspiciousActivityReport s WHERE s.filedDate >= :sinceDate GROUP BY YEAR(s.filedDate), MONTH(s.filedDate) ORDER BY YEAR(s.filedDate), MONTH(s.filedDate)")
    List<Object[]> getMonthlySarTrends(@Param("sinceDate") LocalDateTime sinceDate);

    /**
     * Find SARs by priority level
     */
    List<SuspiciousActivityReport> findByPriorityLevelOrderByFiledDateDesc(Integer priorityLevel);

    /**
     * Find SARs by jurisdiction
     */
    List<SuspiciousActivityReport> findByJurisdictionCodeOrderByFiledDateDesc(String jurisdictionCode);

    /**
     * Find SARs submitted to specific regulatory authority
     */
    List<SuspiciousActivityReport> findByRegulatoryAuthorityOrderBySubmittedAtDesc(String regulatoryAuthority);

    /**
     * Get users with multiple SARs
     */
    @Query("SELECT s.userId, COUNT(s) as sarCount FROM SuspiciousActivityReport s WHERE s.filedDate BETWEEN :startDate AND :endDate GROUP BY s.userId HAVING COUNT(s) >= :minSars ORDER BY sarCount DESC")
    List<Object[]> getUsersWithMultipleSars(@Param("startDate") LocalDateTime startDate, 
                                           @Param("endDate") LocalDateTime endDate, 
                                           @Param("minSars") long minSars);

    /**
     * Delete old SARs (for data retention compliance)
     */
    void deleteByFiledDateBefore(LocalDateTime cutoffDate);
}