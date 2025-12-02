package com.waqiti.analytics.repository;

import com.waqiti.analytics.dto.AlertRule;
import com.waqiti.analytics.dto.Alert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for alert rules
 */
@Repository
public interface AlertRuleRepository extends JpaRepository<AlertRule, String> {
    
    /**
     * Find all active alert rules
     */
    @Query("SELECT r FROM AlertRule r WHERE r.enabled = true")
    List<AlertRule> findAllActive();
    
    /**
     * Find active rules by category
     */
    @Query("SELECT r FROM AlertRule r WHERE r.category = :category AND r.enabled = true")
    List<AlertRule> findActiveByCateogry(@Param("category") String category);
    
    /**
     * Find rules by severity
     */
    List<AlertRule> findBySeverity(Alert.Severity severity);
    
    /**
     * Find rules that have time windows
     */
    @Query("SELECT r FROM AlertRule r WHERE r.timeWindow IS NOT NULL AND r.enabled = true")
    List<AlertRule> findTimeWindowRules();
    
    /**
     * Check if rule with name exists
     */
    boolean existsByName(String name);
    
    /**
     * Find rule by name
     */
    Optional<AlertRule> findByName(String name);
}