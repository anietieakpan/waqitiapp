package com.waqiti.tax.repository;

import com.waqiti.tax.entity.TaxRule;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface for TaxRule entities.
 * Provides comprehensive data access methods for tax rule management.
 * 
 * @author Waqiti Tax Team
 * @since 2.0.0
 */
@Repository
public interface TaxRuleRepository extends JpaRepository<TaxRule, Long> {

    /**
     * Find active tax rules by jurisdiction and transaction type
     */
    List<TaxRule> findByJurisdictionAndTransactionTypeAndActiveTrue(String jurisdiction, String transactionType);

    /**
     * Find all active tax rules by jurisdiction
     */
    List<TaxRule> findByJurisdictionAndActiveTrue(String jurisdiction);

    /**
     * Find tax rule by unique rule code
     */
    Optional<TaxRule> findByRuleCode(String ruleCode);

    /**
     * Find tax rules by jurisdiction, transaction type, and date range
     */
    @Query("SELECT tr FROM TaxRule tr WHERE tr.jurisdiction = :jurisdiction " +
           "AND tr.transactionType = :transactionType " +
           "AND tr.active = true " +
           "AND (tr.effectiveFrom IS NULL OR tr.effectiveFrom <= :date) " +
           "AND (tr.effectiveTo IS NULL OR tr.effectiveTo >= :date)")
    List<TaxRule> findApplicableRules(@Param("jurisdiction") String jurisdiction,
                                     @Param("transactionType") String transactionType,
                                     @Param("date") LocalDate date);

    /**
     * Find tax rules applicable for a specific amount and date
     */
    @Query("SELECT tr FROM TaxRule tr WHERE tr.jurisdiction = :jurisdiction " +
           "AND tr.transactionType = :transactionType " +
           "AND tr.active = true " +
           "AND (tr.effectiveFrom IS NULL OR tr.effectiveFrom <= :date) " +
           "AND (tr.effectiveTo IS NULL OR tr.effectiveTo >= :date) " +
           "AND (tr.minimumAmount IS NULL OR tr.minimumAmount <= :amount) " +
           "AND (tr.maximumAmount IS NULL OR tr.maximumAmount >= :amount) " +
           "ORDER BY tr.priority DESC")
    List<TaxRule> findApplicableRulesForAmount(@Param("jurisdiction") String jurisdiction,
                                              @Param("transactionType") String transactionType,
                                              @Param("amount") BigDecimal amount,
                                              @Param("date") LocalDate date);

    /**
     * Find tax rules by tax type
     */
    List<TaxRule> findByTaxTypeAndActiveTrue(String taxType);

    /**
     * Find tax rules by calculation type
     */
    List<TaxRule> findByCalculationTypeAndActiveTrue(String calculationType);

    /**
     * Find tax rules requiring manual review
     */
    List<TaxRule> findByRequiresManualReviewTrueAndActiveTrue();

    /**
     * Find tax rules that need external updates
     */
    @Query("SELECT tr FROM TaxRule tr WHERE tr.ruleSource = 'EXTERNAL_API' " +
           "AND (tr.lastExternalUpdate IS NULL OR tr.lastExternalUpdate < :cutoffTime)")
    List<TaxRule> findRulesNeedingExternalUpdate(@Param("cutoffTime") java.time.LocalDateTime cutoffTime);

    /**
     * Find tax rules by jurisdiction and priority order
     */
    List<TaxRule> findByJurisdictionAndActiveTrueOrderByPriorityDesc(String jurisdiction);

    /**
     * Find tax rules with confidence level below threshold
     */
    @Query("SELECT tr FROM TaxRule tr WHERE tr.active = true " +
           "AND tr.confidenceLevel < :threshold")
    List<TaxRule> findLowConfidenceRules(@Param("threshold") BigDecimal threshold);

    /**
     * Find overlapping tax rules for a jurisdiction and transaction type
     */
    @Query("SELECT tr FROM TaxRule tr WHERE tr.jurisdiction = :jurisdiction " +
           "AND tr.transactionType = :transactionType " +
           "AND tr.active = true " +
           "AND tr.id != :excludeId " +
           "AND ((tr.effectiveFrom IS NULL AND tr.effectiveTo IS NULL) " +
           "OR (tr.effectiveFrom <= :endDate AND tr.effectiveTo >= :startDate))")
    List<TaxRule> findOverlappingRules(@Param("jurisdiction") String jurisdiction,
                                      @Param("transactionType") String transactionType,
                                      @Param("startDate") LocalDate startDate,
                                      @Param("endDate") LocalDate endDate,
                                      @Param("excludeId") Long excludeId);

    /**
     * Count active rules by jurisdiction
     */
    long countByJurisdictionAndActiveTrue(String jurisdiction);

    /**
     * Find rules by business category
     */
    @Query("SELECT tr FROM TaxRule tr JOIN tr.businessCategories bc " +
           "WHERE bc = :businessCategory AND tr.active = true")
    List<TaxRule> findByBusinessCategory(@Param("businessCategory") String businessCategory);

    /**
     * Find rules by customer type
     */
    @Query("SELECT tr FROM TaxRule tr JOIN tr.customerTypes ct " +
           "WHERE ct = :customerType AND tr.active = true")
    List<TaxRule> findByCustomerType(@Param("customerType") String customerType);

    /**
     * Find rules expiring soon
     */
    @Query("SELECT tr FROM TaxRule tr WHERE tr.active = true " +
           "AND tr.effectiveTo IS NOT NULL " +
           "AND tr.effectiveTo BETWEEN :startDate AND :endDate")
    List<TaxRule> findRulesExpiringSoon(@Param("startDate") LocalDate startDate,
                                       @Param("endDate") LocalDate endDate);

    /**
     * Find rules becoming effective soon
     */
    @Query("SELECT tr FROM TaxRule tr WHERE tr.active = true " +
           "AND tr.effectiveFrom IS NOT NULL " +
           "AND tr.effectiveFrom BETWEEN :startDate AND :endDate")
    List<TaxRule> findRulesBecomingEffective(@Param("startDate") LocalDate startDate,
                                            @Param("endDate") LocalDate endDate);

    /**
     * Search rules by description or legal reference
     */
    @Query("SELECT tr FROM TaxRule tr WHERE tr.active = true " +
           "AND (LOWER(tr.description) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
           "OR LOWER(tr.legalReference) LIKE LOWER(CONCAT('%', :searchTerm, '%')))")
    List<TaxRule> searchByDescriptionOrLegalReference(@Param("searchTerm") String searchTerm);

    /**
     * Find rules by version
     */
    List<TaxRule> findByVersionAndActiveTrue(Integer version);

    /**
     * Find latest version of rules for a jurisdiction
     */
    @Query("SELECT tr FROM TaxRule tr WHERE tr.jurisdiction = :jurisdiction " +
           "AND tr.active = true " +
           "AND tr.version = (SELECT MAX(tr2.version) FROM TaxRule tr2 " +
           "WHERE tr2.jurisdiction = tr.jurisdiction " +
           "AND tr2.transactionType = tr.transactionType " +
           "AND tr2.taxType = tr.taxType " +
           "AND tr2.active = true)")
    List<TaxRule> findLatestVersionRules(@Param("jurisdiction") String jurisdiction);

    /**
     * Find all jurisdictions with active rules
     */
    @Query("SELECT DISTINCT tr.jurisdiction FROM TaxRule tr WHERE tr.active = true")
    List<String> findActiveJurisdictions();

    /**
     * Find all transaction types with active rules
     */
    @Query("SELECT DISTINCT tr.transactionType FROM TaxRule tr WHERE tr.active = true")
    List<String> findActiveTransactionTypes();

    /**
     * Find all tax types with active rules
     */
    @Query("SELECT DISTINCT tr.taxType FROM TaxRule tr WHERE tr.active = true")
    List<String> findActiveTaxTypes();

    /**
     * Find rules updated after a specific date
     */
    List<TaxRule> findByUpdatedAtAfterAndActiveTrue(java.time.LocalDateTime updatedAfter);

    /**
     * Find rules created by a specific user
     */
    List<TaxRule> findByCreatedByAndActiveTrue(String createdBy);

    /**
     * Get paginated rules with filtering
     */
    @Query("SELECT tr FROM TaxRule tr WHERE " +
           "(:jurisdiction IS NULL OR tr.jurisdiction = :jurisdiction) " +
           "AND (:transactionType IS NULL OR tr.transactionType = :transactionType) " +
           "AND (:taxType IS NULL OR tr.taxType = :taxType) " +
           "AND (:active IS NULL OR tr.active = :active)")
    Page<TaxRule> findWithFilters(@Param("jurisdiction") String jurisdiction,
                                 @Param("transactionType") String transactionType,
                                 @Param("taxType") String taxType,
                                 @Param("active") Boolean active,
                                 Pageable pageable);

    /**
     * Update rules to inactive by jurisdiction (for rule replacement)
     */
    @Modifying
    @Query("UPDATE TaxRule tr SET tr.active = false, tr.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE tr.jurisdiction = :jurisdiction AND tr.active = true")
    int deactivateRulesByJurisdiction(@Param("jurisdiction") String jurisdiction);

    /**
     * Count rules by calculation type
     */
    @Query("SELECT tr.calculationType, COUNT(tr) FROM TaxRule tr " +
           "WHERE tr.active = true GROUP BY tr.calculationType")
    List<Object[]> countByCalculationType();

    /**
     * Find rules with effective dates in range
     */
    @Query("SELECT tr FROM TaxRule tr WHERE tr.active = true " +
           "AND ((tr.effectiveFrom BETWEEN :startDate AND :endDate) " +
           "OR (tr.effectiveTo BETWEEN :startDate AND :endDate) " +
           "OR (tr.effectiveFrom <= :startDate AND tr.effectiveTo >= :endDate))")
    List<TaxRule> findRulesInDateRange(@Param("startDate") LocalDate startDate,
                                      @Param("endDate") LocalDate endDate);

    /**
     * Find duplicate rules (same jurisdiction, transaction type, tax type)
     */
    @Query("SELECT tr FROM TaxRule tr WHERE tr.active = true " +
           "AND EXISTS (SELECT tr2 FROM TaxRule tr2 WHERE tr2.active = true " +
           "AND tr2.jurisdiction = tr.jurisdiction " +
           "AND tr2.transactionType = tr.transactionType " +
           "AND tr2.taxType = tr.taxType " +
           "AND tr2.id != tr.id)")
    List<TaxRule> findDuplicateRules();
}