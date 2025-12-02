package com.waqiti.risk.repository;

import com.waqiti.risk.model.RiskRule;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Risk Rule Repository
 *
 * MongoDB repository for managing risk rules
 *
 * @author Waqiti Engineering Team
 * @version 1.0.0
 */
@Repository
public interface RiskRuleRepository extends MongoRepository<RiskRule, String> {

    /**
     * Find rule by name
     */
    Optional<RiskRule> findByRuleName(String ruleName);

    /**
     * Find all enabled rules
     */
    List<RiskRule> findByEnabled(Boolean enabled);

    /**
     * Find rules by type
     */
    List<RiskRule> findByRuleType(String ruleType);

    /**
     * Find enabled rules by type
     */
    List<RiskRule> findByRuleTypeAndEnabled(String ruleType, Boolean enabled);

    /**
     * Find critical rules
     */
    List<RiskRule> findByCritical(Boolean critical);

    /**
     * Find enabled critical rules
     */
    List<RiskRule> findByCriticalAndEnabled(Boolean critical, Boolean enabled);

    /**
     * Find rules by category
     */
    List<RiskRule> findByCategory(String category);

    /**
     * Find effective rules (currently active)
     */
    @Query("{'enabled': true, " +
           "'effectiveFrom': {'$lte': ?0}, " +
           "'$or': [{'effectiveUntil': {'$gte': ?0}}, {'effectiveUntil': null}]}")
    List<RiskRule> findEffectiveRules(LocalDateTime now);

    /**
     * Find rules by priority (descending)
     */
    List<RiskRule> findByEnabledOrderByPriorityDesc(Boolean enabled);

    /**
     * Find rules with high trigger rate
     */
    @Query("{'triggerRate': {'$gte': ?0}}")
    List<RiskRule> findByHighTriggerRate(Double threshold);

    /**
     * Find rules by risk score threshold
     */
    @Query("{'riskScore': {'$gte': ?0}}")
    List<RiskRule> findByRiskScoreGreaterThanEqual(Double threshold);

    /**
     * Find recently triggered rules
     */
    @Query("{'lastTriggeredAt': {'$gte': ?0}}")
    List<RiskRule> findRecentlyTriggeredRules(LocalDateTime since);

    /**
     * Find rarely triggered rules (candidates for review/removal)
     */
    @Query("{'enabled': true, 'executionCount': {'$gte': 100}, 'triggerRate': {'$lt': ?0}}")
    List<RiskRule> findRarelyTriggeredRules(Double maxTriggerRate);

    /**
     * Find rules by version
     */
    List<RiskRule> findByVersion(String version);

    /**
     * Find rules created by user
     */
    List<RiskRule> findByCreatedBy(String createdBy);

    /**
     * Count enabled rules
     */
    long countByEnabled(Boolean enabled);

    /**
     * Find rules updated since
     */
    List<RiskRule> findByUpdatedAtGreaterThan(LocalDateTime since);

    /**
     * Check if rule name exists
     */
    boolean existsByRuleName(String ruleName);
}
