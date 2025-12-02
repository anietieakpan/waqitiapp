package com.waqiti.frauddetection.repository;

import com.waqiti.frauddetection.entity.FraudRuleDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for Fraud Rule Definitions
 * 
 * @author Waqiti Security Team
 */
@Repository
public interface FraudRuleDefinitionRepository extends JpaRepository<FraudRuleDefinition, Long> {

    List<FraudRuleDefinition> findByEnabledTrue();

    List<FraudRuleDefinition> findByEnabledTrueOrderByPriorityDesc();

    Optional<FraudRuleDefinition> findByRuleCode(String ruleCode);

    List<FraudRuleDefinition> findByCategory(String category);

    List<FraudRuleDefinition> findBySeverity(FraudRuleDefinition.RuleSeverity severity);

    @Query("SELECT r FROM FraudRuleDefinition r WHERE r.enabled = true AND " +
           "(r.minAmount IS NULL OR r.minAmount <= :amount) AND " +
           "(r.maxAmount IS NULL OR r.maxAmount >= :amount)")
    List<FraudRuleDefinition> findApplicableRulesByAmount(@Param("amount") java.math.BigDecimal amount);

    @Query("SELECT COUNT(r) FROM FraudRuleDefinition r WHERE r.enabled = true")
    long countActiveRules();
}