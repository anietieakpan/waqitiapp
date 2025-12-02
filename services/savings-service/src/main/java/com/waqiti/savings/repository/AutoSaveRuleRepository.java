package com.waqiti.savings.repository;

import com.waqiti.savings.domain.AutoSaveRule;
import com.waqiti.savings.domain.AutoSaveRule.RuleStatus;
import com.waqiti.savings.domain.AutoSaveRule.RuleType;
import com.waqiti.savings.domain.AutoSaveRule.TriggerFrequency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for AutoSaveRule entity operations.
 * Manages automated savings rules and execution tracking.
 *
 * @author Waqiti Development Team
 * @version 1.0
 * @since 2025-11-19
 */
@Repository
public interface AutoSaveRuleRepository extends JpaRepository<AutoSaveRule, UUID> {

    /**
     * Find all rules for a specific savings goal.
     *
     * @param goalId the savings goal UUID
     * @return list of auto-save rules for the goal
     */
    @Query("SELECT ar FROM AutoSaveRule ar WHERE ar.goalId = :goalId ORDER BY ar.createdAt DESC")
    List<AutoSaveRule> findByGoalId(@Param("goalId") UUID goalId);

    /**
     * Find all rules created by a user across all goals.
     *
     * @param userId the user's UUID
     * @return list of auto-save rules
     */
    @Query("SELECT ar FROM AutoSaveRule ar WHERE ar.userId = :userId ORDER BY ar.createdAt DESC")
    List<AutoSaveRule> findByUserId(@Param("userId") UUID userId);

    /**
     * Find active rules for a goal.
     * Most commonly used query for rule execution.
     *
     * @param goalId the savings goal UUID
     * @return list of active auto-save rules
     */
    @Query("SELECT ar FROM AutoSaveRule ar WHERE ar.goalId = :goalId " +
           "AND ar.status = 'ACTIVE' " +
           "ORDER BY ar.priority DESC")
    List<AutoSaveRule> findActiveRulesByGoalId(@Param("goalId") UUID goalId);

    /**
     * Find active rules for a user across all goals.
     *
     * @param userId the user's UUID
     * @return list of active auto-save rules
     */
    @Query("SELECT ar FROM AutoSaveRule ar WHERE ar.userId = :userId " +
           "AND ar.status = 'ACTIVE' " +
           "ORDER BY ar.priority DESC, ar.createdAt DESC")
    List<AutoSaveRule> findActiveRulesByUserId(@Param("userId") UUID userId);

    /**
     * Find rules by type.
     *
     * @param userId the user's UUID
     * @param ruleType the rule type (ROUND_UP, PERCENTAGE, FIXED_AMOUNT, etc.)
     * @return list of rules of specified type
     */
    List<AutoSaveRule> findByUserIdAndRuleType(UUID userId, RuleType ruleType);

    /**
     * Find rules by status.
     *
     * @param userId the user's UUID
     * @param status the rule status
     * @return list of rules with specified status
     */
    List<AutoSaveRule> findByUserIdAndStatus(UUID userId, RuleStatus status);

    /**
     * Find rules due for execution.
     * Returns rules where next execution time is in the past.
     *
     * @param now current timestamp
     * @return list of rules ready to execute
     */
    @Query("SELECT ar FROM AutoSaveRule ar WHERE ar.status = 'ACTIVE' " +
           "AND ar.nextExecutionAt IS NOT NULL " +
           "AND ar.nextExecutionAt <= :now")
    List<AutoSaveRule> findRulesDueForExecution(@Param("now") LocalDateTime now);

    /**
     * Find round-up rules for a user.
     * Used when processing transactions for round-up calculation.
     *
     * @param userId the user's UUID
     * @return list of active round-up rules
     */
    @Query("SELECT ar FROM AutoSaveRule ar WHERE ar.userId = :userId " +
           "AND ar.status = 'ACTIVE' " +
           "AND ar.ruleType = 'ROUND_UP'")
    List<AutoSaveRule> findActiveRoundUpRules(@Param("userId") UUID userId);

    /**
     * Find percentage-based rules for a user.
     * Used when processing income deposits.
     *
     * @param userId the user's UUID
     * @return list of active percentage rules
     */
    @Query("SELECT ar FROM AutoSaveRule ar WHERE ar.userId = :userId " +
           "AND ar.status = 'ACTIVE' " +
           "AND ar.ruleType = 'PERCENTAGE_BASED'")
    List<AutoSaveRule> findActivePercentageRules(@Param("userId") UUID userId);

    /**
     * Find recurring rules by frequency.
     *
     * @param userId the user's UUID
     * @param frequency the trigger frequency (DAILY, WEEKLY, MONTHLY, etc.)
     * @return list of recurring rules with specified frequency
     */
    @Query("SELECT ar FROM AutoSaveRule ar WHERE ar.userId = :userId " +
           "AND ar.status = 'ACTIVE' " +
           "AND ar.triggerFrequency = :frequency")
    List<AutoSaveRule> findActiveRulesByFrequency(
            @Param("userId") UUID userId,
            @Param("frequency") TriggerFrequency frequency);

    /**
     * Find rules with low success rate.
     * Used for optimization and user notifications.
     *
     * @param successRateThreshold minimum success rate (e.g., 0.5 for 50%)
     * @return list of under-performing rules
     */
    @Query("SELECT ar FROM AutoSaveRule ar WHERE ar.status = 'ACTIVE' " +
           "AND ar.executionCount > 10 " +
           "AND (CAST(ar.successCount AS double) / ar.executionCount) < :threshold")
    List<AutoSaveRule> findRulesWithLowSuccessRate(@Param("threshold") double successRateThreshold);

    /**
     * Find rules that haven't executed recently.
     * Useful for identifying stale or problematic rules.
     *
     * @param daysSinceExecution number of days to check
     * @return list of dormant rules
     */
    @Query("SELECT ar FROM AutoSaveRule ar WHERE ar.status = 'ACTIVE' " +
           "AND ar.lastExecutedAt IS NOT NULL " +
           "AND ar.lastExecutedAt < :sinceDate")
    List<AutoSaveRule> findDormantRules(@Param("sinceDate") LocalDateTime sinceDate);

    /**
     * Count active rules for a goal.
     * Used for enforcing max rules per goal limit.
     *
     * @param goalId the savings goal UUID
     * @return number of active rules
     */
    @Query("SELECT COUNT(ar) FROM AutoSaveRule ar WHERE ar.goalId = :goalId AND ar.status = 'ACTIVE'")
    Long countActiveRulesByGoalId(@Param("goalId") UUID goalId);

    /**
     * Count total rules for a user.
     * Used for enforcing max rules per user limit.
     *
     * @param userId the user's UUID
     * @return number of rules
     */
    Long countByUserId(UUID userId);

    /**
     * Get total amount saved through a specific rule.
     *
     * @param ruleId the auto-save rule UUID
     * @return sum of all saves from this rule
     */
    @Query("SELECT COALESCE(ar.totalSaved, 0) FROM AutoSaveRule ar WHERE ar.id = :ruleId")
    BigDecimal getTotalSavedByRuleId(@Param("ruleId") UUID ruleId);

    /**
     * Get total amount saved through all user's rules.
     *
     * @param userId the user's UUID
     * @return sum of all auto-saves
     */
    @Query("SELECT COALESCE(SUM(ar.totalSaved), 0) FROM AutoSaveRule ar WHERE ar.userId = :userId")
    BigDecimal getTotalAutoSavedByUserId(@Param("userId") UUID userId);

    /**
     * Get rule performance statistics for a user.
     *
     * @param userId the user's UUID
     * @return aggregated statistics across all rules
     */
    @Query("SELECT NEW map(" +
           "COUNT(ar) as totalRules, " +
           "SUM(CASE WHEN ar.status = 'ACTIVE' THEN 1 ELSE 0 END) as activeRules, " +
           "SUM(ar.executionCount) as totalExecutions, " +
           "SUM(ar.successCount) as totalSuccesses, " +
           "SUM(ar.failureCount) as totalFailures, " +
           "COALESCE(SUM(ar.totalSaved), 0) as totalSaved) " +
           "FROM AutoSaveRule ar WHERE ar.userId = :userId")
    Optional<java.util.Map<String, Object>> getRuleStatistics(@Param("userId") UUID userId);

    /**
     * Get statistics by rule type for a user.
     *
     * @param userId the user's UUID
     * @return statistics grouped by rule type
     */
    @Query("SELECT NEW map(" +
           "ar.ruleType as ruleType, " +
           "COUNT(ar) as ruleCount, " +
           "SUM(ar.executionCount) as executions, " +
           "SUM(ar.successCount) as successes, " +
           "COALESCE(SUM(ar.totalSaved), 0) as totalSaved) " +
           "FROM AutoSaveRule ar " +
           "WHERE ar.userId = :userId " +
           "GROUP BY ar.ruleType")
    List<java.util.Map<String, Object>> getStatisticsByRuleType(@Param("userId") UUID userId);

    /**
     * Find rules needing optimization.
     * Rules with high failure rates or not executed in a while.
     *
     * @param daysSinceExecution days threshold
     * @param maxFailureRate maximum acceptable failure rate
     * @return list of rules needing attention
     */
    @Query("SELECT ar FROM AutoSaveRule ar WHERE ar.status = 'ACTIVE' " +
           "AND (ar.lastExecutedAt < :sinceDate " +
           "OR (ar.executionCount > 5 AND " +
           "     CAST(ar.failureCount AS double) / ar.executionCount > :maxFailureRate))")
    List<AutoSaveRule> findRulesNeedingOptimization(
            @Param("sinceDate") LocalDateTime sinceDate,
            @Param("maxFailureRate") double maxFailureRate);

    /**
     * Find all rules for a goal by priority.
     *
     * @param goalId the savings goal UUID
     * @return list of rules ordered by priority (highest first)
     */
    @Query("SELECT ar FROM AutoSaveRule ar WHERE ar.goalId = :goalId " +
           "AND ar.status = 'ACTIVE' " +
           "ORDER BY ar.priority DESC, ar.createdAt ASC")
    List<AutoSaveRule> findRulesByPriority(@Param("goalId") UUID goalId);

    /**
     * Delete all rules for a goal (GDPR compliance).
     *
     * @param goalId the savings goal UUID
     */
    void deleteByGoalId(UUID goalId);

    /**
     * Soft delete all rules for a user.
     *
     * @param userId the user's UUID
     */
    @Query("UPDATE AutoSaveRule ar SET ar.status = 'INACTIVE', ar.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE ar.userId = :userId")
    void softDeleteAllByUserId(@Param("userId") UUID userId);
}
