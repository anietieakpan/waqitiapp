package com.waqiti.compliance.repository.sanctions;

import com.waqiti.compliance.model.sanctions.SanctionsUpdateHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Repository for Sanctions Update History
 *
 * Tracks all changes to sanctions lists for audit compliance and alerting.
 * Records additions, modifications, and removals.
 *
 * CRITICAL COMPLIANCE:
 * All changes must be tracked and logged for regulatory audit purposes.
 *
 * @author Waqiti Engineering Team
 * @version 2.0.0
 * @since 2025-11-19
 */
@Repository
public interface SanctionsUpdateHistoryRepository extends JpaRepository<SanctionsUpdateHistory, UUID> {

    /**
     * Find all updates for a specific list source
     *
     * @param listSource OFAC, EU, UN
     * @return List of update history records
     */
    List<SanctionsUpdateHistory> findByListSourceOrderByDetectedAtDesc(String listSource);

    /**
     * Find updates by change type
     *
     * @param changeType ADDED, MODIFIED, REMOVED
     * @return List of update history records
     */
    List<SanctionsUpdateHistory> findByChangeTypeOrderByDetectedAtDesc(String changeType);

    /**
     * Find updates detected after a certain date
     *
     * @param afterDate Date threshold
     * @return List of update history records
     */
    List<SanctionsUpdateHistory> findByDetectedAtAfterOrderByDetectedAtDesc(LocalDateTime afterDate);

    /**
     * Find updates for a specific version transition
     *
     * @param listSource OFAC, EU, UN
     * @param oldVersionId Old version ID
     * @param newVersionId New version ID
     * @return List of update history records
     */
    @Query("SELECT h FROM SanctionsUpdateHistory h " +
           "WHERE h.listSource = :listSource " +
           "AND h.oldVersionId = :oldVersionId " +
           "AND h.newVersionId = :newVersionId")
    List<SanctionsUpdateHistory> findByVersionTransition(
            @Param("listSource") String listSource,
            @Param("oldVersionId") String oldVersionId,
            @Param("newVersionId") String newVersionId);

    /**
     * Find unprocessed updates
     *
     * @return List of unprocessed update history records
     */
    @Query("SELECT h FROM SanctionsUpdateHistory h " +
           "WHERE h.processedAt IS NULL " +
           "ORDER BY h.detectedAt ASC")
    List<SanctionsUpdateHistory> findUnprocessed();

    /**
     * Find updates where compliance team was not notified
     *
     * @return List of update history records
     */
    @Query("SELECT h FROM SanctionsUpdateHistory h " +
           "WHERE h.complianceTeamNotified = false " +
           "AND h.changeType = 'ADDED' " +
           "ORDER BY h.detectedAt ASC")
    List<SanctionsUpdateHistory> findPendingNotifications();

    /**
     * Find recent updates (last N days)
     *
     * @param daysAgo Number of days to look back
     * @return List of recent update history records
     */
    @Query("SELECT h FROM SanctionsUpdateHistory h " +
           "WHERE h.detectedAt >= :cutoffDate " +
           "ORDER BY h.detectedAt DESC")
    List<SanctionsUpdateHistory> findRecentUpdates(@Param("cutoffDate") LocalDateTime cutoffDate);

    /**
     * Count updates by list source
     *
     * @param listSource OFAC, EU, UN
     * @return Count of updates
     */
    long countByListSource(String listSource);

    /**
     * Count additions detected in a time period
     *
     * @param startDate Start of period
     * @param endDate End of period
     * @return Count of additions
     */
    @Query("SELECT COUNT(h) FROM SanctionsUpdateHistory h " +
           "WHERE h.changeType = 'ADDED' " +
           "AND h.detectedAt BETWEEN :startDate AND :endDate")
    long countAdditionsBetween(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /**
     * Find latest update for each list source
     *
     * @return List of latest updates per source
     */
    @Query("SELECT h FROM SanctionsUpdateHistory h " +
           "WHERE h.detectedAt = (" +
           "  SELECT MAX(h2.detectedAt) FROM SanctionsUpdateHistory h2 " +
           "  WHERE h2.listSource = h.listSource" +
           ") " +
           "ORDER BY h.listSource")
    List<SanctionsUpdateHistory> findLatestUpdatePerSource();

    /**
     * Find significant updates (large entity count changes)
     *
     * @param minEntityCount Minimum entity count threshold
     * @return List of significant updates
     */
    @Query("SELECT h FROM SanctionsUpdateHistory h " +
           "WHERE h.entityCount >= :minCount " +
           "ORDER BY h.detectedAt DESC")
    List<SanctionsUpdateHistory> findSignificantUpdates(@Param("minCount") int minEntityCount);

    /**
     * Get update statistics for a list source
     *
     * @param listSource OFAC, EU, UN
     * @return Statistics object
     */
    @Query("SELECT h.changeType, COUNT(h), SUM(h.entityCount) " +
           "FROM SanctionsUpdateHistory h " +
           "WHERE h.listSource = :listSource " +
           "GROUP BY h.changeType")
    List<Object[]> getUpdateStatistics(@Param("listSource") String listSource);
}
