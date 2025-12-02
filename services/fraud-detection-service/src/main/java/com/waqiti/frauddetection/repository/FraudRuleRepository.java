package com.waqiti.frauddetection.repository;

import com.waqiti.frauddetection.entity.FraudRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Repository for fraud detection rules
 * Supports dynamic rule-based fraud detection engine
 */
@Repository
public interface FraudRuleRepository extends JpaRepository<FraudRule, UUID> {

    /**
     * Find all active rules for evaluation
     * Uses index: idx_fraud_rules_active_priority
     */
    @Query("SELECT r FROM FraudRule r WHERE r.active = true " +
           "ORDER BY r.priority DESC, r.createdAt ASC")
    List<FraudRule> findActiveRulesOrderByPriority();

    /**
     * Find rules by category
     */
    @Query("SELECT r FROM FraudRule r WHERE r.category = :category AND r.active = true")
    List<FraudRule> findActiveByCategoryOrderByPriorityDesc(@Param("category") String category);

    /**
     * Find rules applicable to specific transaction type
     */
    @Query("SELECT r FROM FraudRule r WHERE r.active = true " +
           "AND (:transactionType MEMBER OF r.applicableTransactionTypes OR r.applicableTransactionTypes IS EMPTY) " +
           "ORDER BY r.priority DESC")
    List<FraudRule> findRulesForTransactionType(@Param("transactionType") String transactionType);

    /**
     * Find rules by risk level threshold
     */
    @Query("SELECT r FROM FraudRule r WHERE r.active = true " +
           "AND r.riskScoreThreshold >= :threshold ORDER BY r.priority DESC")
    List<FraudRule> findByRiskScoreThresholdGreaterThanEqual(@Param("threshold") Double threshold);

    /**
     * Count active rules by category
     */
    @Query("SELECT COUNT(r) FROM FraudRule r WHERE r.active = true AND r.category = :category")
    long countActiveByCategory(@Param("category") String category);

    /**
     * Find recently updated rules
     */
    @Query("SELECT r FROM FraudRule r WHERE r.updatedAt >= :since ORDER BY r.updatedAt DESC")
    List<FraudRule> findRecentlyUpdated(@Param("since") LocalDateTime since);
}
