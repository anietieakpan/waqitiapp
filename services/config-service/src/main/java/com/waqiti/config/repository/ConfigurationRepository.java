package com.waqiti.config.repository;

import com.waqiti.config.domain.Configuration;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Configuration entities with comprehensive query capabilities
 */
@Repository
public interface ConfigurationRepository extends JpaRepository<Configuration, UUID> {

    /**
     * Find active configuration by key
     */
    Optional<Configuration> findByKeyAndActiveTrue(String key);

    /**
     * Find configuration by key (including inactive)
     */
    Optional<Configuration> findByKey(String key);

    /**
     * Check if configuration exists by key
     */
    boolean existsByKey(String key);

    /**
     * Find active configurations by service
     */
    List<Configuration> findByServiceAndActiveTrue(String service);

    /**
     * Find active configurations by environment
     */
    List<Configuration> findByEnvironmentAndActiveTrue(String environment);

    /**
     * Find all active configurations
     */
    List<Configuration> findByActiveTrue();

    /**
     * Find configurations by service and environment
     */
    List<Configuration> findByServiceAndEnvironmentAndActiveTrue(String service, String environment);

    /**
     * Find sensitive configurations
     */
    List<Configuration> findBySensitiveTrueAndActiveTrue();

    /**
     * Find encrypted configurations
     */
    List<Configuration> findByEncryptedTrueAndActiveTrue();

    /**
     * Complex search query with multiple optional filters
     */
    // SECURITY FIX: Properly escape wildcards to prevent wildcard injection attacks
    @Query("SELECT c FROM Configuration c WHERE " +
           "(:key IS NULL OR LOWER(c.key) LIKE LOWER(CONCAT('%', REPLACE(REPLACE(REPLACE(:key, '\\\\', '\\\\\\\\'), '%', '\\\\%'), '_', '\\\\_'), '%'))) AND " +
           "(:service IS NULL OR c.service = :service) AND " +
           "(:environment IS NULL OR c.environment = :environment) AND " +
           "(:active IS NULL OR c.active = :active) " +
           "ORDER BY c.lastModified DESC")
    Page<Configuration> searchConfigurations(
        @Param("key") String key,
        @Param("service") String service,
        @Param("environment") String environment,
        @Param("active") Boolean active,
        Pageable pageable
    );

    /**
     * Find configurations modified after timestamp
     */
    @Query("SELECT c FROM Configuration c WHERE c.lastModified > :timestamp AND c.active = true")
    List<Configuration> findModifiedAfter(@Param("timestamp") java.time.Instant timestamp);

    /**
     * Find configurations by key pattern
     */
    @Query("SELECT c FROM Configuration c WHERE c.key LIKE :pattern AND c.active = true")
    List<Configuration> findByKeyPattern(@Param("pattern") String pattern);

    /**
     * Count configurations by service
     */
    @Query("SELECT COUNT(c) FROM Configuration c WHERE c.service = :service AND c.active = true")
    long countByService(@Param("service") String service);

    /**
     * Count configurations by environment
     */
    @Query("SELECT COUNT(c) FROM Configuration c WHERE c.environment = :environment AND c.active = true")
    long countByEnvironment(@Param("environment") String environment);

    /**
     * Find configurations that need refresh (cache TTL expired)
     */
    @Query("SELECT c FROM Configuration c WHERE c.lastModified < :refreshThreshold AND c.active = true")
    List<Configuration> findConfigsNeedingRefresh(@Param("refreshThreshold") java.time.Instant refreshThreshold);

    /**
     * Find orphaned configurations (no service assigned)
     */
    @Query("SELECT c FROM Configuration c WHERE (c.service IS NULL OR c.service = '') AND c.active = true")
    List<Configuration> findOrphanedConfigurations();

    /**
     * Batch update last modified timestamp
     */
    @Query("UPDATE Configuration c SET c.lastModified = :timestamp WHERE c.id IN :ids")
    void updateLastModifiedBatch(@Param("ids") List<UUID> ids, @Param("timestamp") java.time.Instant timestamp);

    /**
     * Soft delete configurations by service
     */
    @Query("UPDATE Configuration c SET c.active = false, c.deletedAt = :deletedAt WHERE c.service = :service")
    void softDeleteByService(@Param("service") String service, @Param("deletedAt") java.time.Instant deletedAt);

    /**
     * Find duplicate keys across services
     */
    @Query("SELECT c.key, COUNT(c) FROM Configuration c WHERE c.active = true GROUP BY c.key HAVING COUNT(c) > 1")
    List<Object[]> findDuplicateKeys();
}