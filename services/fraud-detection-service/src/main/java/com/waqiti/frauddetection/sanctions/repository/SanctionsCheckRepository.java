package com.waqiti.frauddetection.sanctions.repository;

import com.waqiti.common.repository.BaseRepository;
import com.waqiti.frauddetection.sanctions.entity.SanctionsCheckRecord;
import com.waqiti.frauddetection.sanctions.entity.SanctionsCheckRecord.*;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for OFAC sanctions screening records.
 *
 * @author Waqiti Platform
 * @version 1.0
 * @since 2025-10-01
 */
@Repository
public interface SanctionsCheckRepository extends BaseRepository<SanctionsCheckRecord, UUID> {

    /**
     * Find by idempotency key to prevent duplicate checks
     */
    Optional<SanctionsCheckRecord> findByIdempotencyKey(String idempotencyKey);

    /**
     * Find all checks for a specific user
     */
    @Query("SELECT s FROM SanctionsCheckRecord s WHERE s.userId = :userId AND s.deleted = false ORDER BY s.checkedAt DESC")
    List<SanctionsCheckRecord> findByUserId(@Param("userId") UUID userId);

    /**
     * Find latest check for a user
     */
    @Query("SELECT s FROM SanctionsCheckRecord s WHERE s.userId = :userId AND s.deleted = false ORDER BY s.checkedAt DESC")
    Optional<SanctionsCheckRecord> findLatestByUserId(@Param("userId") UUID userId);

    /**
     * Find checks by entity
     */
    @Query("SELECT s FROM SanctionsCheckRecord s WHERE s.entityType = :entityType AND s.entityId = :entityId AND s.deleted = false ORDER BY s.checkedAt DESC")
    List<SanctionsCheckRecord> findByEntity(@Param("entityType") EntityType entityType,
                                             @Param("entityId") UUID entityId);

    /**
     * Find checks with matches found
     */
    @Query("SELECT s FROM SanctionsCheckRecord s WHERE s.matchFound = true AND s.checkStatus = :status AND s.deleted = false ORDER BY s.checkedAt DESC")
    List<SanctionsCheckRecord> findMatchesWithStatus(@Param("status") CheckStatus status);

    /**
     * Find checks pending manual review
     */
    @Query("SELECT s FROM SanctionsCheckRecord s WHERE s.checkStatus = 'MANUAL_REVIEW' AND s.resolution IS NULL AND s.deleted = false ORDER BY s.riskLevel DESC, s.checkedAt ASC")
    List<SanctionsCheckRecord> findPendingManualReview();

    /**
     * Find high-risk unresolved checks
     */
    @Query("SELECT s FROM SanctionsCheckRecord s WHERE s.riskLevel IN ('HIGH', 'CRITICAL') AND s.resolution IS NULL AND s.deleted = false ORDER BY s.checkedAt DESC")
    List<SanctionsCheckRecord> findHighRiskUnresolved();

    /**
     * Find checks for a transaction
     */
    @Query("SELECT s FROM SanctionsCheckRecord s WHERE s.relatedTransactionId = :transactionId AND s.deleted = false")
    List<SanctionsCheckRecord> findByTransactionId(@Param("transactionId") UUID transactionId);

    /**
     * Find checks within date range
     */
    @Query("SELECT s FROM SanctionsCheckRecord s WHERE s.checkedAt BETWEEN :startDate AND :endDate AND s.deleted = false ORDER BY s.checkedAt DESC")
    List<SanctionsCheckRecord> findByDateRange(@Param("startDate") LocalDateTime startDate,
                                                @Param("endDate") LocalDateTime endDate);

    /**
     * Find checks due for periodic review
     */
    @Query("SELECT s FROM SanctionsCheckRecord s WHERE s.nextReviewDate <= CURRENT_DATE AND s.resolution = 'CLEARED' AND s.deleted = false")
    List<SanctionsCheckRecord> findDueForReview();

    /**
     * Find checks with specific risk level
     */
    @Query("SELECT s FROM SanctionsCheckRecord s WHERE s.riskLevel = :riskLevel AND s.deleted = false ORDER BY s.checkedAt DESC")
    List<SanctionsCheckRecord> findByRiskLevel(@Param("riskLevel") RiskLevel riskLevel);

    /**
     * Find checks by resolution status
     */
    @Query("SELECT s FROM SanctionsCheckRecord s WHERE s.resolution = :resolution AND s.deleted = false ORDER BY s.resolvedAt DESC")
    List<SanctionsCheckRecord> findByResolution(@Param("resolution") Resolution resolution);

    /**
     * Find checks where SAR was filed
     */
    @Query("SELECT s FROM SanctionsCheckRecord s WHERE s.sarFiled = true AND s.deleted = false ORDER BY s.checkedAt DESC")
    List<SanctionsCheckRecord> findWithSarFiled();

    /**
     * Count checks by status within date range
     */
    @Query("SELECT s.checkStatus, COUNT(s) FROM SanctionsCheckRecord s WHERE s.checkedAt BETWEEN :startDate AND :endDate AND s.deleted = false GROUP BY s.checkStatus")
    List<Object[]> countByStatusInDateRange(@Param("startDate") LocalDateTime startDate,
                                            @Param("endDate") LocalDateTime endDate);

    /**
     * Count matches by risk level
     */
    @Query("SELECT s.riskLevel, COUNT(s) FROM SanctionsCheckRecord s WHERE s.matchFound = true AND s.deleted = false GROUP BY s.riskLevel")
    List<Object[]> countMatchesByRiskLevel();

    /**
     * Find false positives for ML training
     */
    @Query("SELECT s FROM SanctionsCheckRecord s WHERE s.falsePositive = true AND s.deleted = false ORDER BY s.checkedAt DESC")
    List<SanctionsCheckRecord> findFalsePositives();

    /**
     * Find checks with automated decisions
     */
    @Query("SELECT s FROM SanctionsCheckRecord s WHERE s.automatedDecision = true AND s.automatedDecisionConfidence >= :minConfidence AND s.deleted = false")
    List<SanctionsCheckRecord> findAutomatedDecisionsAboveConfidence(@Param("minConfidence") java.math.BigDecimal minConfidence);

    /**
     * Find checks by compliance case
     */
    @Query("SELECT s FROM SanctionsCheckRecord s WHERE s.complianceCaseId = :caseId AND s.deleted = false")
    List<SanctionsCheckRecord> findByComplianceCase(@Param("caseId") UUID caseId);

    /**
     * Find checks by check source
     */
    @Query("SELECT s FROM SanctionsCheckRecord s WHERE s.checkSource = :source AND s.deleted = false ORDER BY s.checkedAt DESC")
    List<SanctionsCheckRecord> findByCheckSource(@Param("source") CheckSource source);

    /**
     * Find recent checks for name (fuzzy match prevention)
     */
    @Query("SELECT s FROM SanctionsCheckRecord s WHERE LOWER(s.checkedName) = LOWER(:name) AND s.checkedAt > :since AND s.deleted = false ORDER BY s.checkedAt DESC")
    List<SanctionsCheckRecord> findRecentChecksByName(@Param("name") String name,
                                                       @Param("since") LocalDateTime since);

    /**
     * Count daily checks for compliance reporting
     */
    @Query("SELECT DATE(s.checkedAt), COUNT(s), SUM(CASE WHEN s.matchFound = true THEN 1 ELSE 0 END) " +
           "FROM SanctionsCheckRecord s WHERE s.checkedAt BETWEEN :startDate AND :endDate AND s.deleted = false " +
           "GROUP BY DATE(s.checkedAt) ORDER BY DATE(s.checkedAt)")
    List<Object[]> getDailyCheckStatistics(@Param("startDate") LocalDateTime startDate,
                                           @Param("endDate") LocalDateTime endDate);

    /**
     * Find blocked entities (for watchlist)
     */
    @Query("SELECT s FROM SanctionsCheckRecord s WHERE s.resolution = 'BLOCKED' AND s.deleted = false ORDER BY s.resolvedAt DESC")
    List<SanctionsCheckRecord> findBlockedEntities();

    /**
     * Find whitelisted entities
     */
    @Query("SELECT s FROM SanctionsCheckRecord s WHERE s.resolution = 'WHITELISTED' AND s.deleted = false ORDER BY s.resolvedAt DESC")
    List<SanctionsCheckRecord> findWhitelistedEntities();

    /**
     * Check if entity is blocked
     */
    @Query("SELECT CASE WHEN COUNT(s) > 0 THEN true ELSE false END FROM SanctionsCheckRecord s " +
           "WHERE s.entityId = :entityId AND s.resolution = 'BLOCKED' AND s.deleted = false")
    boolean isEntityBlocked(@Param("entityId") UUID entityId);

    /**
     * Check if entity is whitelisted
     */
    @Query("SELECT CASE WHEN COUNT(s) > 0 THEN true ELSE false END FROM SanctionsCheckRecord s " +
           "WHERE s.entityId = :entityId AND s.resolution = 'WHITELISTED' AND s.deleted = false")
    boolean isEntityWhitelisted(@Param("entityId") UUID entityId);
}
