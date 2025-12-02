package com.waqiti.payment.fraud.repository;

import com.waqiti.payment.fraud.model.FraudReviewCase;
import com.waqiti.payment.fraud.model.FraudReviewStatus;
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
 * Fraud Review Case Repository
 *
 * Data access layer for fraud review cases with optimized queries
 * for queue management and analytics.
 *
 * @author Waqiti Fraud Detection Team
 * @version 2.0.0
 */
@Repository
public interface FraudReviewCaseRepository extends JpaRepository<FraudReviewCase, UUID> {

    // ========== BASIC LOOKUPS ==========

    /**
     * Find fraud review case by review ID
     */
    Optional<FraudReviewCase> findByReviewId(String reviewId);

    /**
     * Find fraud review case by payment ID
     */
    Optional<FraudReviewCase> findByPaymentId(UUID paymentId);

    /**
     * Find all cases for a user
     */
    List<FraudReviewCase> findByUserIdOrderByCreatedAtDesc(String userId);

    /**
     * Find all cases for a user with pagination
     */
    Page<FraudReviewCase> findByUserId(String userId, Pageable pageable);

    // ========== QUEUE MANAGEMENT ==========

    /**
     * Get next pending case for review (ordered by priority, then queue time)
     */
    Optional<FraudReviewCase> findTopByStatusOrderByPriorityAscQueuedAtAsc(FraudReviewStatus status);

    /**
     * Get all pending cases ordered by priority
     */
    List<FraudReviewCase> findByStatusOrderByPriorityAscQueuedAtAsc(
        FraudReviewStatus status, Pageable pageable);

    /**
     * Find cases by status
     */
    List<FraudReviewCase> findByStatus(FraudReviewStatus status);

    /**
     * Find cases assigned to analyst
     */
    List<FraudReviewCase> findByAssignedAnalystAndStatus(
        String analystId, FraudReviewStatus status);

    /**
     * Get most recently created case
     */
    Optional<FraudReviewCase> findFirstByOrderByCreatedAtDesc();

    // ========== STATISTICS & METRICS ==========

    /**
     * Count cases by status
     */
    long countByStatus(FraudReviewStatus status);

    /**
     * Count cases by status and priority
     */
    long countByStatusAndPriority(FraudReviewStatus status, Integer priority);

    /**
     * Count overdue cases (past SLA deadline)
     */
    long countByStatusAndSlaDeadlineBefore(FraudReviewStatus status, LocalDateTime deadline);

    /**
     * Find overdue cases
     */
    List<FraudReviewCase> findByStatusAndSlaDeadlineBefore(
        FraudReviewStatus status, LocalDateTime deadline);

    /**
     * Count completed cases after a date
     */
    long countByStatusAndReviewCompletedAtAfter(FraudReviewStatus status, LocalDateTime after);

    /**
     * Count SLA violations after a date
     */
    long countBySlaViolationAndReviewCompletedAtAfter(boolean slaViolation, LocalDateTime after);

    /**
     * Get average review duration
     */
    @Query("SELECT AVG(f.reviewDurationMinutes) FROM FraudReviewCase f " +
           "WHERE f.status = 'COMPLETED' AND f.reviewCompletedAt >= :since")
    Double getAverageReviewDurationMinutes(@Param("since") LocalDateTime since);

    /**
     * Count recent checks by user
     */
    @Query("SELECT COUNT(f) FROM FraudReviewCase f " +
           "WHERE f.userId = :userId AND f.createdAt >= :since")
    long countByUserIdAndCreatedAtAfter(
        @Param("userId") String userId,
        @Param("since") LocalDateTime since);

    /**
     * Find recent cases by user
     */
    @Query("SELECT f FROM FraudReviewCase f " +
           "WHERE f.userId = :userId AND f.createdAt >= :since " +
           "ORDER BY f.createdAt DESC")
    List<FraudReviewCase> findRecentByUserId(
        @Param("userId") String userId,
        @Param("since") LocalDateTime since);

    /**
     * Count high-risk cases by user
     */
    @Query("SELECT COUNT(f) FROM FraudReviewCase f " +
           "WHERE f.userId = :userId AND f.riskScore >= 0.7 AND f.createdAt >= :since")
    long countHighRiskByUserId(
        @Param("userId") String userId,
        @Param("since") LocalDateTime since);

    /**
     * Count blocked cases by user
     */
    @Query("SELECT COUNT(f) FROM FraudReviewCase f " +
           "WHERE f.userId = :userId AND f.decision = 'REJECT' AND f.createdAt >= :since")
    long countBlockedByUserId(
        @Param("userId") String userId,
        @Param("since") LocalDateTime since);

    // ========== DEVICE & IP ANALYTICS ==========

    /**
     * Find cases by device ID
     */
    List<FraudReviewCase> findByDeviceIdOrderByCreatedAtDesc(String deviceId, Pageable pageable);

    /**
     * Count cases by device ID
     */
    long countByDeviceId(String deviceId);

    /**
     * Count blocked cases by device ID
     */
    @Query("SELECT COUNT(f) FROM FraudReviewCase f " +
           "WHERE f.deviceId = :deviceId AND f.decision = 'REJECT'")
    long countBlockedByDeviceId(@Param("deviceId") String deviceId);

    /**
     * Count distinct users by device ID
     */
    @Query("SELECT COUNT(DISTINCT f.userId) FROM FraudReviewCase f " +
           "WHERE f.deviceId = :deviceId")
    long countDistinctUsersByDeviceId(@Param("deviceId") String deviceId);

    /**
     * Get device first seen time
     */
    @Query("SELECT MIN(f.createdAt) FROM FraudReviewCase f WHERE f.deviceId = :deviceId")
    LocalDateTime getDeviceFirstSeenTime(@Param("deviceId") String deviceId);

    /**
     * Check if device is user's primary device
     */
    @Query("SELECT CASE WHEN COUNT(f) > 10 THEN true ELSE false END " +
           "FROM FraudReviewCase f WHERE f.userId = :userId AND f.deviceId = :deviceId")
    boolean isUserPrimaryDevice(
        @Param("userId") String userId,
        @Param("deviceId") String deviceId);

    // ========== REPORTING & ANALYTICS ==========

    /**
     * Get risk level distribution
     */
    @Query("SELECT f.riskLevel, COUNT(f) FROM FraudReviewCase f " +
           "WHERE f.createdAt >= :since GROUP BY f.riskLevel")
    List<Object[]> countByRiskLevelSince(@Param("since") LocalDateTime since);

    /**
     * Get decision distribution
     */
    @Query("SELECT f.decision, COUNT(f) FROM FraudReviewCase f " +
           "WHERE f.reviewCompletedAt >= :since GROUP BY f.decision")
    List<Object[]> countByDecisionSince(@Param("since") LocalDateTime since);

    /**
     * Get average risk score
     */
    @Query("SELECT AVG(f.riskScore) FROM FraudReviewCase f WHERE f.createdAt >= :since")
    Double getAverageRiskScoreSince(@Param("since") LocalDateTime since);

    /**
     * Find cases by analyst and date range
     */
    @Query("SELECT f FROM FraudReviewCase f " +
           "WHERE f.assignedAnalyst = :analystId " +
           "AND f.reviewCompletedAt BETWEEN :startDate AND :endDate " +
           "ORDER BY f.reviewCompletedAt DESC")
    List<FraudReviewCase> findByAnalystAndDateRange(
        @Param("analystId") String analystId,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate);

    /**
     * Get analyst performance metrics
     */
    @Query("SELECT f.assignedAnalyst, " +
           "COUNT(f), " +
           "AVG(f.reviewDurationMinutes), " +
           "SUM(CASE WHEN f.slaViolation = true THEN 1 ELSE 0 END) " +
           "FROM FraudReviewCase f " +
           "WHERE f.reviewCompletedAt >= :since " +
           "GROUP BY f.assignedAnalyst")
    List<Object[]> getAnalystPerformanceMetrics(@Param("since") LocalDateTime since);

    // ========== ESCALATION TRACKING ==========

    /**
     * Find escalated cases
     */
    List<FraudReviewCase> findByEscalatedTrueOrderByEscalatedAtDesc();

    /**
     * Count escalated cases
     */
    long countByEscalatedTrue();

    /**
     * Find recently escalated cases
     */
    @Query("SELECT f FROM FraudReviewCase f " +
           "WHERE f.escalated = true AND f.escalatedAt >= :since " +
           "ORDER BY f.escalatedAt DESC")
    List<FraudReviewCase> findRecentlyEscalated(@Param("since") LocalDateTime since);

    // ========== MERCHANT ANALYTICS ==========

    /**
     * Count cases by merchant
     */
    @Query("SELECT COUNT(f) FROM FraudReviewCase f " +
           "WHERE f.merchantId = :merchantId AND f.createdAt >= :since")
    long countByMerchantIdSince(
        @Param("merchantId") String merchantId,
        @Param("since") LocalDateTime since);

    /**
     * Count blocked cases by merchant
     */
    @Query("SELECT COUNT(f) FROM FraudReviewCase f " +
           "WHERE f.merchantId = :merchantId AND f.decision = 'REJECT'")
    long countBlockedByMerchantId(@Param("merchantId") String merchantId);

    /**
     * Get merchant category
     */
    @Query("SELECT f.merchantCategory FROM FraudReviewCase f " +
           "WHERE f.merchantId = :merchantId ORDER BY f.createdAt DESC LIMIT 1")
    String getMerchantCategory(@Param("merchantId") String merchantId);
}
