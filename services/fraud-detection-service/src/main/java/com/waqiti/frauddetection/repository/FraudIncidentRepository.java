package com.waqiti.frauddetection.repository;

import com.waqiti.frauddetection.entity.FraudIncident;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for managing fraud incident records
 * Production-ready with optimized queries and proper indexing strategy
 */
@Repository
public interface FraudIncidentRepository extends JpaRepository<FraudIncident, UUID> {

    /**
     * Find all incidents for a specific user
     * Uses index: idx_fraud_incidents_user_id
     */
    @Query("SELECT f FROM FraudIncident f WHERE f.userId = :userId ORDER BY f.createdAt DESC")
    List<FraudIncident> findByUserIdOrderByCreatedAtDesc(@Param("userId") UUID userId);

    /**
     * Find incidents by severity level within time window
     * Uses composite index: idx_fraud_incidents_severity_created
     */
    @Query("SELECT f FROM FraudIncident f WHERE f.severity = :severity " +
           "AND f.createdAt >= :startDate ORDER BY f.createdAt DESC")
    List<FraudIncident> findBySeverityAndCreatedAtAfter(
            @Param("severity") String severity,
            @Param("startDate") LocalDateTime startDate);

    /**
     * Find open incidents requiring investigation
     * Uses composite index: idx_fraud_incidents_status_created
     */
    @Query("SELECT f FROM FraudIncident f WHERE f.status IN ('OPEN', 'INVESTIGATING') " +
           "ORDER BY f.severity DESC, f.createdAt DESC")
    List<FraudIncident> findOpenIncidents();

    /**
     * Count incidents for user in time period
     * Uses composite index: idx_fraud_incidents_user_created
     */
    @Query("SELECT COUNT(f) FROM FraudIncident f WHERE f.userId = :userId " +
           "AND f.createdAt BETWEEN :startDate AND :endDate")
    long countByUserIdAndCreatedAtBetween(
            @Param("userId") UUID userId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /**
     * Find recent high-severity incidents
     */
    @Query("SELECT f FROM FraudIncident f WHERE f.severity IN ('CRITICAL', 'HIGH') " +
           "AND f.createdAt >= :since ORDER BY f.createdAt DESC")
    List<FraudIncident> findRecentHighSeverityIncidents(@Param("since") LocalDateTime since);

    /**
     * Find incident by external reference ID
     */
    Optional<FraudIncident> findByExternalReferenceId(String externalReferenceId);

    /**
     * Find all incidents associated with a transaction
     */
    List<FraudIncident> findByTransactionId(UUID transactionId);
}
