package com.waqiti.common.gdpr.repository;

import com.waqiti.common.gdpr.model.GDPRDataExport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * GDPR Data Export Repository
 *
 * Manages data export requests as required by GDPR Article 15 (Right to Access)
 * and Article 20 (Right to Data Portability).
 *
 * @author Waqiti Platform Team
 * @version 1.0
 * @since 2025-10-20
 */
@Repository
public interface GDPRDataExportRepository extends JpaRepository<GDPRDataExport, UUID> {

    /**
     * Find all export requests for a user
     */
    List<GDPRDataExport> findByUserIdOrderByRequestedAtDesc(UUID userId);

    /**
     * Find export by export ID
     */
    Optional<GDPRDataExport> findByExportId(String exportId);

    /**
     * Find pending export requests
     */
    @Query("SELECT e FROM GDPRDataExport e WHERE e.status = 'PENDING' ORDER BY e.requestedAt ASC")
    List<GDPRDataExport> findPendingExports();

    /**
     * Find in-progress export requests
     */
    @Query("SELECT e FROM GDPRDataExport e WHERE e.status = 'PROCESSING' ORDER BY e.startedAt ASC")
    List<GDPRDataExport> findProcessingExports();

    /**
     * Find completed exports ready for download
     */
    @Query("SELECT e FROM GDPRDataExport e WHERE e.userId = :userId " +
            "AND e.status = 'COMPLETED' " +
            "AND e.expiresAt > :now " +
            "ORDER BY e.completedAt DESC")
    List<GDPRDataExport> findAvailableExports(
            @Param("userId") UUID userId,
            @Param("now") LocalDateTime now
    );

    /**
     * Find expired exports that need cleanup
     */
    @Query("SELECT e FROM GDPRDataExport e WHERE e.expiresAt <= :now " +
            "AND e.status NOT IN ('EXPIRED', 'CANCELLED')")
    List<GDPRDataExport> findExpiredExports(@Param("now") LocalDateTime now);

    /**
     * Find exports older than retention period
     */
    @Query("SELECT e FROM GDPRDataExport e WHERE e.createdAt < :cutoffDate")
    List<GDPRDataExport> findExportsOlderThan(@Param("cutoffDate") LocalDateTime cutoffDate);

    /**
     * Count pending exports for a user
     */
    @Query("SELECT COUNT(e) FROM GDPRDataExport e WHERE e.userId = :userId " +
            "AND e.status IN ('PENDING', 'PROCESSING')")
    long countPendingExportsByUser(@Param("userId") UUID userId);

    /**
     * Find failed exports for retry
     */
    @Query("SELECT e FROM GDPRDataExport e WHERE e.status = 'FAILED' " +
            "AND e.retryCount < :maxRetries " +
            "ORDER BY e.requestedAt ASC")
    List<GDPRDataExport> findFailedExportsForRetry(@Param("maxRetries") int maxRetries);

    /**
     * Update export status
     */
    @Modifying
    @Query("UPDATE GDPRDataExport e SET e.status = :status, e.updatedAt = :now " +
            "WHERE e.id = :id")
    int updateStatus(
            @Param("id") UUID id,
            @Param("status") GDPRDataExport.ExportStatus status,
            @Param("now") LocalDateTime now
    );

    /**
     * Mark exports as expired
     */
    @Modifying
    @Query("UPDATE GDPRDataExport e SET e.status = 'EXPIRED', e.updatedAt = :now " +
            "WHERE e.expiresAt <= :now AND e.status = 'COMPLETED'")
    int markExpiredExports(@Param("now") LocalDateTime now);

    /**
     * Find exports by status
     */
    List<GDPRDataExport> findByStatus(GDPRDataExport.ExportStatus status);

    /**
     * Find exports requested within date range
     */
    @Query("SELECT e FROM GDPRDataExport e WHERE e.requestedAt BETWEEN :startDate AND :endDate")
    List<GDPRDataExport> findExportsRequestedBetween(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    /**
     * Count exports by status
     */
    @Query("SELECT e.status, COUNT(e) FROM GDPRDataExport e GROUP BY e.status")
    List<Object[]> countExportsByStatus();

    /**
     * Find most recent completed export for user
     */
    @Query("SELECT e FROM GDPRDataExport e WHERE e.userId = :userId " +
            "AND e.status = 'COMPLETED' " +
            "ORDER BY e.completedAt DESC")
    Optional<GDPRDataExport> findMostRecentCompletedExport(@Param("userId") UUID userId);

    /**
     * Delete expired exports (physical cleanup)
     */
    @Modifying
    @Query("DELETE FROM GDPRDataExport e WHERE e.status = 'EXPIRED' " +
            "AND e.expiresAt < :cutoffDate")
    int deleteExpiredExports(@Param("cutoffDate") LocalDateTime cutoffDate);

    /**
     * Calculate average processing time
     */
    @Query("SELECT AVG(TIMESTAMPDIFF(SECOND, e.startedAt, e.completedAt)) " +
            "FROM GDPRDataExport e WHERE e.status = 'COMPLETED' " +
            "AND e.startedAt IS NOT NULL AND e.completedAt IS NOT NULL")
    Double calculateAverageProcessingTimeSeconds();
}