package com.waqiti.compliance.repository.sanctions;

import com.waqiti.compliance.model.sanctions.SanctionedEntityAlias;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for Sanctioned Entity Aliases
 *
 * Provides data access for alternative names, aliases, and name variants
 * of sanctioned entities.
 *
 * CRITICAL FOR FUZZY MATCHING:
 * Aliases are essential for detecting sanctioned individuals using alternate names.
 *
 * @author Waqiti Engineering Team
 * @version 2.0.0
 * @since 2025-11-19
 */
@Repository
public interface SanctionedEntityAliasRepository extends JpaRepository<SanctionedEntityAlias, UUID> {

    /**
     * Find all aliases for a sanctioned entity
     *
     * @param sanctionedEntityId Entity UUID
     * @return List of aliases
     */
    List<SanctionedEntityAlias> findBySanctionedEntityId(UUID sanctionedEntityId);

    /**
     * Find aliases by normalized name (for fuzzy matching)
     *
     * @param normalizedName Normalized alias name
     * @return List of matching aliases
     */
    @Query("SELECT a FROM SanctionedEntityAlias a " +
           "WHERE a.aliasNameNormalized LIKE %:normalizedName%")
    List<SanctionedEntityAlias> searchByNormalizedName(@Param("normalizedName") String normalizedName);

    /**
     * Find aliases by alias type
     *
     * @param aliasType AKA, FKA, NICKNAME, WEAK_AKA
     * @return List of aliases
     */
    List<SanctionedEntityAlias> findByAliasType(String aliasType);

    /**
     * Find aliases by quality rating
     *
     * @param quality STRONG, WEAK, LOW
     * @return List of aliases
     */
    List<SanctionedEntityAlias> findByAliasQuality(String quality);

    /**
     * Find primary aliases for an entity
     *
     * @param sanctionedEntityId Entity UUID
     * @return List of primary aliases
     */
    @Query("SELECT a FROM SanctionedEntityAlias a " +
           "WHERE a.sanctionedEntityId = :entityId " +
           "AND a.isPrimary = true")
    List<SanctionedEntityAlias> findPrimaryAliasesByEntityId(@Param("entityId") UUID sanctionedEntityId);

    /**
     * Find strong quality aliases for an entity
     *
     * @param sanctionedEntityId Entity UUID
     * @return List of strong aliases
     */
    @Query("SELECT a FROM SanctionedEntityAlias a " +
           "WHERE a.sanctionedEntityId = :entityId " +
           "AND a.aliasQuality = 'STRONG'")
    List<SanctionedEntityAlias> findStrongAliasesByEntityId(@Param("entityId") UUID sanctionedEntityId);

    /**
     * Full-text search on alias names
     *
     * @param searchTerm Search term
     * @return List of matching aliases
     */
    @Query(value = "SELECT * FROM sanctioned_entity_aliases a " +
                   "WHERE to_tsvector('english', a.alias_name) @@ plainto_tsquery('english', :searchTerm)",
           nativeQuery = true)
    List<SanctionedEntityAlias> fullTextSearchByAliasName(@Param("searchTerm") String searchTerm);

    /**
     * Count total aliases in database
     *
     * @return Total count
     */
    @Query("SELECT COUNT(a) FROM SanctionedEntityAlias a")
    long count();

    /**
     * Find aliases by script type (for multilingual support)
     *
     * @param scriptType LATIN, CYRILLIC, ARABIC, etc.
     * @return List of aliases
     */
    List<SanctionedEntityAlias> findByScriptType(String scriptType);

    /**
     * Find aliases by language code
     *
     * @param languageCode ISO 639-2 language code
     * @return List of aliases
     */
    List<SanctionedEntityAlias> findByLanguageCode(String languageCode);
}
