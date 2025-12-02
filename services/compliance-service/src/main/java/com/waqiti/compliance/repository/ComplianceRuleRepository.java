package com.waqiti.compliance.repository;

import com.waqiti.compliance.domain.ComplianceRule;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for ComplianceRule entities
 * Provides data access methods for compliance rule management and enforcement
 */
@Repository
public interface ComplianceRuleRepository extends JpaRepository<ComplianceRule, UUID> {

    /**
     * Find rule by rule name
     */
    Optional<ComplianceRule> findByRuleName(String ruleName);

    /**
     * Check if rule exists by name
     */
    boolean existsByRuleName(String ruleName);

    /**
     * Find all active rules
     */
    List<ComplianceRule> findAllActive();

    /**
     * Find rules by rule type
     */
    List<ComplianceRule> findByRuleType(String ruleType);

    /**
     * Find active rules by type
     */
    @Query("SELECT cr FROM ComplianceRule cr WHERE cr.ruleType = :ruleType AND cr.isActive = true")
    List<ComplianceRule> findActiveByRuleType(@Param("ruleType") String ruleType);

    /**
     * Find rules by priority
     */
    List<ComplianceRule> findByPriority(String priority);

    /**
     * Find rules by action type
     */
    List<ComplianceRule> findByAction(String action);

    /**
     * Find rules created by specific user
     */
    List<ComplianceRule> findByCreatedBy(String createdBy);

    /**
     * Find rules updated by specific user
     */
    List<ComplianceRule> findByUpdatedBy(String updatedBy);

    /**
     * Find rules created within date range
     */
    List<ComplianceRule> findByCreatedAtBetween(LocalDateTime startDate, LocalDateTime endDate);

    /**
     * Find rules updated within date range
     */
    List<ComplianceRule> findByUpdatedAtBetween(LocalDateTime startDate, LocalDateTime endDate);

    /**
     * Find high priority active rules
     */
    @Query("SELECT cr FROM ComplianceRule cr WHERE cr.priority IN ('HIGH', 'CRITICAL') " +
           "AND cr.isActive = true ORDER BY cr.priority DESC, cr.createdAt ASC")
    List<ComplianceRule> findHighPriorityActiveRules();

    /**
     * Find rules by condition pattern
     * SECURITY FIX: Properly escape wildcards to prevent wildcard injection attacks
     */
    @Query("SELECT cr FROM ComplianceRule cr WHERE cr.conditions LIKE CONCAT('%', REPLACE(REPLACE(REPLACE(:pattern, '\\\\', '\\\\\\\\'), '%', '\\\\%'), '_', '\\\\_'), '%')")
    List<ComplianceRule> findByConditionPattern(@Param("pattern") String pattern);

    /**
     * Find rules applicable to specific transaction type
     * SECURITY FIX: Properly escape wildcards to prevent wildcard injection attacks
     */
    @Query("SELECT cr FROM ComplianceRule cr WHERE cr.isActive = true " +
           "AND (cr.conditions LIKE CONCAT('%', REPLACE(REPLACE(REPLACE(:transactionType, '\\\\', '\\\\\\\\'), '%', '\\\\%'), '_', '\\\\_'), '%') " +
           "OR cr.ruleType = 'UNIVERSAL' " +
           "OR cr.ruleType = 'TRANSACTION')")
    List<ComplianceRule> findApplicableToTransactionType(@Param("transactionType") String transactionType);

    /**
     * Find rules applicable to specific amount range
     */
    @Query("SELECT cr FROM ComplianceRule cr WHERE cr.isActive = true " +
           "AND (cr.conditions LIKE '%amount%' " +
           "OR cr.ruleType = 'AMOUNT_BASED' " +
           "OR cr.ruleType = 'UNIVERSAL')")
    List<ComplianceRule> findApplicableToAmountRules();

    /**
     * Find rules applicable to specific customer risk level
     * SECURITY FIX: Properly escape wildcards to prevent wildcard injection attacks
     */
    @Query("SELECT cr FROM ComplianceRule cr WHERE cr.isActive = true " +
           "AND (cr.conditions LIKE CONCAT('%', REPLACE(REPLACE(REPLACE(:riskLevel, '\\\\', '\\\\\\\\'), '%', '\\\\%'), '_', '\\\\_'), '%') " +
           "OR cr.ruleType = 'RISK_BASED' " +
           "OR cr.ruleType = 'UNIVERSAL')")
    List<ComplianceRule> findApplicableToRiskLevel(@Param("riskLevel") String riskLevel);

    /**
     * Find rules applicable to specific geography
     * SECURITY FIX: Properly escape wildcards to prevent wildcard injection attacks
     */
    @Query("SELECT cr FROM ComplianceRule cr WHERE cr.isActive = true " +
           "AND (cr.conditions LIKE CONCAT('%', REPLACE(REPLACE(REPLACE(:countryCode, '\\\\', '\\\\\\\\'), '%', '\\\\%'), '_', '\\\\_'), '%') " +
           "OR cr.ruleType = 'GEOGRAPHIC' " +
           "OR cr.ruleType = 'UNIVERSAL')")
    List<ComplianceRule> findApplicableToCountry(@Param("countryCode") String countryCode);

    /**
     * Find AML-specific rules
     */
    @Query("SELECT cr FROM ComplianceRule cr WHERE cr.isActive = true " +
           "AND (cr.ruleType = 'AML' " +
           "OR cr.ruleName LIKE '%AML%' " +
           "OR cr.ruleName LIKE '%Anti%Money%Laundering%')")
    List<ComplianceRule> findAMLRules();

    /**
     * Find KYC-specific rules
     */
    @Query("SELECT cr FROM ComplianceRule cr WHERE cr.isActive = true " +
           "AND (cr.ruleType = 'KYC' " +
           "OR cr.ruleName LIKE '%KYC%' " +
           "OR cr.ruleName LIKE '%Know%Your%Customer%')")
    List<ComplianceRule> findKYCRules();

    /**
     * Find sanctions screening rules
     */
    @Query("SELECT cr FROM ComplianceRule cr WHERE cr.isActive = true " +
           "AND (cr.ruleType = 'SANCTIONS' " +
           "OR cr.ruleName LIKE '%Sanctions%' " +
           "OR cr.ruleName LIKE '%OFAC%')")
    List<ComplianceRule> findSanctionsRules();

    /**
     * Find rules requiring periodic review
     */
    @Query("SELECT cr FROM ComplianceRule cr WHERE cr.isActive = true " +
           "AND (cr.lastReviewDate IS NULL " +
           "OR cr.lastReviewDate < :reviewThreshold)")
    List<ComplianceRule> findRequiringReview(@Param("reviewThreshold") LocalDateTime reviewThreshold);

    /**
     * Find rules by effectiveness rating
     */
    @Query("SELECT cr FROM ComplianceRule cr WHERE cr.effectivenessRating >= :minRating " +
           "AND cr.isActive = true ORDER BY cr.effectivenessRating DESC")
    List<ComplianceRule> findByEffectivenessRating(@Param("minRating") double minRating);

    /**
     * Find rules with high false positive rates
     */
    @Query("SELECT cr FROM ComplianceRule cr WHERE cr.falsePositiveRate > :threshold " +
           "AND cr.isActive = true ORDER BY cr.falsePositiveRate DESC")
    List<ComplianceRule> findWithHighFalsePositiveRate(@Param("threshold") double threshold);

    /**
     * Find recently triggered rules
     */
    @Query("SELECT cr FROM ComplianceRule cr WHERE cr.lastTriggeredAt >= :since " +
           "AND cr.isActive = true ORDER BY cr.lastTriggeredAt DESC")
    List<ComplianceRule> findRecentlyTriggered(@Param("since") LocalDateTime since);

    /**
     * Find rules never triggered
     */
    @Query("SELECT cr FROM ComplianceRule cr WHERE cr.lastTriggeredAt IS NULL " +
           "AND cr.isActive = true " +
           "AND cr.createdAt < :createdBefore")
    List<ComplianceRule> findNeverTriggered(@Param("createdBefore") LocalDateTime createdBefore);

    /**
     * Count rules by type
     */
    @Query("SELECT cr.ruleType, COUNT(cr) FROM ComplianceRule cr " +
           "WHERE cr.isActive = true GROUP BY cr.ruleType")
    List<Object[]> countByRuleType();

    /**
     * Count rules by priority
     */
    @Query("SELECT cr.priority, COUNT(cr) FROM ComplianceRule cr " +
           "WHERE cr.isActive = true GROUP BY cr.priority")
    List<Object[]> countByPriority();

    /**
     * Find conflicting rules (rules with contradictory conditions)
     */
    @Query("SELECT cr1, cr2 FROM ComplianceRule cr1, ComplianceRule cr2 " +
           "WHERE cr1.id != cr2.id " +
           "AND cr1.isActive = true AND cr2.isActive = true " +
           "AND cr1.ruleType = cr2.ruleType " +
           "AND cr1.action != cr2.action " +
           "AND cr1.conditions = cr2.conditions")
    List<Object[]> findConflictingRules();

    /**
     * Search rules by multiple criteria
     */
    @Query("SELECT cr FROM ComplianceRule cr WHERE " +
           "(:ruleType IS NULL OR cr.ruleType = :ruleType) AND " +
           "(:priority IS NULL OR cr.priority = :priority) AND " +
           "(:action IS NULL OR cr.action = :action) AND " +
           "(:isActive IS NULL OR cr.isActive = :isActive) AND " +
           "(:createdBy IS NULL OR cr.createdBy = :createdBy) AND " +
           "(:startDate IS NULL OR cr.createdAt >= :startDate) AND " +
           "(:endDate IS NULL OR cr.createdAt <= :endDate)")
    Page<ComplianceRule> searchByCriteria(
            @Param("ruleType") String ruleType,
            @Param("priority") String priority,
            @Param("action") String action,
            @Param("isActive") Boolean isActive,
            @Param("createdBy") String createdBy,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable);

    /**
     * Find rules for specific regulatory requirement
     * SECURITY FIX: Properly escape wildcards to prevent wildcard injection attacks
     */
    @Query("SELECT cr FROM ComplianceRule cr WHERE cr.isActive = true " +
           "AND (cr.regulatoryReference LIKE CONCAT('%', REPLACE(REPLACE(REPLACE(:regulation, '\\\\', '\\\\\\\\'), '%', '\\\\%'), '_', '\\\\_'), '%') " +
           "OR cr.description LIKE CONCAT('%', REPLACE(REPLACE(REPLACE(:regulation, '\\\\', '\\\\\\\\'), '%', '\\\\%'), '_', '\\\\_'), '%'))")
    List<ComplianceRule> findForRegulation(@Param("regulation") String regulation);

    /**
     * Find overdue rules (rules that should have been reviewed)
     */
    @Query("SELECT cr FROM ComplianceRule cr WHERE cr.isActive = true " +
           "AND cr.nextReviewDate <= :currentDate")
    List<ComplianceRule> findOverdueForReview(@Param("currentDate") LocalDateTime currentDate);

    /**
     * Update rule effectiveness metrics
     */
    @Query("UPDATE ComplianceRule cr SET " +
           "cr.triggerCount = cr.triggerCount + 1, " +
           "cr.lastTriggeredAt = :triggerTime " +
           "WHERE cr.id = :ruleId")
    void updateTriggerMetrics(@Param("ruleId") UUID ruleId, @Param("triggerTime") LocalDateTime triggerTime);

    /**
     * Update rule effectiveness rating
     */
    @Query("UPDATE ComplianceRule cr SET " +
           "cr.effectivenessRating = :rating, " +
           "cr.falsePositiveRate = :fpRate, " +
           "cr.lastEffectivenessUpdate = :updateTime " +
           "WHERE cr.id = :ruleId")
    void updateEffectivenessMetrics(@Param("ruleId") UUID ruleId,
                                   @Param("rating") double rating,
                                   @Param("fpRate") double fpRate,
                                   @Param("updateTime") LocalDateTime updateTime);

    /**
     * Deactivate old unused rules
     */
    @Query("UPDATE ComplianceRule cr SET cr.isActive = false, cr.deactivatedAt = :deactivateTime " +
           "WHERE cr.isActive = true " +
           "AND cr.lastTriggeredAt IS NULL " +
           "AND cr.createdAt < :cutoffDate")
    void deactivateOldUnusedRules(@Param("cutoffDate") LocalDateTime cutoffDate,
                                 @Param("deactivateTime") LocalDateTime deactivateTime);

    /**
     * Find rules due for automatic review
     */
    @Query("SELECT cr FROM ComplianceRule cr WHERE cr.isActive = true " +
           "AND cr.autoReviewEnabled = true " +
           "AND cr.nextAutoReviewDate <= :currentDate")
    List<ComplianceRule> findDueForAutoReview(@Param("currentDate") LocalDateTime currentDate);
}