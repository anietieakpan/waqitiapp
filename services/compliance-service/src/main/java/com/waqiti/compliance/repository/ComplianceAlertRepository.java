package com.waqiti.compliance.repository;

import com.waqiti.compliance.domain.ComplianceAlert;
import com.waqiti.compliance.dto.AMLAlertFilter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Repository for ComplianceAlert entities
 * Provides data access methods for compliance alerts and AML screening
 */
@Repository
public interface ComplianceAlertRepository extends JpaRepository<ComplianceAlert, UUID> {

    /**
     * Find alerts by entity ID
     */
    List<ComplianceAlert> findByRelatedEntityId(UUID entityId);

    /**
     * Find alerts by alert type
     */
    List<ComplianceAlert> findByAlertType(String alertType);

    /**
     * Find alerts by status
     */
    List<ComplianceAlert> findByStatus(ComplianceAlert.Status status);

    /**
     * Find alerts by priority
     */
    List<ComplianceAlert> findByPriority(ComplianceAlert.Priority priority);

    /**
     * Find alerts created within a date range
     */
    List<ComplianceAlert> findByCreatedAtBetween(LocalDateTime startDate, LocalDateTime endDate);

    /**
     * Find unresolved alerts for an entity
     */
    @Query("SELECT ca FROM ComplianceAlert ca WHERE ca.relatedEntityId = :entityId AND ca.status != 'RESOLVED'")
    List<ComplianceAlert> findUnresolvedAlertsByEntityId(@Param("entityId") UUID entityId);

    /**
     * Find high priority alerts
     */
    @Query("SELECT ca FROM ComplianceAlert ca WHERE ca.priority = 'HIGH' AND ca.status != 'RESOLVED'")
    List<ComplianceAlert> findHighPriorityUnresolvedAlerts();

    /**
     * Find alerts by type and status
     */
    List<ComplianceAlert> findByAlertTypeAndStatus(String alertType, ComplianceAlert.Status status);

    /**
     * Find alerts with filters (complex query for AML alerts)
     */
    @Query("SELECT ca FROM ComplianceAlert ca WHERE " +
           "(:#{#filter.alertType} IS NULL OR ca.alertType = :#{#filter.alertType}) AND " +
           "(:#{#filter.status} IS NULL OR ca.status = :#{#filter.status}) AND " +
           "(:#{#filter.priority} IS NULL OR ca.priority = :#{#filter.priority}) AND " +
           "(:#{#filter.entityId} IS NULL OR ca.relatedEntityId = :#{#filter.entityId}) AND " +
           "(:#{#filter.startDate} IS NULL OR ca.createdAt >= :#{#filter.startDate}) AND " +
           "(:#{#filter.endDate} IS NULL OR ca.createdAt <= :#{#filter.endDate})")
    Page<ComplianceAlert> findWithFilters(@Param("filter") AMLAlertFilter filter, Pageable pageable);

    /**
     * Count alerts by status
     */
    long countByStatus(ComplianceAlert.Status status);

    /**
     * Count alerts by type
     */
    long countByAlertType(String alertType);

    /**
     * Find recent alerts for an entity
     */
    @Query("SELECT ca FROM ComplianceAlert ca WHERE ca.relatedEntityId = :entityId " +
           "AND ca.createdAt >= :sinceDate ORDER BY ca.createdAt DESC")
    List<ComplianceAlert> findRecentAlertsByEntityId(@Param("entityId") UUID entityId, 
                                                    @Param("sinceDate") LocalDateTime sinceDate);

    /**
     * Find alerts requiring action
     */
    @Query("SELECT ca FROM ComplianceAlert ca WHERE ca.status IN ('NEW', 'IN_PROGRESS') " +
           "AND ca.priority IN ('HIGH', 'CRITICAL')")
    List<ComplianceAlert> findAlertsRequiringAction();

    /**
     * Find alerts by resolved by user
     */
    List<ComplianceAlert> findByResolvedBy(String resolvedBy);

    /**
     * Check if alert exists by external reference
     */
    boolean existsByExternalReference(String externalReference);

    /**
     * Find alerts with specific metadata key
     */
    @Query("SELECT ca FROM ComplianceAlert ca WHERE JSON_EXTRACT(ca.metadata, :key) IS NOT NULL")
    List<ComplianceAlert> findAlertsWithMetadataKey(@Param("key") String key);

    /**
     * Find alerts escalated to manual review
     */
    @Query("SELECT ca FROM ComplianceAlert ca WHERE ca.description LIKE '%manual review%' " +
           "OR ca.alertType LIKE '%MANUAL%'")
    List<ComplianceAlert> findManualReviewAlerts();

    /**
     * Delete old resolved alerts (for data cleanup)
     */
    @Query("DELETE FROM ComplianceAlert ca WHERE ca.status = 'RESOLVED' " +
           "AND ca.resolvedAt < :cutoffDate")
    void deleteOldResolvedAlerts(@Param("cutoffDate") LocalDateTime cutoffDate);
}