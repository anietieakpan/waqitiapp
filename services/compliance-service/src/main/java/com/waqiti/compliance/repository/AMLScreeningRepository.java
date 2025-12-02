package com.waqiti.compliance.repository;

import com.waqiti.compliance.domain.AMLScreening;
import com.waqiti.compliance.domain.AMLScreeningStatus;
import com.waqiti.compliance.domain.AMLRiskLevel;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Production-grade repository for AML screening operations.
 * Provides comprehensive data access for AML screening records.
 */
@Repository
public interface AMLScreeningRepository extends MongoRepository<AMLScreening, String> {

    /**
     * Check if a screening already exists for an entity and event (idempotency)
     */
    boolean existsByEntityIdAndEventId(String entityId, String eventId);

    /**
     * Find screening by event ID
     */
    Optional<AMLScreening> findByEventId(String eventId);

    /**
     * Find all screenings for an entity
     */
    List<AMLScreening> findByEntityIdOrderByCreatedAtDesc(String entityId);

    /**
     * Find screenings by entity type
     */
    List<AMLScreening> findByEntityTypeOrderByCreatedAtDesc(String entityType);

    /**
     * Find screenings by status
     */
    List<AMLScreening> findByStatus(AMLScreeningStatus status);

    /**
     * Find screenings by risk level
     */
    List<AMLScreening> findByRiskLevel(AMLRiskLevel riskLevel);

    /**
     * Find high-risk screenings that need review
     */
    @Query("{ 'riskLevel': { $in: ['HIGH', 'CRITICAL', 'PROHIBITED'] }, 'status': { $in: ['UNDER_REVIEW', 'ESCALATED'] } }")
    List<AMLScreening> findHighRiskScreeningsNeedingReview();

    /**
     * Find screenings pending review
     */
    List<AMLScreening> findByStatusIn(List<AMLScreeningStatus> statuses);

    /**
     * Find screenings by transaction ID
     */
    Optional<AMLScreening> findByTransactionId(String transactionId);

    /**
     * Find all screenings for a transaction
     */
    List<AMLScreening> findAllByTransactionIdOrderByCreatedAtDesc(String transactionId);

    /**
     * Find screenings by correlation ID
     */
    List<AMLScreening> findByCorrelationId(String correlationId);

    /**
     * Find screenings that resulted in blocking
     */
    @Query("{ 'blocked': true }")
    List<AMLScreening> findBlockedScreenings();

    /**
     * Find screenings that resulted in SAR filing
     */
    @Query("{ 'reported': true }")
    List<AMLScreening> findReportedScreenings();

    /**
     * Find screenings by date range
     */
    @Query("{ 'createdAt': { $gte: ?0, $lte: ?1 } }")
    List<AMLScreening> findByCreatedAtBetween(LocalDateTime startDate, LocalDateTime endDate);

    /**
     * Find escalated screenings
     */
    @Query("{ 'escalated': true, 'status': { $ne: 'COMPLETED' } }")
    List<AMLScreening> findEscalatedScreeningsNotCompleted();

    /**
     * Find screenings by screening type
     */
    List<AMLScreening> findByScreeningTypeOrderByCreatedAtDesc(AMLScreening.AMLScreeningType screeningType);

    /**
     * Find screenings with matches
     */
    @Query("{ 'totalMatches': { $gt: 0 } }")
    List<AMLScreening> findScreeningsWithMatches();

    /**
     * Find screenings by entity and date range
     */
    @Query("{ 'entityId': ?0, 'createdAt': { $gte: ?1, $lte: ?2 } }")
    List<AMLScreening> findByEntityIdAndDateRange(String entityId, LocalDateTime startDate, LocalDateTime endDate);

    /**
     * Find screenings by reviewer
     */
    List<AMLScreening> findByReviewerId(String reviewerId);

    /**
     * Find screenings by screening provider
     */
    List<AMLScreening> findByScreeningProvider(String provider);

    /**
     * Count screenings by status
     */
    long countByStatus(AMLScreeningStatus status);

    /**
     * Count screenings by risk level
     */
    long countByRiskLevel(AMLRiskLevel riskLevel);

    /**
     * Count high-risk screenings created today
     */
    @Query(value = "{ 'riskLevel': { $in: ['HIGH', 'CRITICAL', 'PROHIBITED'] }, 'createdAt': { $gte: ?0 } }", count = true)
    long countHighRiskScreeningsSince(LocalDateTime since);

    /**
     * Find most recent screening for an entity
     */
    Optional<AMLScreening> findFirstByEntityIdOrderByCreatedAtDesc(String entityId);

    /**
     * Check if entity has any high-risk screenings
     */
    @Query(value = "{ 'entityId': ?0, 'riskLevel': { $in: ['HIGH', 'CRITICAL', 'PROHIBITED'] } }", exists = true)
    boolean existsHighRiskScreeningForEntity(String entityId);

    /**
     * Find failed screenings that need retry
     */
    @Query("{ 'status': 'FAILED', 'updatedAt': { $gte: ?0 } }")
    List<AMLScreening> findFailedScreeningsSince(LocalDateTime since);

    /**
     * Find screenings with false positives
     */
    @Query("{ 'falsePositive': true }")
    List<AMLScreening> findFalsePositiveScreenings();

    /**
     * Find pending screenings older than specified time
     */
    @Query("{ 'status': { $in: ['INITIATED', 'IN_PROGRESS'] }, 'createdAt': { $lt: ?0 } }")
    List<AMLScreening> findStaleScreenings(LocalDateTime olderThan);
}
