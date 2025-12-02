package com.waqiti.compliance.repository;

import com.waqiti.compliance.model.SanctionedEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Sanctioned Entity Repository - CRITICAL for OFAC/AML Compliance
 *
 * This repository enables sanctions screening against OFAC, UN, EU lists
 * PRODUCTION-READY with comprehensive query methods
 */
@Repository
public interface SanctionedEntityRepository extends JpaRepository<SanctionedEntity, String> {

    /**
     * Find active sanctioned entities by name (case-insensitive fuzzy match)
     * CRITICAL: Used for customer onboarding sanctions screening
     */
    @Query("SELECT s FROM SanctionedEntity s WHERE " +
           "LOWER(s.entityName) LIKE LOWER(CONCAT('%', :name, '%')) " +
           "AND s.isActive = true")
    List<SanctionedEntity> findByEntityNameContainingIgnoreCase(@Param("name") String name);

    /**
     * Find by exact name match (case-insensitive)
     */
    @Query("SELECT s FROM SanctionedEntity s WHERE " +
           "LOWER(s.entityName) = LOWER(:name) AND s.isActive = true")
    Optional<SanctionedEntity> findByEntityNameIgnoreCase(@Param("name") String name);

    /**
     * Find by sanctions list and active status
     * Lists: OFAC_SDN, OFAC_NONSDN, UN_1267, EU_SANCTIONS, etc.
     */
    List<SanctionedEntity> findBySanctionsListAndIsActiveTrue(String sanctionsList);

    /**
     * Find by country code (ISO 3166-1 alpha-3)
     */
    List<SanctionedEntity> findByCountryAndIsActiveTrue(String country);

    /**
     * Search by name OR any alias
     * CRITICAL: Comprehensive name matching including known aliases
     */
    @Query("SELECT DISTINCT s FROM SanctionedEntity s " +
           "LEFT JOIN s.aliases a WHERE " +
           "(LOWER(s.entityName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
           "OR LOWER(a) LIKE LOWER(CONCAT('%', :searchTerm, '%'))) " +
           "AND s.isActive = true")
    List<SanctionedEntity> searchByNameOrAlias(@Param("searchTerm") String searchTerm);

    /**
     * Find entities added after specific date (for incremental updates)
     */
    List<SanctionedEntity> findByAddedDateAfterAndIsActiveTrue(LocalDateTime date);

    /**
     * Check if entity exists on any sanctions list
     * CRITICAL: Fast boolean check for sanctions screening
     */
    @Query("SELECT CASE WHEN COUNT(s) > 0 THEN true ELSE false END " +
           "FROM SanctionedEntity s WHERE " +
           "LOWER(s.entityName) = LOWER(:name) AND s.isActive = true")
    boolean existsByEntityName(@Param("name") String name);

    /**
     * Find by entity type (INDIVIDUAL, ORGANIZATION, VESSEL, AIRCRAFT)
     */
    List<SanctionedEntity> findByEntityTypeAndIsActiveTrue(String entityType);

    /**
     * Advanced search with multiple criteria
     * Used for administrative sanctions list management
     */
    @Query("SELECT s FROM SanctionedEntity s WHERE " +
           "(:entityType IS NULL OR s.entityType = :entityType) " +
           "AND (:sanctionsList IS NULL OR s.sanctionsList = :sanctionsList) " +
           "AND (:country IS NULL OR s.country = :country) " +
           "AND s.isActive = true")
    List<SanctionedEntity> searchByCriteria(
        @Param("entityType") String entityType,
        @Param("sanctionsList") String sanctionsList,
        @Param("country") String country
    );

    /**
     * Find recently removed entities (deactivated)
     */
    @Query("SELECT s FROM SanctionedEntity s WHERE " +
           "s.isActive = false AND s.removedDate >= :since")
    List<SanctionedEntity> findRecentlyRemoved(@Param("since") LocalDateTime since);

    /**
     * Get all active sanctions lists
     */
    @Query("SELECT DISTINCT s.sanctionsList FROM SanctionedEntity s WHERE s.isActive = true")
    List<String> findAllActiveSanctionsLists();

    /**
     * Count entities by sanctions list
     */
    @Query("SELECT s.sanctionsList, COUNT(s) FROM SanctionedEntity s " +
           "WHERE s.isActive = true GROUP BY s.sanctionsList")
    List<Object[]> countBySanctionsList();

    /**
     * Find entities by associate name (known associates/related parties)
     */
    @Query("SELECT DISTINCT s FROM SanctionedEntity s " +
           "JOIN s.associates a WHERE " +
           "LOWER(a.associateName) LIKE LOWER(CONCAT('%', :name, '%')) " +
           "AND s.isActive = true")
    List<SanctionedEntity> findByAssociateName(@Param("name") String name);
}
