package com.waqiti.config.repository;

import com.waqiti.config.domain.FeatureFlag;
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
import java.util.Set;
import java.util.UUID;

/**
 * Repository for FeatureFlag entities with advanced querying capabilities
 */
@Repository
public interface FeatureFlagRepository extends JpaRepository<FeatureFlag, UUID> {

    /**
     * Find active feature flag by name
     */
    Optional<FeatureFlag> findByNameAndActiveTrue(String name);

    /**
     * Find feature flag by name (including inactive)
     */
    Optional<FeatureFlag> findByName(String name);

    /**
     * Check if feature flag exists by name
     */
    boolean existsByName(String name);

    /**
     * Find all active feature flags
     */
    List<FeatureFlag> findByActiveTrue();

    /**
     * Find enabled feature flags
     */
    List<FeatureFlag> findByEnabledTrueAndActiveTrue();

    /**
     * Find feature flags by environment
     */
    @Query("SELECT f FROM FeatureFlag f WHERE :environment MEMBER OF f.environments AND f.active = true")
    List<FeatureFlag> findByEnvironment(@Param("environment") String environment);

    /**
     * Find feature flags targeting specific user
     */
    @Query("SELECT f FROM FeatureFlag f WHERE :userId MEMBER OF f.targetUsers AND f.active = true")
    List<FeatureFlag> findByTargetUser(@Param("userId") String userId);

    /**
     * Find feature flags with percentage rollout
     */
    @Query("SELECT f FROM FeatureFlag f WHERE f.percentageRollout > 0 AND f.active = true")
    List<FeatureFlag> findWithPercentageRollout();

    /**
     * Find feature flags with custom rules
     */
    @Query("SELECT f FROM FeatureFlag f WHERE f.rules IS NOT EMPTY AND f.active = true")
    List<FeatureFlag> findWithCustomRules();

    /**
     * Search feature flags with filters
     */
    // SECURITY FIX: Properly escape wildcards to prevent wildcard injection attacks
    @Query("SELECT f FROM FeatureFlag f WHERE " +
           "(:name IS NULL OR LOWER(f.name) LIKE LOWER(CONCAT('%', REPLACE(REPLACE(REPLACE(:name, '\\\\', '\\\\\\\\'), '%', '\\\\%'), '_', '\\\\_'), '%'))) AND " +
           "(:enabled IS NULL OR f.enabled = :enabled) AND " +
           "(:environment IS NULL OR :environment MEMBER OF f.environments) AND " +
           "(:active IS NULL OR f.active = :active) " +
           "ORDER BY f.lastModified DESC")
    Page<FeatureFlag> searchFeatureFlags(
        @Param("name") String name,
        @Param("enabled") Boolean enabled,
        @Param("environment") String environment,
        @Param("active") Boolean active,
        Pageable pageable
    );

    /**
     * Find feature flags modified after timestamp
     */
    @Query("SELECT f FROM FeatureFlag f WHERE f.lastModified > :timestamp AND f.active = true")
    List<FeatureFlag> findModifiedAfter(@Param("timestamp") Instant timestamp);

    /**
     * Find feature flags by name pattern
     */
    @Query("SELECT f FROM FeatureFlag f WHERE f.name LIKE :pattern AND f.active = true")
    List<FeatureFlag> findByNamePattern(@Param("pattern") String pattern);

    /**
     * Count enabled feature flags
     */
    @Query("SELECT COUNT(f) FROM FeatureFlag f WHERE f.enabled = true AND f.active = true")
    long countEnabledFlags();

    /**
     * Count feature flags by environment
     */
    @Query("SELECT COUNT(f) FROM FeatureFlag f WHERE :environment MEMBER OF f.environments AND f.active = true")
    long countByEnvironment(@Param("environment") String environment);

    /**
     * Find feature flags with high rollout percentage
     */
    @Query("SELECT f FROM FeatureFlag f WHERE f.percentageRollout >= :threshold AND f.active = true")
    List<FeatureFlag> findWithHighRollout(@Param("threshold") Integer threshold);

    /**
     * Find stale feature flags (not modified for a while)
     */
    @Query("SELECT f FROM FeatureFlag f WHERE f.lastModified < :threshold AND f.active = true")
    List<FeatureFlag> findStaleFlags(@Param("threshold") Instant threshold);

    /**
     * Find feature flags without environments (global flags)
     */
    @Query("SELECT f FROM FeatureFlag f WHERE f.environments IS EMPTY AND f.active = true")
    List<FeatureFlag> findGlobalFlags();

    /**
     * Find feature flags with specific target users count
     */
    @Query("SELECT f FROM FeatureFlag f WHERE SIZE(f.targetUsers) >= :minUsers AND f.active = true")
    List<FeatureFlag> findWithMinimumTargetUsers(@Param("minUsers") int minUsers);

    /**
     * Batch update enabled status
     */
    @Modifying
    @Query("UPDATE FeatureFlag f SET f.enabled = :enabled, f.lastModified = :timestamp WHERE f.id IN :ids")
    void updateEnabledStatusBatch(@Param("ids") List<UUID> ids, @Param("enabled") boolean enabled, @Param("timestamp") Instant timestamp);

    /**
     * Soft delete feature flags by name pattern
     */
    @Modifying
    @Query("UPDATE FeatureFlag f SET f.active = false, f.deletedAt = :deletedAt WHERE f.name LIKE :pattern")
    void softDeleteByNamePattern(@Param("pattern") String pattern, @Param("deletedAt") Instant deletedAt);

    /**
     * Find duplicate feature flag names
     */
    @Query("SELECT f.name, COUNT(f) FROM FeatureFlag f WHERE f.active = true GROUP BY f.name HAVING COUNT(f) > 1")
    List<Object[]> findDuplicateNames();

    /**
     * Get feature flag statistics
     */
    @Query("SELECT " +
           "COUNT(f) as total, " +
           "SUM(CASE WHEN f.enabled = true THEN 1 ELSE 0 END) as enabled, " +
           "SUM(CASE WHEN f.enabled = false THEN 1 ELSE 0 END) as disabled, " +
           "SUM(CASE WHEN f.percentageRollout > 0 THEN 1 ELSE 0 END) as withRollout " +
           "FROM FeatureFlag f WHERE f.active = true")
    Object[] getFeatureFlagStatistics();

    /**
     * Find conflicting feature flags (same name, different environments)
     */
    @Query("SELECT f1 FROM FeatureFlag f1, FeatureFlag f2 WHERE " +
           "f1.name = f2.name AND f1.id != f2.id AND " +
           "f1.active = true AND f2.active = true AND " +
           "f1.enabled != f2.enabled")
    List<FeatureFlag> findConflictingFlags();
}