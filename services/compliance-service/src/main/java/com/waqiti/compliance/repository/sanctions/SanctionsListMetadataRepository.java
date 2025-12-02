package com.waqiti.compliance.repository.sanctions;

import com.waqiti.compliance.model.sanctions.SanctionsListMetadata;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Sanctions List Metadata
 *
 * Provides data access for sanctions list versions and metadata tracking.
 *
 * @author Waqiti Engineering Team
 * @version 2.0.0
 * @since 2025-11-19
 */
@Repository
public interface SanctionsListMetadataRepository extends JpaRepository<SanctionsListMetadata, UUID> {

    /**
     * Find latest active version for a given list source
     *
     * @param listSource OFAC, EU, or UN
     * @return Optional metadata for the active version
     */
    @Query("SELECT m FROM SanctionsListMetadata m " +
           "WHERE m.listSource = :listSource " +
           "AND m.isActive = true " +
           "ORDER BY m.versionDate DESC LIMIT 1")
    Optional<SanctionsListMetadata> findLatestActiveByListSource(@Param("listSource") String listSource);

    /**
     * Find all active versions for a given list source
     *
     * @param listSource OFAC, EU, or UN
     * @return List of active metadata records
     */
    @Query("SELECT m FROM SanctionsListMetadata m " +
           "WHERE m.listSource = :listSource " +
           "AND m.isActive = true " +
           "ORDER BY m.versionDate DESC")
    List<SanctionsListMetadata> findAllActiveByListSource(@Param("listSource") String listSource);

    /**
     * Find latest version for a given list source (active or inactive)
     *
     * @param listSource OFAC, EU, or UN
     * @return Optional metadata for the latest version
     */
    @Query("SELECT m FROM SanctionsListMetadata m " +
           "WHERE m.listSource = :listSource " +
           "ORDER BY m.versionDate DESC LIMIT 1")
    Optional<SanctionsListMetadata> findLatestByListSource(@Param("listSource") String listSource);

    /**
     * Find metadata by list source and version ID
     *
     * @param listSource OFAC, EU, or UN
     * @param versionId Version identifier
     * @return Optional metadata
     */
    Optional<SanctionsListMetadata> findByListSourceAndVersionId(String listSource, String versionId);

    /**
     * Find all versions for a list source
     *
     * @param listSource OFAC, EU, or UN
     * @return List of all metadata records
     */
    List<SanctionsListMetadata> findByListSourceOrderByVersionDateDesc(String listSource);

    /**
     * Find metadata by processing status
     *
     * @param status PENDING, PROCESSING, COMPLETED, FAILED
     * @return List of metadata records with given status
     */
    List<SanctionsListMetadata> findByProcessingStatus(String status);

    /**
     * Find metadata downloaded after a certain date
     *
     * @param afterDate Date threshold
     * @return List of metadata records
     */
    List<SanctionsListMetadata> findByDownloadTimestampAfter(LocalDateTime afterDate);

    /**
     * Count total active list versions across all sources
     *
     * @return Count of active versions
     */
    @Query("SELECT COUNT(m) FROM SanctionsListMetadata m WHERE m.isActive = true")
    long countActiveVersions();

    /**
     * Find metadata by source file hash (for duplicate detection)
     *
     * @param hash SHA-256 hash
     * @return Optional metadata
     */
    Optional<SanctionsListMetadata> findBySourceFileHash(String hash);
}
