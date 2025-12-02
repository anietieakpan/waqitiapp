package com.waqiti.security.repository;

import com.waqiti.security.model.AuthAnomaly;
import com.waqiti.security.model.AnomalySeverity;
import com.waqiti.security.model.AnomalyStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository for Authentication Anomalies
 */
@Repository
public interface AuthAnomalyRepository extends JpaRepository<AuthAnomaly, String> {

    /**
     * Find anomalies by event ID
     */
    List<AuthAnomaly> findByEventId(String eventId);

    /**
     * Find anomalies by user ID
     */
    List<AuthAnomaly> findByUserId(String userId);

    /**
     * Find recent anomalies for a user
     */
    @Query("SELECT a FROM AuthAnomaly a WHERE a.userId = :userId AND a.detectedAt >= :since ORDER BY a.detectedAt DESC")
    List<AuthAnomaly> findRecentByUserId(@Param("userId") String userId, @Param("since") Instant since);

    /**
     * Find anomalies by type
     */
    List<AuthAnomaly> findByAnomalyType(String anomalyType);

    /**
     * Find anomalies by severity
     */
    List<AuthAnomaly> findBySeverity(AnomalySeverity severity);

    /**
     * Find anomalies by status
     */
    List<AuthAnomaly> findByStatus(AnomalyStatus status);

    /**
     * Find high-severity unresolved anomalies
     */
    @Query("SELECT a FROM AuthAnomaly a WHERE a.severity IN :severities AND a.status IN :statuses ORDER BY a.detectedAt DESC")
    List<AuthAnomaly> findBySeverityInAndStatusIn(
        @Param("severities") List<AnomalySeverity> severities,
        @Param("statuses") List<AnomalyStatus> statuses
    );

    /**
     * Find anomalies in date range
     */
    @Query("SELECT a FROM AuthAnomaly a WHERE a.detectedAt BETWEEN :startDate AND :endDate ORDER BY a.detectedAt DESC")
    List<AuthAnomaly> findByDateRange(@Param("startDate") Instant startDate, @Param("endDate") Instant endDate);

    /**
     * Find anomalies by user and date range
     */
    @Query("SELECT a FROM AuthAnomaly a WHERE a.userId = :userId AND a.detectedAt BETWEEN :startDate AND :endDate ORDER BY a.detectedAt DESC")
    List<AuthAnomaly> findByUserIdAndDateRange(
        @Param("userId") String userId,
        @Param("startDate") Instant startDate,
        @Param("endDate") Instant endDate
    );

    /**
     * Check if anomaly exists for event after a certain time
     */
    boolean existsByEventIdAndCreatedAtAfter(String eventId, Instant createdAt);

    /**
     * Count anomalies by user
     */
    long countByUserId(String userId);

    /**
     * Count recent anomalies by user
     */
    @Query("SELECT COUNT(a) FROM AuthAnomaly a WHERE a.userId = :userId AND a.detectedAt >= :since")
    long countRecentByUserId(@Param("userId") String userId, @Param("since") Instant since);

    /**
     * Count anomalies by type and user
     */
    long countByUserIdAndAnomalyType(String userId, String anomalyType);

    /**
     * Find anomalies with pagination
     */
    Page<AuthAnomaly> findByUserId(String userId, Pageable pageable);

    /**
     * Find anomalies by multiple criteria
     */
    @Query("SELECT a FROM AuthAnomaly a WHERE " +
           "(:userId IS NULL OR a.userId = :userId) AND " +
           "(:anomalyType IS NULL OR a.anomalyType = :anomalyType) AND " +
           "(:severity IS NULL OR a.severity = :severity) AND " +
           "(:status IS NULL OR a.status = :status) AND " +
           "(:startDate IS NULL OR a.detectedAt >= :startDate) AND " +
           "(:endDate IS NULL OR a.detectedAt <= :endDate)")
    Page<AuthAnomaly> findByCriteria(
        @Param("userId") String userId,
        @Param("anomalyType") String anomalyType,
        @Param("severity") AnomalySeverity severity,
        @Param("status") AnomalyStatus status,
        @Param("startDate") Instant startDate,
        @Param("endDate") Instant endDate,
        Pageable pageable
    );

    /**
     * Find anomalies requiring review
     */
    @Query("SELECT a FROM AuthAnomaly a WHERE a.status = 'PENDING' AND a.severity IN ('HIGH', 'CRITICAL') ORDER BY a.detectedAt ASC")
    List<AuthAnomaly> findAnomaliesRequiringReview();

    /**
     * Get anomaly statistics by type
     */
    @Query("SELECT a.anomalyType, COUNT(a) FROM AuthAnomaly a WHERE a.detectedAt >= :since GROUP BY a.anomalyType")
    List<Object[]> getAnomalyStatsByType(@Param("since") Instant since);

    /**
     * Get anomaly statistics by severity
     */
    @Query("SELECT a.severity, COUNT(a) FROM AuthAnomaly a WHERE a.detectedAt >= :since GROUP BY a.severity")
    List<Object[]> getAnomalyStatsBySeverity(@Param("since") Instant since);

    /**
     * Find similar anomalies for correlation
     */
    @Query("SELECT a FROM AuthAnomaly a WHERE a.userId = :userId AND a.anomalyType = :anomalyType AND a.detectedAt >= :since ORDER BY a.detectedAt DESC")
    List<AuthAnomaly> findSimilarAnomalies(
        @Param("userId") String userId,
        @Param("anomalyType") String anomalyType,
        @Param("since") Instant since
    );
}
