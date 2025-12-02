package com.waqiti.account.repository;

import com.waqiti.account.entity.PermanentFailureRecord;
import com.waqiti.account.entity.PermanentFailureRecord.BusinessImpact;
import com.waqiti.account.entity.PermanentFailureRecord.FailureCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for permanent failure audit records
 *
 * <p><b>⚠️ CRITICAL COMPLIANCE REQUIREMENT:</b></p>
 * <p>This repository provides READ-ONLY query access. DELETE operations are
 * BLOCKED by database trigger to ensure 7-year SOX/GDPR retention compliance.</p>
 *
 * <p>All queries support compliance reporting, forensic analysis, and
 * business impact assessment.</p>
 *
 * @author Waqiti Platform Team
 * @since 1.0.0
 */
@Repository
public interface PermanentFailureRepository extends JpaRepository<PermanentFailureRecord, UUID> {

    /**
     * Find failures by topic
     *
     * @param topic Original topic name
     * @return List of permanent failures ordered by failed_at DESC
     */
    List<PermanentFailureRecord> findByOriginalTopicOrderByFailedAtDesc(String topic);

    /**
     * Find failures by topic and date range
     *
     * @param topic Original topic
     * @param startDate Start date (inclusive)
     * @param endDate End date (inclusive)
     * @return List of failures
     */
    @Query("SELECT f FROM PermanentFailureRecord f WHERE f.originalTopic = :topic " +
           "AND f.failedAt >= :startDate AND f.failedAt <= :endDate " +
           "ORDER BY f.failedAt DESC")
    List<PermanentFailureRecord> findByTopicAndDateRange(
        @Param("topic") String topic,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate);

    /**
     * Find failures by category
     *
     * @param category Failure category
     * @return List of failures
     */
    List<PermanentFailureRecord> findByFailureCategoryOrderByFailedAtDesc(FailureCategory category);

    /**
     * Find failures by business impact level
     *
     * @param impact Business impact level
     * @return List of failures
     */
    List<PermanentFailureRecord> findByBusinessImpactOrderByFailedAtDesc(BusinessImpact impact);

    /**
     * Find failures requiring compliance review
     *
     * @return List of unreviewed failures
     */
    @Query("SELECT f FROM PermanentFailureRecord f WHERE f.complianceReviewed = false " +
           "ORDER BY f.businessImpact ASC, f.recordedAt ASC")
    List<PermanentFailureRecord> findUnreviewedFailures();

    /**
     * Find failures requiring remediation
     *
     * @param status Remediation status filter
     * @return List of failures requiring remediation
     */
    @Query("SELECT f FROM PermanentFailureRecord f WHERE f.remediationRequired = true " +
           "AND (:status IS NULL OR f.remediationStatus = :status) " +
           "ORDER BY f.businessImpact ASC, f.recordedAt ASC")
    List<PermanentFailureRecord> findFailuresRequiringRemediation(
        @Param("status") String status);

    /**
     * Find failures by correlation ID
     *
     * @param correlationId Correlation ID
     * @return List of related failures
     */
    List<PermanentFailureRecord> findByCorrelationIdOrderByRecordedAtDesc(String correlationId);

    /**
     * Find failures by handler name
     *
     * @param handlerName DLQ handler name
     * @return List of failures
     */
    List<PermanentFailureRecord> findByHandlerNameOrderByFailedAtDesc(String handlerName);

    /**
     * Find failure by original message coordinates
     *
     * @param topic Original topic
     * @param partition Original partition
     * @param offset Original offset
     * @return Optional failure record
     */
    Optional<PermanentFailureRecord> findByOriginalTopicAndOriginalPartitionAndOriginalOffset(
        String topic, Integer partition, Long offset);

    /**
     * Count failures by category
     *
     * @param category Failure category
     * @return Count
     */
    long countByFailureCategory(FailureCategory category);

    /**
     * Count failures by business impact
     *
     * @param impact Business impact level
     * @return Count
     */
    long countByBusinessImpact(BusinessImpact impact);

    /**
     * Count failures by topic
     *
     * @param topic Topic name
     * @return Count
     */
    long countByOriginalTopic(String topic);

    /**
     * Count unreviewed failures
     *
     * @return Count of failures requiring compliance review
     */
    @Query("SELECT COUNT(f) FROM PermanentFailureRecord f WHERE f.complianceReviewed = false")
    long countUnreviewedFailures();

    /**
     * Calculate total financial impact
     *
     * @param currency Currency code (default: USD)
     * @return Total financial impact amount
     */
    @Query("SELECT COALESCE(SUM(f.financialImpactAmount), 0) FROM PermanentFailureRecord f " +
           "WHERE f.financialImpactCurrency = :currency")
    BigDecimal calculateTotalFinancialImpact(@Param("currency") String currency);

    /**
     * Calculate financial impact by topic
     *
     * @param topic Topic name
     * @param currency Currency code
     * @return Total financial impact for topic
     */
    @Query("SELECT COALESCE(SUM(f.financialImpactAmount), 0) FROM PermanentFailureRecord f " +
           "WHERE f.originalTopic = :topic AND f.financialImpactCurrency = :currency")
    BigDecimal calculateFinancialImpactByTopic(
        @Param("topic") String topic,
        @Param("currency") String currency);

    /**
     * Find records eligible for archival (past 7-year retention)
     *
     * @param today Current date
     * @return List of archival-eligible records
     */
    @Query("SELECT f FROM PermanentFailureRecord f WHERE f.auditRetentionUntil < :today " +
           "ORDER BY f.auditRetentionUntil ASC")
    List<PermanentFailureRecord> findEligibleForArchival(@Param("today") LocalDate today);

    /**
     * Find high-impact failures requiring attention
     *
     * @return List of critical/high impact failures not yet reviewed
     */
    @Query("SELECT f FROM PermanentFailureRecord f WHERE f.businessImpact IN ('CRITICAL', 'HIGH') " +
           "AND f.complianceReviewed = false " +
           "ORDER BY f.businessImpact ASC, f.recordedAt ASC")
    List<PermanentFailureRecord> findHighImpactUnreviewedFailures();

    /**
     * Find failures by date range for compliance reporting
     *
     * @param startDate Report start date
     * @param endDate Report end date
     * @return List of failures in date range
     */
    @Query("SELECT f FROM PermanentFailureRecord f WHERE f.recordedAt >= :startDate " +
           "AND f.recordedAt <= :endDate ORDER BY f.recordedAt DESC")
    List<PermanentFailureRecord> findForComplianceReport(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate);

    /**
     * Get failure statistics by category (for dashboards)
     *
     * @return List of [category, count] tuples
     */
    @Query("SELECT f.failureCategory, COUNT(f) FROM PermanentFailureRecord f " +
           "GROUP BY f.failureCategory ORDER BY COUNT(f) DESC")
    List<Object[]> getFailureStatisticsByCategory();

    /**
     * Get failure statistics by topic (for monitoring)
     *
     * @return List of [topic, count] tuples
     */
    @Query("SELECT f.originalTopic, COUNT(f) FROM PermanentFailureRecord f " +
           "GROUP BY f.originalTopic ORDER BY COUNT(f) DESC")
    List<Object[]> getFailureStatisticsByTopic();

    /**
     * Get failure trend over time (daily aggregation)
     *
     * @param startDate Trend start date
     * @param endDate Trend end date
     * @return List of [date, count] tuples
     */
    @Query("SELECT CAST(f.recordedAt AS DATE), COUNT(f) FROM PermanentFailureRecord f " +
           "WHERE f.recordedAt >= :startDate AND f.recordedAt <= :endDate " +
           "GROUP BY CAST(f.recordedAt AS DATE) ORDER BY CAST(f.recordedAt AS DATE) ASC")
    List<Object[]> getFailureTrend(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate);
}
