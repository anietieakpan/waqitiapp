package com.waqiti.config.repository;

import com.waqiti.config.domain.ServiceConfig;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for ServiceConfig entities with service-specific configuration management
 */
@Repository
public interface ServiceConfigRepository extends JpaRepository<ServiceConfig, UUID> {

    /**
     * Find service configuration by service name
     */
    Optional<ServiceConfig> findByServiceName(String serviceName);

    /**
     * Find active service configurations
     */
    List<ServiceConfig> findByActiveTrue();

    /**
     * Find service configurations by environment
     */
    List<ServiceConfig> findByEnvironment(String environment);

    /**
     * Find service configurations by environment and active status
     */
    List<ServiceConfig> findByEnvironmentAndActiveTrue(String environment);

    /**
     * Check if service configuration exists
     */
    boolean existsByServiceName(String serviceName);

    /**
     * Find service configurations that need refresh
     */
    @Query("SELECT sc FROM ServiceConfig sc WHERE sc.lastRefresh < :threshold AND sc.active = true")
    List<ServiceConfig> findConfigsNeedingRefresh(@Param("threshold") Instant threshold);

    /**
     * Find service configurations by version
     */
    List<ServiceConfig> findByVersionAndActiveTrue(String version);

    /**
     * Search service configurations
     */
    // SECURITY FIX: Properly escape wildcards to prevent wildcard injection attacks
    @Query("SELECT sc FROM ServiceConfig sc WHERE " +
           "(:serviceName IS NULL OR LOWER(sc.serviceName) LIKE LOWER(CONCAT('%', REPLACE(REPLACE(REPLACE(:serviceName, '\\\\', '\\\\\\\\'), '%', '\\\\%'), '_', '\\\\_'), '%'))) AND " +
           "(:environment IS NULL OR sc.environment = :environment) AND " +
           "(:version IS NULL OR sc.version = :version) AND " +
           "(:active IS NULL OR sc.active = :active) " +
           "ORDER BY sc.lastRefresh DESC")
    Page<ServiceConfig> searchServiceConfigs(
        @Param("serviceName") String serviceName,
        @Param("environment") String environment,
        @Param("version") String version,
        @Param("active") Boolean active,
        Pageable pageable
    );

    /**
     * Find services with critical configurations
     */
    @Query("SELECT sc FROM ServiceConfig sc WHERE sc.critical = true AND sc.active = true")
    List<ServiceConfig> findCriticalServiceConfigs();

    /**
     * Find services with high refresh frequency
     */
    @Query("SELECT sc FROM ServiceConfig sc WHERE sc.refreshIntervalSeconds < :threshold AND sc.active = true")
    List<ServiceConfig> findHighFrequencyRefreshServices(@Param("threshold") Integer threshold);

    /**
     * Find stale service configurations
     */
    @Query("SELECT sc FROM ServiceConfig sc WHERE " +
           "sc.lastRefresh < :staleThreshold AND " +
           "sc.active = true " +
           "ORDER BY sc.lastRefresh ASC")
    List<ServiceConfig> findStaleServiceConfigs(@Param("staleThreshold") Instant staleThreshold);

    /**
     * Count service configurations by environment
     */
    @Query("SELECT COUNT(sc) FROM ServiceConfig sc WHERE sc.environment = :environment AND sc.active = true")
    long countByEnvironment(@Param("environment") String environment);

    /**
     * Count active service configurations
     */
    @Query("SELECT COUNT(sc) FROM ServiceConfig sc WHERE sc.active = true")
    long countActiveServices();

    /**
     * Find service configurations modified after timestamp
     */
    @Query("SELECT sc FROM ServiceConfig sc WHERE sc.lastModified > :timestamp ORDER BY sc.lastModified DESC")
    List<ServiceConfig> findModifiedAfter(@Param("timestamp") Instant timestamp);

    /**
     * Update last refresh timestamp
     */
    @Modifying
    @Query("UPDATE ServiceConfig sc SET sc.lastRefresh = :refreshTime WHERE sc.serviceName = :serviceName")
    void updateLastRefresh(@Param("serviceName") String serviceName, @Param("refreshTime") Instant refreshTime);

    /**
     * Update last refresh timestamp for multiple services
     */
    @Modifying
    @Query("UPDATE ServiceConfig sc SET sc.lastRefresh = :refreshTime WHERE sc.serviceName IN :serviceNames")
    void updateLastRefreshBatch(@Param("serviceNames") List<String> serviceNames, @Param("refreshTime") Instant refreshTime);

    /**
     * Find services by configuration count threshold
     */
    @Query("SELECT sc FROM ServiceConfig sc WHERE sc.configurationCount >= :threshold AND sc.active = true")
    List<ServiceConfig> findServicesWithManyConfigurations(@Param("threshold") Integer threshold);

    /**
     * Get service statistics
     */
    @Query("SELECT " +
           "COUNT(sc) as totalServices, " +
           "SUM(CASE WHEN sc.active = true THEN 1 ELSE 0 END) as activeServices, " +
           "SUM(CASE WHEN sc.critical = true THEN 1 ELSE 0 END) as criticalServices, " +
           "AVG(sc.configurationCount) as avgConfigsPerService, " +
           "AVG(sc.refreshIntervalSeconds) as avgRefreshInterval " +
           "FROM ServiceConfig sc")
    Object[] getServiceStatistics();

    /**
     * Find services with no recent refresh
     */
    @Query("SELECT sc FROM ServiceConfig sc WHERE " +
           "sc.lastRefresh IS NULL OR " +
           "sc.lastRefresh < :threshold " +
           "ORDER BY sc.lastRefresh ASC NULLS FIRST")
    List<ServiceConfig> findServicesWithNoRecentRefresh(@Param("threshold") Instant threshold);

    /**
     * Find services by configuration key pattern
     */
    // SECURITY FIX: Properly escape wildcards to prevent wildcard injection attacks
    @Query("SELECT DISTINCT sc FROM ServiceConfig sc " +
           "JOIN sc.configurations conf " +
           "WHERE LOWER(conf.key) LIKE LOWER(CONCAT('%', REPLACE(REPLACE(REPLACE(:keyPattern, '\\\\', '\\\\\\\\'), '%', '\\\\%'), '_', '\\\\_'), '%')) " +
           "AND sc.active = true")
    List<ServiceConfig> findByConfigurationKeyPattern(@Param("keyPattern") String keyPattern);

    /**
     * Find duplicate service names across environments
     */
    @Query("SELECT sc.serviceName, sc.environment, COUNT(sc) FROM ServiceConfig sc " +
           "WHERE sc.active = true " +
           "GROUP BY sc.serviceName, sc.environment " +
           "HAVING COUNT(sc) > 1")
    List<Object[]> findDuplicateServiceConfigurations();

    /**
     * Find services missing critical configurations
     */
    @Query("SELECT sc FROM ServiceConfig sc WHERE " +
           "sc.active = true AND " +
           "sc.serviceName NOT IN (" +
           "  SELECT c.service FROM Configuration c WHERE " +
           "  c.key IN :criticalConfigKeys AND c.active = true" +
           ")")
    List<ServiceConfig> findServicesMissingCriticalConfigs(@Param("criticalConfigKeys") List<String> criticalConfigKeys);

    /**
     * Get configuration health status
     */
    @Query("SELECT sc.serviceName, sc.environment, " +
           "CASE " +
           "  WHEN sc.lastRefresh > :healthyThreshold THEN 'HEALTHY' " +
           "  WHEN sc.lastRefresh > :warningThreshold THEN 'WARNING' " +
           "  ELSE 'CRITICAL' " +
           "END as healthStatus " +
           "FROM ServiceConfig sc WHERE sc.active = true")
    List<Object[]> getConfigurationHealthStatus(
        @Param("healthyThreshold") Instant healthyThreshold,
        @Param("warningThreshold") Instant warningThreshold
    );

    /**
     * Soft delete service configuration
     */
    @Modifying
    @Query("UPDATE ServiceConfig sc SET sc.active = false, sc.lastModified = :timestamp WHERE sc.serviceName = :serviceName")
    void softDeleteByServiceName(@Param("serviceName") String serviceName, @Param("timestamp") Instant timestamp);

    /**
     * Reactivate service configuration
     */
    @Modifying
    @Query("UPDATE ServiceConfig sc SET sc.active = true, sc.lastModified = :timestamp WHERE sc.serviceName = :serviceName")
    void reactivateServiceConfig(@Param("serviceName") String serviceName, @Param("timestamp") Instant timestamp);

    /**
     * Update configuration count for service
     */
    @Modifying
    @Query("UPDATE ServiceConfig sc SET sc.configurationCount = :count, sc.lastModified = :timestamp " +
           "WHERE sc.serviceName = :serviceName")
    void updateConfigurationCount(@Param("serviceName") String serviceName, @Param("count") Integer count, @Param("timestamp") Instant timestamp);
}