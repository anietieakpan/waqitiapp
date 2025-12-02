package com.waqiti.compliance.repository.sanctions;

import com.waqiti.compliance.model.sanctions.SanctionedEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Sanctioned Entities
 *
 * Provides data access for sanctioned individuals, entities, vessels, and aircraft
 * from OFAC, EU, and UN sanctions lists.
 *
 * CRITICAL FOR COMPLIANCE:
 * All sanctions screening operations depend on this repository for entity lookups.
 *
 * @author Waqiti Engineering Team
 * @version 2.0.0
 * @since 2025-11-19
 */
@Repository
public interface SanctionedEntityRepository extends JpaRepository<SanctionedEntity, UUID> {

    /**
     * Find entity by source ID and list metadata
     *
     * @param listMetadataId List metadata UUID
     * @param sourceId Original source ID
     * @return Optional entity
     */
    Optional<SanctionedEntity> findByListMetadataIdAndSourceId(UUID listMetadataId, String sourceId);

    /**
     * Find all active entities for a list metadata
     *
     * @param listMetadataId List metadata UUID
     * @return List of entities
     */
    @Query("SELECT e FROM SanctionedEntity e " +
           "WHERE e.listMetadataId = :listMetadataId " +
           "AND e.isActive = true")
    List<SanctionedEntity> findActiveEntitiesByListMetadata(@Param("listMetadataId") UUID listMetadataId);

    /**
     * Search entities by normalized name (fuzzy matching support)
     *
     * @param nameNormalized Normalized name
     * @return List of matching entities
     */
    @Query("SELECT e FROM SanctionedEntity e " +
           "WHERE e.nameNormalized LIKE %:nameNormalized% " +
           "AND e.isActive = true")
    List<SanctionedEntity> searchByNormalizedName(@Param("nameNormalized") String nameNormalized);

    /**
     * Find entities by entity type
     *
     * @param entityType INDIVIDUAL, ENTITY, VESSEL, AIRCRAFT
     * @return List of entities
     */
    @Query("SELECT e FROM SanctionedEntity e " +
           "WHERE e.entityType = :entityType " +
           "AND e.isActive = true")
    List<SanctionedEntity> findActiveByEntityType(@Param("entityType") String entityType);

    /**
     * Find entities by nationality
     *
     * @param nationality ISO 3166-1 alpha-3 country code
     * @return List of entities
     */
    @Query("SELECT e FROM SanctionedEntity e " +
           "WHERE e.nationality = :nationality " +
           "AND e.isActive = true")
    List<SanctionedEntity> findActiveByNationality(@Param("nationality") String nationality);

    /**
     * Find entities by passport number
     *
     * @param passportNumber Passport number
     * @return List of matching entities
     */
    @Query("SELECT e FROM SanctionedEntity e " +
           "WHERE e.passportNumber = :passportNumber " +
           "AND e.isActive = true")
    List<SanctionedEntity> findActiveByPassportNumber(@Param("passportNumber") String passportNumber);

    /**
     * Find entities by national ID number
     *
     * @param nationalIdNumber National ID number
     * @return List of matching entities
     */
    @Query("SELECT e FROM SanctionedEntity e " +
           "WHERE e.nationalIdNumber = :nationalIdNumber " +
           "AND e.isActive = true")
    List<SanctionedEntity> findActiveByNationalIdNumber(@Param("nationalIdNumber") String nationalIdNumber);

    /**
     * Find vessel entities by call sign
     *
     * @param callSign Vessel call sign
     * @return List of matching vessels
     */
    @Query("SELECT e FROM SanctionedEntity e " +
           "WHERE e.vesselCallSign = :callSign " +
           "AND e.entityType = 'VESSEL' " +
           "AND e.isActive = true")
    List<SanctionedEntity> findActiveVesselsByCallSign(@Param("callSign") String callSign);

    /**
     * Find aircraft entities by tail number
     *
     * @param tailNumber Aircraft tail number
     * @return List of matching aircraft
     */
    @Query("SELECT e FROM SanctionedEntity e " +
           "WHERE e.aircraftTailNumber = :tailNumber " +
           "AND e.entityType = 'AIRCRAFT' " +
           "AND e.isActive = true")
    List<SanctionedEntity> findActiveAircraftByTailNumber(@Param("tailNumber") String tailNumber);

    /**
     * Full-text search on primary name
     *
     * @param searchTerm Search term
     * @return List of matching entities
     */
    @Query(value = "SELECT * FROM sanctioned_entities e " +
                   "WHERE to_tsvector('english', e.primary_name) @@ plainto_tsquery('english', :searchTerm) " +
                   "AND e.is_active = true",
           nativeQuery = true)
    List<SanctionedEntity> fullTextSearchByName(@Param("searchTerm") String searchTerm);

    /**
     * Count entities by list source (via join)
     *
     * @param listSource OFAC, EU, UN
     * @return Count of entities
     */
    @Query("SELECT COUNT(e) FROM SanctionedEntity e " +
           "JOIN SanctionsListMetadata m ON e.listMetadataId = m.id " +
           "WHERE m.listSource = :listSource " +
           "AND e.isActive = true " +
           "AND m.isActive = true")
    long countByListSource(@Param("listSource") String listSource);

    /**
     * Find all active entities across all lists
     *
     * @return List of all active entities
     */
    @Query("SELECT e FROM SanctionedEntity e WHERE e.isActive = true")
    List<SanctionedEntity> findAllActive();

    /**
     * Find entities by program name
     *
     * @param programName Program name (e.g., "IRAN", "SYRIA")
     * @return List of entities
     */
    @Query("SELECT e FROM SanctionedEntity e " +
           "WHERE e.programName = :programName " +
           "AND e.isActive = true")
    List<SanctionedEntity> findActiveByProgramName(@Param("programName") String programName);
}
