package com.waqiti.payment.repository;

import com.waqiti.payment.entity.VelocityRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * VelocityRuleRepository - Production-Grade JPA Repository
 *
 * Provides database access for VelocityRule entities with optimized queries for:
 * - Active rules retrieval (sorted by priority)
 * - Rule type filtering
 * - Amount-based rule matching
 * - User scope filtering
 *
 * PERFORMANCE OPTIMIZATIONS:
 * - Indexed queries on enabled + priority
 * - Cached commonly-used rules
 * - Efficient filtering by rule type
 *
 * @author Waqiti Engineering Team
 * @version 2.0 (Production-Ready)
 */
@Repository
public interface VelocityRuleRepository extends JpaRepository<VelocityRule, UUID> {

    // ==================== ACTIVE RULES ====================

    /**
     * Find all enabled rules ordered by priority
     *
     * @return List of enabled rules (higher priority first)
     */
    List<VelocityRule> findByEnabledTrueOrderByPriorityAsc();

    /**
     * Find all enabled rules of a specific type
     *
     * @param ruleType Rule type to filter
     * @return List of enabled rules
     */
    List<VelocityRule> findByRuleTypeAndEnabledTrueOrderByPriorityAsc(String ruleType);

    /**
     * Find enabled rules by user scope
     *
     * @param userScope User scope (role, tier, etc.)
     * @return List of enabled rules
     */
    List<VelocityRule> findByUserScopeAndEnabledTrueOrderByPriorityAsc(String userScope);

    // ==================== RULE MATCHING ====================

    /**
     * Find applicable rules for a given transaction amount
     *
     * @param amount Transaction amount
     * @return List of applicable rules
     */
    @Query("SELECT vr FROM VelocityRule vr WHERE vr.enabled = true " +
           "AND (:amount >= vr.minAmount OR vr.minAmount IS NULL) " +
           "AND (:amount <= vr.maxAmount OR vr.maxAmount IS NULL) " +
           "ORDER BY vr.priority ASC")
    List<VelocityRule> findApplicableRules(@Param("amount") BigDecimal amount);

    /**
     * Find applicable rules for transaction amount and rule type
     *
     * @param amount Transaction amount
     * @param ruleType Rule type
     * @return List of applicable rules
     */
    @Query("SELECT vr FROM VelocityRule vr WHERE vr.enabled = true " +
           "AND vr.ruleType = :ruleType " +
           "AND (:amount >= vr.minAmount OR vr.minAmount IS NULL) " +
           "AND (:amount <= vr.maxAmount OR vr.maxAmount IS NULL) " +
           "ORDER BY vr.priority ASC")
    List<VelocityRule> findApplicableRulesByType(
        @Param("amount") BigDecimal amount,
        @Param("ruleType") String ruleType
    );

    /**
     * Find applicable rules by amount and user scope
     *
     * @param amount Transaction amount
     * @param userScope User scope
     * @return List of applicable rules
     */
    @Query("SELECT vr FROM VelocityRule vr WHERE vr.enabled = true " +
           "AND (vr.userScope = :userScope OR vr.userScope IS NULL) " +
           "AND (:amount >= vr.minAmount OR vr.minAmount IS NULL) " +
           "AND (:amount <= vr.maxAmount OR vr.maxAmount IS NULL) " +
           "ORDER BY vr.priority ASC")
    List<VelocityRule> findApplicableRulesByUserScope(
        @Param("amount") BigDecimal amount,
        @Param("userScope") String userScope
    );

    // ==================== RULE LOOKUP ====================

    /**
     * Find rule by name
     *
     * @param name Rule name
     * @return Optional rule
     */
    Optional<VelocityRule> findByName(String name);

    /**
     * Check if rule name exists
     *
     * @param name Rule name
     * @return True if exists
     */
    boolean existsByName(String name);

    // ==================== ALERT-ONLY RULES ====================

    /**
     * Find alert-only rules (monitoring without blocking)
     *
     * @return List of alert-only rules
     */
    List<VelocityRule> findByEnabledTrueAndAlertOnlyTrueOrderByPriorityAsc();

    /**
     * Find blocking rules (non-alert-only)
     *
     * @return List of blocking rules
     */
    List<VelocityRule> findByEnabledTrueAndAlertOnlyFalseOrderByPriorityAsc();

    // ==================== STATISTICS ====================

    /**
     * Count enabled rules
     *
     * @return Number of enabled rules
     */
    long countByEnabledTrue();

    /**
     * Count enabled rules by type
     *
     * @param ruleType Rule type
     * @return Number of enabled rules
     */
    long countByRuleTypeAndEnabledTrue(String ruleType);

    /**
     * Find most triggered rules
     *
     * @return List of rules ordered by trigger count
     */
    @Query("SELECT vr FROM VelocityRule vr WHERE vr.enabled = true " +
           "ORDER BY vr.triggerCount DESC, vr.lastTriggeredAt DESC")
    List<VelocityRule> findMostTriggeredRules();

    // ==================== LARGE AMOUNT RULES ====================

    /**
     * Find rules for large transactions (above threshold)
     *
     * @param threshold Minimum amount threshold
     * @return List of large amount rules
     */
    @Query("SELECT vr FROM VelocityRule vr WHERE vr.enabled = true " +
           "AND vr.minAmount >= :threshold ORDER BY vr.minAmount ASC")
    List<VelocityRule> findLargeAmountRules(@Param("threshold") BigDecimal threshold);

    // ==================== TIME WINDOW QUERIES ====================

    /**
     * Find rules with specific time windows
     *
     * @param timeWindowSeconds Time window in seconds
     * @return List of rules
     */
    List<VelocityRule> findByTimeWindowSecondsAndEnabledTrueOrderByPriorityAsc(Integer timeWindowSeconds);

    /**
     * Find rules with time window less than or equal to specified value
     *
     * @param maxTimeWindowSeconds Maximum time window in seconds
     * @return List of rules
     */
    @Query("SELECT vr FROM VelocityRule vr WHERE vr.enabled = true " +
           "AND vr.timeWindowSeconds <= :maxTimeWindowSeconds " +
           "ORDER BY vr.timeWindowSeconds ASC, vr.priority ASC")
    List<VelocityRule> findShortTimeWindowRules(@Param("maxTimeWindowSeconds") Integer maxTimeWindowSeconds);
}
