package com.waqiti.reconciliation.repository;

import com.waqiti.reconciliation.domain.ReconciliationRule;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface ReconciliationRuleRepository extends JpaRepository<ReconciliationRule, UUID> {

    /**
     * Find all active reconciliation rules
     */
    @Query("SELECT r FROM ReconciliationRule r WHERE r.isActive = true ORDER BY r.priority DESC, r.name ASC")
    List<ReconciliationRule> findActiveRules();

    /**
     * Find active rules with pagination
     */
    @Query("SELECT r FROM ReconciliationRule r WHERE r.isActive = true ORDER BY r.priority DESC, r.name ASC")
    Page<ReconciliationRule> findActiveRules(Pageable pageable);

    /**
     * Find rules by rule type
     */
    List<ReconciliationRule> findByRuleTypeAndIsActiveTrueOrderByPriorityDescNameAsc(ReconciliationRule.RuleType ruleType);

    /**
     * Find rules by category
     */
    List<ReconciliationRule> findByRuleCategoryAndIsActiveTrueOrderByPriorityDescNameAsc(String ruleCategory);

    /**
     * Find rules by priority range
     */
    @Query("SELECT r FROM ReconciliationRule r WHERE r.priority BETWEEN :minPriority AND :maxPriority AND r.isActive = true ORDER BY r.priority DESC")
    List<ReconciliationRule> findByPriorityRange(
        @Param("minPriority") Integer minPriority,
        @Param("maxPriority") Integer maxPriority
    );

    /**
     * Find rules created by specific user
     */
    List<ReconciliationRule> findByCreatedByOrderByCreatedAtDesc(String createdBy);

    /**
     * Find rules modified after specific time
     */
    List<ReconciliationRule> findByUpdatedAtAfterOrderByUpdatedAtDesc(LocalDateTime after);

    /**
     * Find rules by description containing text (case insensitive)
     */
    @Query("SELECT r FROM ReconciliationRule r WHERE LOWER(r.description) LIKE LOWER(CONCAT('%', :searchText, '%')) ORDER BY r.name")
    List<ReconciliationRule> findByDescriptionContainingIgnoreCase(@Param("searchText") String searchText);

    /**
     * Find rules by name containing text (case insensitive)
     */
    @Query("SELECT r FROM ReconciliationRule r WHERE LOWER(r.name) LIKE LOWER(CONCAT('%', :searchText, '%')) ORDER BY r.name")
    List<ReconciliationRule> findByNameContainingIgnoreCase(@Param("searchText") String searchText);

    /**
     * Find auto-resolution rules
     */
    @Query("SELECT r FROM ReconciliationRule r WHERE r.canAutoResolve = true AND r.isActive = true ORDER BY r.priority DESC")
    List<ReconciliationRule> findAutoResolutionRules();

    /**
     * Find rules applicable to specific break type
     */
    // SECURITY FIX: Properly escape wildcards to prevent wildcard injection attacks
    @Query("SELECT r FROM ReconciliationRule r WHERE r.applicableBreakTypes LIKE CONCAT('%', REPLACE(REPLACE(REPLACE(:breakType, '\\\\', '\\\\\\\\'), '%', '\\\\%'), '_', '\\\\_'), '%') AND r.isActive = true ORDER BY r.priority DESC")
    List<ReconciliationRule> findRulesForBreakType(@Param("breakType") String breakType);

    /**
     * Find rules with tolerance amount greater than specified value
     */
    @Query("SELECT r FROM ReconciliationRule r WHERE r.toleranceAmount > :amount AND r.isActive = true ORDER BY r.toleranceAmount ASC")
    List<ReconciliationRule> findRulesWithToleranceGreaterThan(@Param("amount") java.math.BigDecimal amount);

    /**
     * Count active rules by category
     */
    @Query("SELECT r.ruleCategory, COUNT(r) FROM ReconciliationRule r WHERE r.isActive = true GROUP BY r.ruleCategory")
    List<Object[]> countActiveRulesByCategory();

    /**
     * Count rules by type
     */
    @Query("SELECT r.ruleType, COUNT(r) FROM ReconciliationRule r GROUP BY r.ruleType")
    List<Object[]> countRulesByType();

    /**
     * Find recently created rules
     */
    @Query("SELECT r FROM ReconciliationRule r WHERE r.createdAt >= :since ORDER BY r.createdAt DESC")
    List<ReconciliationRule> findRecentlyCreatedRules(@Param("since") LocalDateTime since);

    /**
     * Find rules that have never been used
     */
    @Query("SELECT r FROM ReconciliationRule r WHERE r.lastUsedAt IS NULL ORDER BY r.createdAt DESC")
    List<ReconciliationRule> findUnusedRules();

    /**
     * Find most frequently used rules
     */
    @Query("SELECT r FROM ReconciliationRule r WHERE r.usageCount > 0 ORDER BY r.usageCount DESC")
    List<ReconciliationRule> findMostUsedRules();

    /**
     * Find rules that need review (old rules not used recently)
     */
    @Query("SELECT r FROM ReconciliationRule r WHERE r.isActive = true AND (r.lastUsedAt IS NULL OR r.lastUsedAt < :reviewCutoff) ORDER BY r.createdAt ASC")
    List<ReconciliationRule> findRulesNeedingReview(@Param("reviewCutoff") LocalDateTime reviewCutoff);

    /**
     * Find rules by execution time range (performance analysis)
     */
    @Query("SELECT r FROM ReconciliationRule r WHERE r.averageExecutionTimeMs BETWEEN :minTime AND :maxTime ORDER BY r.averageExecutionTimeMs DESC")
    List<ReconciliationRule> findRulesByExecutionTimeRange(
        @Param("minTime") Long minTime,
        @Param("maxTime") Long maxTime
    );

    /**
     * Find slowest performing rules
     */
    @Query("SELECT r FROM ReconciliationRule r WHERE r.averageExecutionTimeMs IS NOT NULL ORDER BY r.averageExecutionTimeMs DESC")
    List<ReconciliationRule> findSlowestRules(Pageable pageable);

    /**
     * Find rules with high failure rate
     */
    @Query("SELECT r FROM ReconciliationRule r WHERE r.usageCount > 0 AND (r.failureCount * 100.0 / r.usageCount) > :maxFailurePercentage ORDER BY (r.failureCount * 100.0 / r.usageCount) DESC")
    List<ReconciliationRule> findRulesWithHighFailureRate(@Param("maxFailurePercentage") Double maxFailurePercentage);

    /**
     * Update rule usage statistics
     */
    @Query("UPDATE ReconciliationRule r SET r.usageCount = r.usageCount + 1, r.lastUsedAt = :usedAt WHERE r.id = :ruleId")
    int updateUsageStatistics(@Param("ruleId") UUID ruleId, @Param("usedAt") LocalDateTime usedAt);

    /**
     * Update rule failure statistics
     */
    @Query("UPDATE ReconciliationRule r SET r.failureCount = r.failureCount + 1 WHERE r.id = :ruleId")
    int updateFailureStatistics(@Param("ruleId") UUID ruleId);

    /**
     * Update rule execution time
     */
    @Query("UPDATE ReconciliationRule r SET r.averageExecutionTimeMs = :executionTime WHERE r.id = :ruleId")
    int updateExecutionTime(@Param("ruleId") UUID ruleId, @Param("executionTime") Long executionTime);

    /**
     * Find duplicate rules (same name)
     */
    @Query("SELECT r FROM ReconciliationRule r WHERE r.name IN (SELECT r2.name FROM ReconciliationRule r2 GROUP BY r2.name HAVING COUNT(r2) > 1) ORDER BY r.name, r.createdAt")
    List<ReconciliationRule> findDuplicateRules();

    /**
     * Deactivate old unused rules
     */
    @Query("UPDATE ReconciliationRule r SET r.isActive = false WHERE r.lastUsedAt < :cutoffDate OR (r.lastUsedAt IS NULL AND r.createdAt < :cutoffDate)")
    int deactivateOldUnusedRules(@Param("cutoffDate") LocalDateTime cutoffDate);

    /**
     * Find rules expiring soon (if they have expiry dates)
     */
    @Query("SELECT r FROM ReconciliationRule r WHERE r.expiryDate IS NOT NULL AND r.expiryDate <= :checkDate AND r.isActive = true ORDER BY r.expiryDate ASC")
    List<ReconciliationRule> findExpiringRules(@Param("checkDate") LocalDateTime checkDate);

    /**
     * Check if rule name already exists
     */
    boolean existsByNameAndIsActiveTrue(String name);

    /**
     * Find rules by version (for rule evolution tracking)
     */
    List<ReconciliationRule> findByVersionOrderByCreatedAtDesc(Integer version);
}