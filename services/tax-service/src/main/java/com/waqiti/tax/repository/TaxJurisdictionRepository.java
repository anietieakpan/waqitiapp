package com.waqiti.tax.repository;

import com.waqiti.tax.entity.TaxJurisdiction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface for TaxJurisdiction entities.
 * Provides comprehensive data access methods for jurisdiction management.
 * 
 * @author Waqiti Tax Team
 * @since 2.0.0
 */
@Repository
public interface TaxJurisdictionRepository extends JpaRepository<TaxJurisdiction, Long> {

    /**
     * Find jurisdiction by unique code
     */
    Optional<TaxJurisdiction> findByCode(String code);

    /**
     * Find all active jurisdictions
     */
    List<TaxJurisdiction> findByActiveTrue();

    /**
     * Find jurisdictions by country code
     */
    List<TaxJurisdiction> findByCountryCodeAndActiveTrue(String countryCode);

    /**
     * Find jurisdictions by region code
     */
    List<TaxJurisdiction> findByRegionCodeAndActiveTrue(String regionCode);

    /**
     * Find jurisdictions by jurisdiction type
     */
    List<TaxJurisdiction> findByJurisdictionTypeAndActiveTrue(String jurisdictionType);

    /**
     * Find jurisdictions by primary tax type
     */
    List<TaxJurisdiction> findByPrimaryTaxTypeAndActiveTrue(String primaryTaxType);

    /**
     * Find jurisdictions supporting a specific transaction type
     */
    @Query("SELECT tj FROM TaxJurisdiction tj JOIN tj.supportedTransactionTypes stt " +
           "WHERE stt = :transactionType AND tj.active = true")
    List<TaxJurisdiction> findBySupportedTransactionType(@Param("transactionType") String transactionType);

    /**
     * Find jurisdictions with real-time calculation enabled
     */
    List<TaxJurisdiction> findByRealTimeCalculationTrueAndActiveTrue();

    /**
     * Find jurisdictions with external API integration
     */
    List<TaxJurisdiction> findByExternalApiAvailableTrueAndActiveTrue();

    /**
     * Find jurisdictions needing external sync
     */
    @Query("SELECT tj FROM TaxJurisdiction tj WHERE tj.active = true " +
           "AND tj.externalApiAvailable = true " +
           "AND (tj.lastExternalSync IS NULL OR tj.lastExternalSync < :cutoffTime)")
    List<TaxJurisdiction> findJurisdictionsNeedingSync(@Param("cutoffTime") LocalDateTime cutoffTime);

    /**
     * Find jurisdictions by filing frequency
     */
    @Query("SELECT tj FROM TaxJurisdiction tj JOIN tj.filingFrequencies ff " +
           "WHERE ff = :filingFrequency AND tj.active = true")
    List<TaxJurisdiction> findByFilingFrequency(@Param("filingFrequency") String filingFrequency);

    /**
     * Find jurisdictions with exemption category
     */
    @Query("SELECT tj FROM TaxJurisdiction tj JOIN tj.exemptionCategories ec " +
           "WHERE ec = :exemptionCategory AND tj.active = true")
    List<TaxJurisdiction> findByExemptionCategory(@Param("exemptionCategory") String exemptionCategory);

    /**
     * Search jurisdictions by name
     */
    @Query("SELECT tj FROM TaxJurisdiction tj WHERE tj.active = true " +
           "AND LOWER(tj.name) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    List<TaxJurisdiction> searchByName(@Param("searchTerm") String searchTerm);

    /**
     * Find jurisdictions by tax authority
     */
    List<TaxJurisdiction> findByTaxAuthorityAndActiveTrue(String taxAuthority);

    /**
     * Find jurisdictions by priority order
     */
    List<TaxJurisdiction> findByActiveTrueOrderByPriorityDesc();

    /**
     * Find jurisdictions by default currency
     */
    List<TaxJurisdiction> findByDefaultCurrencyAndActiveTrue(String defaultCurrency);

    /**
     * Count active jurisdictions by country
     */
    long countByCountryCodeAndActiveTrue(String countryCode);

    /**
     * Count active jurisdictions by jurisdiction type
     */
    long countByJurisdictionTypeAndActiveTrue(String jurisdictionType);

    /**
     * Find all distinct country codes
     */
    @Query("SELECT DISTINCT tj.countryCode FROM TaxJurisdiction tj WHERE tj.active = true")
    List<String> findDistinctCountryCodes();

    /**
     * Find all distinct region codes
     */
    @Query("SELECT DISTINCT tj.regionCode FROM TaxJurisdiction tj " +
           "WHERE tj.active = true AND tj.regionCode IS NOT NULL")
    List<String> findDistinctRegionCodes();

    /**
     * Find all distinct jurisdiction types
     */
    @Query("SELECT DISTINCT tj.jurisdictionType FROM TaxJurisdiction tj WHERE tj.active = true")
    List<String> findDistinctJurisdictionTypes();

    /**
     * Find all distinct currencies
     */
    @Query("SELECT DISTINCT tj.defaultCurrency FROM TaxJurisdiction tj WHERE tj.active = true")
    List<String> findDistinctCurrencies();

    /**
     * Find jurisdictions updated after a specific time
     */
    List<TaxJurisdiction> findByUpdatedAtAfterAndActiveTrue(LocalDateTime updatedAfter);

    /**
     * Find jurisdictions created by a specific user
     */
    List<TaxJurisdiction> findByCreatedByAndActiveTrue(String createdBy);

    /**
     * Find jurisdictions with configuration parameter
     */
    @Query("SELECT tj FROM TaxJurisdiction tj WHERE tj.active = true " +
           "AND JSON_EXTRACT(tj.configuration, '$.#{#configKey}') IS NOT NULL")
    List<TaxJurisdiction> findByConfigurationKey(@Param("configKey") String configKey);

    /**
     * Find jurisdictions with compliance requirement
     */
    @Query("SELECT tj FROM TaxJurisdiction tj WHERE tj.active = true " +
           "AND JSON_EXTRACT(tj.complianceRequirements, '$.#{#requirementKey}') IS NOT NULL")
    List<TaxJurisdiction> findByComplianceRequirement(@Param("requirementKey") String requirementKey);

    /**
     * Find jurisdictions by time zone
     */
    List<TaxJurisdiction> findByTimeZoneAndActiveTrue(String timeZone);

    /**
     * Find jurisdictions by language code
     */
    List<TaxJurisdiction> findByLanguageCodeAndActiveTrue(String languageCode);

    /**
     * Find jurisdictions with calculation precision
     */
    List<TaxJurisdiction> findByCalculationPrecisionAndActiveTrue(Integer calculationPrecision);

    /**
     * Find jurisdictions with rounding method
     */
    List<TaxJurisdiction> findByRoundingMethodAndActiveTrue(String roundingMethod);

    /**
     * Find paginated jurisdictions with filters
     */
    @Query("SELECT tj FROM TaxJurisdiction tj WHERE " +
           "(:countryCode IS NULL OR tj.countryCode = :countryCode) " +
           "AND (:jurisdictionType IS NULL OR tj.jurisdictionType = :jurisdictionType) " +
           "AND (:active IS NULL OR tj.active = :active) " +
           "AND (:defaultCurrency IS NULL OR tj.defaultCurrency = :defaultCurrency)")
    Page<TaxJurisdiction> findWithFilters(@Param("countryCode") String countryCode,
                                         @Param("jurisdictionType") String jurisdictionType,
                                         @Param("active") Boolean active,
                                         @Param("defaultCurrency") String defaultCurrency,
                                         Pageable pageable);

    /**
     * Update last external sync timestamp
     */
    @Query("UPDATE TaxJurisdiction tj SET tj.lastExternalSync = :syncTime " +
           "WHERE tj.id = :jurisdictionId")
    int updateLastExternalSync(@Param("jurisdictionId") Long jurisdictionId,
                              @Param("syncTime") LocalDateTime syncTime);

    /**
     * Deactivate jurisdictions by country (for bulk operations)
     */
    @Query("UPDATE TaxJurisdiction tj SET tj.active = false, tj.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE tj.countryCode = :countryCode")
    int deactivateByCountryCode(@Param("countryCode") String countryCode);

    /**
     * Get jurisdiction statistics
     */
    @Query("SELECT tj.jurisdictionType, COUNT(tj), " +
           "SUM(CASE WHEN tj.realTimeCalculation = true THEN 1 ELSE 0 END), " +
           "SUM(CASE WHEN tj.externalApiAvailable = true THEN 1 ELSE 0 END) " +
           "FROM TaxJurisdiction tj WHERE tj.active = true " +
           "GROUP BY tj.jurisdictionType")
    List<Object[]> getJurisdictionStatistics();

    /**
     * Find jurisdictions requiring external API configuration
     */
    @Query("SELECT tj FROM TaxJurisdiction tj WHERE tj.active = true " +
           "AND tj.externalApiAvailable = true " +
           "AND (tj.externalApiUrl IS NULL OR tj.externalApiAuth IS NULL)")
    List<TaxJurisdiction> findJurisdictionsNeedingApiConfiguration();

    /**
     * Find duplicate jurisdiction codes (for validation)
     */
    @Query("SELECT tj.code, COUNT(tj) FROM TaxJurisdiction tj " +
           "GROUP BY tj.code HAVING COUNT(tj) > 1")
    List<Object[]> findDuplicateJurisdictionCodes();

    /**
     * Find jurisdictions without supported transaction types
     */
    @Query("SELECT tj FROM TaxJurisdiction tj WHERE tj.active = true " +
           "AND (tj.supportedTransactionTypes IS NULL OR SIZE(tj.supportedTransactionTypes) = 0)")
    List<TaxJurisdiction> findJurisdictionsWithoutTransactionTypes();

    /**
     * Find jurisdictions without exemption categories
     */
    @Query("SELECT tj FROM TaxJurisdiction tj WHERE tj.active = true " +
           "AND (tj.exemptionCategories IS NULL OR SIZE(tj.exemptionCategories) = 0)")
    List<TaxJurisdiction> findJurisdictionsWithoutExemptions();

    /**
     * Find jurisdictions by metadata key
     */
    @Query("SELECT tj FROM TaxJurisdiction tj WHERE tj.active = true " +
           "AND JSON_EXTRACT(tj.metadata, '$.#{#metadataKey}') IS NOT NULL")
    List<TaxJurisdiction> findByMetadataKey(@Param("metadataKey") String metadataKey);

    /**
     * Find top jurisdictions by priority
     */
    List<TaxJurisdiction> findTop10ByActiveTrueOrderByPriorityDesc();

    /**
     * Find recently created jurisdictions
     */
    @Query("SELECT tj FROM TaxJurisdiction tj WHERE tj.active = true " +
           "AND tj.createdAt >= :since ORDER BY tj.createdAt DESC")
    List<TaxJurisdiction> findRecentlyCreated(@Param("since") LocalDateTime since);

    /**
     * Find recently updated jurisdictions
     */
    @Query("SELECT tj FROM TaxJurisdiction tj WHERE tj.active = true " +
           "AND tj.updatedAt >= :since ORDER BY tj.updatedAt DESC")
    List<TaxJurisdiction> findRecentlyUpdated(@Param("since") LocalDateTime since);

    /**
     * Check if code exists (excluding specific ID)
     */
    @Query("SELECT COUNT(tj) > 0 FROM TaxJurisdiction tj " +
           "WHERE tj.code = :code AND (:excludeId IS NULL OR tj.id != :excludeId)")
    boolean existsByCodeExcludingId(@Param("code") String code, @Param("excludeId") Long excludeId);
}