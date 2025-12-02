package com.waqiti.legal.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Legal Audit Repository
 *
 * Complete data access layer for LegalAudit entities with custom query methods
 * Supports audit trail, compliance audits, and legal action tracking
 *
 * Note: This repository is designed for a LegalAudit entity that should be created
 * to track legal audits and audit trails
 *
 * @author Waqiti Legal Team
 * @version 1.0.0
 * @since 2025-10-18
 */
@Repository
public interface LegalAuditRepository extends JpaRepository<LegalAudit, UUID> {

    /**
     * Find audit by audit ID
     */
    Optional<LegalAudit> findByAuditId(String auditId);

    /**
     * Find audits by entity type
     */
    @Query("SELECT a FROM LegalAudit a WHERE a.entityType = :entityType")
    List<LegalAudit> findByEntityType(@Param("entityType") String entityType);

    /**
     * Find audits by entity ID
     */
    @Query("SELECT a FROM LegalAudit a WHERE a.entityId = :entityId " +
           "ORDER BY a.auditTimestamp DESC")
    List<LegalAudit> findByEntityId(@Param("entityId") String entityId);

    /**
     * Find audits by action type
     */
    @Query("SELECT a FROM LegalAudit a WHERE a.actionType = :actionType")
    List<LegalAudit> findByActionType(@Param("actionType") String actionType);

    /**
     * Find audits by performed by user
     */
    @Query("SELECT a FROM LegalAudit a WHERE a.performedBy = :userId " +
           "ORDER BY a.auditTimestamp DESC")
    List<LegalAudit> findByPerformedBy(@Param("userId") String userId);

    /**
     * Find audits by user role
     */
    @Query("SELECT a FROM LegalAudit a WHERE a.userRole = :userRole")
    List<LegalAudit> findByUserRole(@Param("userRole") String userRole);

    /**
     * Find audits within date range
     */
    @Query("SELECT a FROM LegalAudit a WHERE a.auditTimestamp BETWEEN :startDateTime AND :endDateTime " +
           "ORDER BY a.auditTimestamp DESC")
    List<LegalAudit> findByAuditTimestampBetween(
        @Param("startDateTime") LocalDateTime startDateTime,
        @Param("endDateTime") LocalDateTime endDateTime
    );

    /**
     * Find audits by severity level
     */
    @Query("SELECT a FROM LegalAudit a WHERE a.severityLevel = :severityLevel " +
           "ORDER BY a.auditTimestamp DESC")
    List<LegalAudit> findBySeverityLevel(@Param("severityLevel") String severityLevel);

    /**
     * Find critical audits
     */
    @Query("SELECT a FROM LegalAudit a WHERE a.severityLevel IN ('CRITICAL', 'HIGH') " +
           "ORDER BY a.severityLevel DESC, a.auditTimestamp DESC")
    List<LegalAudit> findCriticalAudits();

    /**
     * Find audits by category
     */
    @Query("SELECT a FROM LegalAudit a WHERE a.category = :category")
    List<LegalAudit> findByCategory(@Param("category") String category);

    /**
     * Find compliance audits
     */
    @Query("SELECT a FROM LegalAudit a WHERE a.category = 'COMPLIANCE'")
    List<LegalAudit> findComplianceAudits();

    /**
     * Find security audits
     */
    @Query("SELECT a FROM LegalAudit a WHERE a.category = 'SECURITY'")
    List<LegalAudit> findSecurityAudits();

    /**
     * Find access audits
     */
    @Query("SELECT a FROM LegalAudit a WHERE a.category = 'ACCESS'")
    List<LegalAudit> findAccessAudits();

    /**
     * Find modification audits
     */
    @Query("SELECT a FROM LegalAudit a WHERE a.actionType IN ('CREATE', 'UPDATE', 'DELETE') " +
           "ORDER BY a.auditTimestamp DESC")
    List<LegalAudit> findModificationAudits();

    /**
     * Find audits by IP address
     */
    @Query("SELECT a FROM LegalAudit a WHERE a.ipAddress = :ipAddress")
    List<LegalAudit> findByIpAddress(@Param("ipAddress") String ipAddress);

    /**
     * Find audits by session ID
     */
    @Query("SELECT a FROM LegalAudit a WHERE a.sessionId = :sessionId " +
           "ORDER BY a.auditTimestamp ASC")
    List<LegalAudit> findBySessionId(@Param("sessionId") String sessionId);

    /**
     * Find failed action audits
     */
    @Query("SELECT a FROM LegalAudit a WHERE a.actionSuccessful = false " +
           "ORDER BY a.auditTimestamp DESC")
    List<LegalAudit> findFailedActions();

    /**
     * Find successful action audits
     */
    @Query("SELECT a FROM LegalAudit a WHERE a.actionSuccessful = true")
    List<LegalAudit> findSuccessfulActions();

    /**
     * Find audits with specific result code
     */
    @Query("SELECT a FROM LegalAudit a WHERE a.resultCode = :resultCode")
    List<LegalAudit> findByResultCode(@Param("resultCode") String resultCode);

    /**
     * Find audits by application module
     */
    @Query("SELECT a FROM LegalAudit a WHERE a.applicationModule = :module")
    List<LegalAudit> findByApplicationModule(@Param("module") String module);

    /**
     * Find audits with exceptions or errors
     */
    @Query("SELECT a FROM LegalAudit a WHERE a.exceptionOccurred = true " +
           "ORDER BY a.auditTimestamp DESC")
    List<LegalAudit> findAuditsWithExceptions();

    /**
     * Find audits by regulatory requirement
     */
    @Query("SELECT a FROM LegalAudit a WHERE a.regulatoryRequirement = :requirement")
    List<LegalAudit> findByRegulatoryRequirement(@Param("requirement") String requirement);

    /**
     * Find audits requiring investigation
     */
    @Query("SELECT a FROM LegalAudit a WHERE a.requiresInvestigation = true " +
           "AND a.investigationCompleted = false")
    List<LegalAudit> findRequiringInvestigation();

    /**
     * Find audits with completed investigation
     */
    @Query("SELECT a FROM LegalAudit a WHERE a.requiresInvestigation = true " +
           "AND a.investigationCompleted = true")
    List<LegalAudit> findWithCompletedInvestigation();

    /**
     * Find audits flagged for review
     */
    @Query("SELECT a FROM LegalAudit a WHERE a.flaggedForReview = true " +
           "AND a.reviewCompleted = false")
    List<LegalAudit> findFlaggedForReview();

    /**
     * Find audits by data classification
     */
    @Query("SELECT a FROM LegalAudit a WHERE a.dataClassification = :classification")
    List<LegalAudit> findByDataClassification(@Param("classification") String classification);

    /**
     * Find audits involving sensitive data
     */
    @Query("SELECT a FROM LegalAudit a WHERE a.dataClassification IN ('CONFIDENTIAL', 'HIGHLY_CONFIDENTIAL', 'RESTRICTED')")
    List<LegalAudit> findSensitiveDataAudits();

    /**
     * Find audits by geographic location
     */
    @Query("SELECT a FROM LegalAudit a WHERE a.geographicLocation = :location")
    List<LegalAudit> findByGeographicLocation(@Param("location") String location);

    /**
     * Find audits by device type
     */
    @Query("SELECT a FROM LegalAudit a WHERE a.deviceType = :deviceType")
    List<LegalAudit> findByDeviceType(@Param("deviceType") String deviceType);

    /**
     * Find audits by user agent
     */
    @Query("SELECT a FROM LegalAudit a WHERE a.userAgent LIKE CONCAT('%', :userAgentPattern, '%')")
    List<LegalAudit> findByUserAgentContaining(@Param("userAgentPattern") String userAgentPattern);

    /**
     * Find external access audits
     */
    @Query("SELECT a FROM LegalAudit a WHERE a.externalAccess = true " +
           "ORDER BY a.auditTimestamp DESC")
    List<LegalAudit> findExternalAccessAudits();

    /**
     * Find internal access audits
     */
    @Query("SELECT a FROM LegalAudit a WHERE a.externalAccess = false OR a.externalAccess IS NULL")
    List<LegalAudit> findInternalAccessAudits();

    /**
     * Find audits by retention period
     */
    @Query("SELECT a FROM LegalAudit a WHERE a.retentionPeriodYears = :years")
    List<LegalAudit> findByRetentionPeriodYears(@Param("years") Integer years);

    /**
     * Find audits eligible for deletion (past retention period)
     */
    @Query("SELECT a FROM LegalAudit a WHERE a.auditTimestamp < :thresholdDate " +
           "AND a.retentionPeriodYears IS NOT NULL")
    List<LegalAudit> findEligibleForDeletion(@Param("thresholdDate") LocalDateTime thresholdDate);

    /**
     * Count audits by action type
     */
    @Query("SELECT COUNT(a) FROM LegalAudit a WHERE a.actionType = :actionType")
    long countByActionType(@Param("actionType") String actionType);

    /**
     * Count audits by entity type
     */
    @Query("SELECT COUNT(a) FROM LegalAudit a WHERE a.entityType = :entityType")
    long countByEntityType(@Param("entityType") String entityType);

    /**
     * Count audits by user within date range
     */
    @Query("SELECT COUNT(a) FROM LegalAudit a WHERE a.performedBy = :userId " +
           "AND a.auditTimestamp BETWEEN :startDateTime AND :endDateTime")
    long countByUserAndDateRange(
        @Param("userId") String userId,
        @Param("startDateTime") LocalDateTime startDateTime,
        @Param("endDateTime") LocalDateTime endDateTime
    );

    /**
     * Count failed actions by user
     */
    @Query("SELECT COUNT(a) FROM LegalAudit a WHERE a.performedBy = :userId " +
           "AND a.actionSuccessful = false")
    long countFailedActionsByUser(@Param("userId") String userId);

    /**
     * Check if audit exists for entity and action
     */
    boolean existsByEntityIdAndActionType(String entityId, String actionType);

    /**
     * Find recent audits for entity (last N days)
     */
    @Query("SELECT a FROM LegalAudit a WHERE a.entityId = :entityId " +
           "AND a.auditTimestamp >= :thresholdDateTime " +
           "ORDER BY a.auditTimestamp DESC")
    List<LegalAudit> findRecentAuditsForEntity(
        @Param("entityId") String entityId,
        @Param("thresholdDateTime") LocalDateTime thresholdDateTime
    );

    /**
     * Find audits requiring immediate attention
     */
    @Query("SELECT a FROM LegalAudit a WHERE " +
           "(a.severityLevel IN ('CRITICAL', 'HIGH')) " +
           "OR (a.actionSuccessful = false AND a.severityLevel = 'MEDIUM') " +
           "OR (a.requiresInvestigation = true AND a.investigationCompleted = false) " +
           "OR (a.flaggedForReview = true AND a.reviewCompleted = false) " +
           "ORDER BY a.severityLevel DESC, a.auditTimestamp DESC")
    List<LegalAudit> findRequiringImmediateAttention();

    /**
     * Search audits by description
     */
    @Query("SELECT a FROM LegalAudit a WHERE LOWER(a.actionDescription) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    List<LegalAudit> searchByDescription(@Param("searchTerm") String searchTerm);

    /**
     * Get audit statistics by action type
     */
    @Query("SELECT a.actionType, COUNT(*), " +
           "COUNT(CASE WHEN a.actionSuccessful = true THEN 1 END), " +
           "COUNT(CASE WHEN a.actionSuccessful = false THEN 1 END) " +
           "FROM LegalAudit a " +
           "WHERE a.auditTimestamp BETWEEN :startDateTime AND :endDateTime " +
           "GROUP BY a.actionType")
    List<Object[]> getAuditStatisticsByActionType(
        @Param("startDateTime") LocalDateTime startDateTime,
        @Param("endDateTime") LocalDateTime endDateTime
    );

    /**
     * Get audit statistics by user
     */
    @Query("SELECT a.performedBy, COUNT(*), " +
           "COUNT(CASE WHEN a.actionSuccessful = true THEN 1 END), " +
           "COUNT(CASE WHEN a.severityLevel IN ('CRITICAL', 'HIGH') THEN 1 END) " +
           "FROM LegalAudit a " +
           "WHERE a.auditTimestamp BETWEEN :startDateTime AND :endDateTime " +
           "GROUP BY a.performedBy")
    List<Object[]> getAuditStatisticsByUser(
        @Param("startDateTime") LocalDateTime startDateTime,
        @Param("endDateTime") LocalDateTime endDateTime
    );

    /**
     * Find compliance violations
     */
    @Query("SELECT a FROM LegalAudit a WHERE a.complianceViolation = true " +
           "ORDER BY a.auditTimestamp DESC")
    List<LegalAudit> findComplianceViolations();

    /**
     * Find audits by before and after values (data changes)
     */
    @Query("SELECT a FROM LegalAudit a WHERE a.beforeValue IS NOT NULL " +
           "AND a.afterValue IS NOT NULL " +
           "AND a.entityId = :entityId")
    List<LegalAudit> findDataChangesForEntity(@Param("entityId") String entityId);
}

/**
 * Placeholder class for LegalAudit entity
 * This should be created as a proper domain entity in com.waqiti.legal.domain package
 */
class LegalAudit {
    private UUID id;
    private String auditId;
    private String entityType;
    private String entityId;
    private String actionType;
    private String performedBy;
    private String userRole;
    private LocalDateTime auditTimestamp;
    private String severityLevel;
    private String category;
    private String ipAddress;
    private String sessionId;
    private Boolean actionSuccessful;
    private String resultCode;
    private String applicationModule;
    private Boolean exceptionOccurred;
    private String regulatoryRequirement;
    private Boolean requiresInvestigation;
    private Boolean investigationCompleted;
    private Boolean flaggedForReview;
    private Boolean reviewCompleted;
    private String dataClassification;
    private String geographicLocation;
    private String deviceType;
    private String userAgent;
    private Boolean externalAccess;
    private Integer retentionPeriodYears;
    private String actionDescription;
    private Boolean complianceViolation;
    private String beforeValue;
    private String afterValue;
}
