package com.waqiti.config.repository;

import com.waqiti.config.domain.ConfigAudit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Repository for ConfigAudit entities with comprehensive audit querying
 */
@Repository
public interface ConfigAuditRepository extends JpaRepository<ConfigAudit, UUID> {

    /**
     * Find audit entries by config key ordered by timestamp descending
     */
    List<ConfigAudit> findByConfigKeyOrderByTimestampDesc(String configKey);

    /**
     * Find audit entries by config key with pagination
     */
    Page<ConfigAudit> findByConfigKeyOrderByTimestampDesc(String configKey, Pageable pageable);

    /**
     * Find audit entries performed by user
     */
    List<ConfigAudit> findByPerformedByOrderByTimestampDesc(String performedBy);

    /**
     * Find audit entries by action type
     */
    List<ConfigAudit> findByActionOrderByTimestampDesc(String action);

    /**
     * Find audit entries by service
     */
    List<ConfigAudit> findByServiceOrderByTimestampDesc(String service);

    /**
     * Find audit entries by environment
     */
    List<ConfigAudit> findByEnvironmentOrderByTimestampDesc(String environment);

    /**
     * Find audit entries within time range
     */
    @Query("SELECT a FROM ConfigAudit a WHERE a.timestamp BETWEEN :startTime AND :endTime ORDER BY a.timestamp DESC")
    List<ConfigAudit> findByTimestampBetween(@Param("startTime") Instant startTime, @Param("endTime") Instant endTime);

    /**
     * Find audit entries within time range with pagination
     */
    @Query("SELECT a FROM ConfigAudit a WHERE a.timestamp BETWEEN :startTime AND :endTime ORDER BY a.timestamp DESC")
    Page<ConfigAudit> findByTimestampBetween(@Param("startTime") Instant startTime, @Param("endTime") Instant endTime, Pageable pageable);

    /**
     * Complex search query for audit entries
     */
    // SECURITY FIX: Properly escape wildcards to prevent wildcard injection attacks
    @Query("SELECT a FROM ConfigAudit a WHERE " +
           "(:configKey IS NULL OR LOWER(a.configKey) LIKE LOWER(CONCAT('%', REPLACE(REPLACE(REPLACE(:configKey, '\\\\', '\\\\\\\\'), '%', '\\\\%'), '_', '\\\\_'), '%'))) AND " +
           "(:action IS NULL OR a.action = :action) AND " +
           "(:performedBy IS NULL OR a.performedBy = :performedBy) AND " +
           "(:service IS NULL OR a.service = :service) AND " +
           "(:environment IS NULL OR a.environment = :environment) AND " +
           "(:startTime IS NULL OR a.timestamp >= :startTime) AND " +
           "(:endTime IS NULL OR a.timestamp <= :endTime) " +
           "ORDER BY a.timestamp DESC")
    Page<ConfigAudit> searchAuditEntries(
        @Param("configKey") String configKey,
        @Param("action") String action,
        @Param("performedBy") String performedBy,
        @Param("service") String service,
        @Param("environment") String environment,
        @Param("startTime") Instant startTime,
        @Param("endTime") Instant endTime,
        Pageable pageable
    );

    /**
     * Find recent audit entries
     */
    @Query("SELECT a FROM ConfigAudit a WHERE a.timestamp > :threshold ORDER BY a.timestamp DESC")
    List<ConfigAudit> findRecentEntries(@Param("threshold") Instant threshold);

    /**
     * Find most active users (by number of configuration changes)
     */
    @Query("SELECT a.performedBy, COUNT(a) as changeCount FROM ConfigAudit a " +
           "WHERE a.timestamp >= :startTime " +
           "GROUP BY a.performedBy " +
           "ORDER BY changeCount DESC")
    List<Object[]> findMostActiveUsers(@Param("startTime") Instant startTime);

    /**
     * Find most changed configurations
     */
    @Query("SELECT a.configKey, COUNT(a) as changeCount FROM ConfigAudit a " +
           "WHERE a.timestamp >= :startTime " +
           "GROUP BY a.configKey " +
           "ORDER BY changeCount DESC")
    List<Object[]> findMostChangedConfigurations(@Param("startTime") Instant startTime);

    /**
     * Count audit entries by action type
     */
    @Query("SELECT a.action, COUNT(a) FROM ConfigAudit a " +
           "WHERE a.timestamp >= :startTime " +
           "GROUP BY a.action " +
           "ORDER BY COUNT(a) DESC")
    List<Object[]> countByActionType(@Param("startTime") Instant startTime);

    /**
     * Count audit entries by service
     */
    @Query("SELECT a.service, COUNT(a) FROM ConfigAudit a " +
           "WHERE a.timestamp >= :startTime AND a.service IS NOT NULL " +
           "GROUP BY a.service " +
           "ORDER BY COUNT(a) DESC")
    List<Object[]> countByService(@Param("startTime") Instant startTime);

    /**
     * Count audit entries by environment
     */
    @Query("SELECT a.environment, COUNT(a) FROM ConfigAudit a " +
           "WHERE a.timestamp >= :startTime AND a.environment IS NOT NULL " +
           "GROUP BY a.environment " +
           "ORDER BY COUNT(a) DESC")
    List<Object[]> countByEnvironment(@Param("startTime") Instant startTime);

    /**
     * Find sensitive configuration changes
     */
    @Query("SELECT a FROM ConfigAudit a WHERE " +
           "a.configKey IN (SELECT c.key FROM Configuration c WHERE c.sensitive = true) " +
           "ORDER BY a.timestamp DESC")
    List<ConfigAudit> findSensitiveConfigChanges();

    /**
     * Find configuration deletions
     */
    List<ConfigAudit> findByActionAndTimestampAfterOrderByTimestampDesc(String action, Instant timestamp);

    /**
     * Find bulk operations
     */
    @Query("SELECT a FROM ConfigAudit a WHERE a.action IN ('BULK_UPDATE', 'BULK_IMPORT', 'REFRESH_ALL') " +
           "ORDER BY a.timestamp DESC")
    List<ConfigAudit> findBulkOperations();

    /**
     * Find configuration changes that might need rollback
     */
    @Query("SELECT a FROM ConfigAudit a WHERE " +
           "a.action IN ('UPDATE', 'DELETE') AND " +
           "a.timestamp >= :threshold " +
           "ORDER BY a.timestamp DESC")
    List<ConfigAudit> findRecentChangesForRollback(@Param("threshold") Instant threshold);

    /**
     * Get audit statistics for time period
     */
    @Query("SELECT " +
           "COUNT(a) as totalChanges, " +
           "COUNT(DISTINCT a.configKey) as uniqueConfigs, " +
           "COUNT(DISTINCT a.performedBy) as uniqueUsers, " +
           "COUNT(DISTINCT a.service) as uniqueServices " +
           "FROM ConfigAudit a " +
           "WHERE a.timestamp BETWEEN :startTime AND :endTime")
    Object[] getAuditStatistics(@Param("startTime") Instant startTime, @Param("endTime") Instant endTime);

    /**
     * Delete old audit entries (for cleanup)
     */
    @Query("DELETE FROM ConfigAudit a WHERE a.timestamp < :threshold")
    void deleteOldEntries(@Param("threshold") Instant threshold);

    /**
     * Find configuration changes by day
     */
    @Query("SELECT DATE(a.timestamp) as changeDate, COUNT(a) as changeCount " +
           "FROM ConfigAudit a " +
           "WHERE a.timestamp >= :startDate " +
           "GROUP BY DATE(a.timestamp) " +
           "ORDER BY changeDate DESC")
    List<Object[]> findChangesByDay(@Param("startDate") Instant startDate);

    /**
     * Find failed operations (if failure tracking is implemented)
     */
    @Query("SELECT a FROM ConfigAudit a WHERE " +
           "a.details IS NOT NULL AND " +
           "CAST(a.details AS string) LIKE '%error%' OR " +
           "CAST(a.details AS string) LIKE '%failed%' " +
           "ORDER BY a.timestamp DESC")
    List<ConfigAudit> findFailedOperations();

    /**
     * Count total audit entries
     */
    @Query("SELECT COUNT(a) FROM ConfigAudit a")
    long getTotalAuditCount();

    /**
     * Find last change for each configuration
     */
    @Query("SELECT a FROM ConfigAudit a WHERE " +
           "a.timestamp = (SELECT MAX(a2.timestamp) FROM ConfigAudit a2 WHERE a2.configKey = a.configKey) " +
           "ORDER BY a.timestamp DESC")
    List<ConfigAudit> findLastChangePerConfiguration();
}