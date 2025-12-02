package com.waqiti.card.repository;

import com.waqiti.card.entity.CardFraudRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * CardFraudRuleRepository - Spring Data JPA repository for CardFraudRule entity
 *
 * @author Waqiti Engineering Team
 * @version 2.0 (Consolidated)
 * @since 2025-11-09
 */
@Repository
public interface CardFraudRuleRepository extends JpaRepository<CardFraudRule, UUID>, JpaSpecificationExecutor<CardFraudRule> {

    Optional<CardFraudRule> findByRuleId(String ruleId);

    List<CardFraudRule> findByRuleType(String ruleType);

    @Query("SELECT r FROM CardFraudRule r WHERE r.isActive = true AND " +
           "(r.effectiveFrom IS NULL OR r.effectiveFrom <= :currentDateTime) AND " +
           "(r.effectiveUntil IS NULL OR r.effectiveUntil >= :currentDateTime) AND " +
           "r.deletedAt IS NULL ORDER BY r.priority DESC")
    List<CardFraudRule> findEffectiveRules(@Param("currentDateTime") LocalDateTime currentDateTime);

    @Query("SELECT r FROM CardFraudRule r WHERE r.isActive = true AND r.isBlocking = true AND " +
           "(r.effectiveFrom IS NULL OR r.effectiveFrom <= :currentDateTime) AND " +
           "(r.effectiveUntil IS NULL OR r.effectiveUntil >= :currentDateTime) AND " +
           "r.deletedAt IS NULL ORDER BY r.priority DESC")
    List<CardFraudRule> findEffectiveBlockingRules(@Param("currentDateTime") LocalDateTime currentDateTime);

    @Query("SELECT r FROM CardFraudRule r WHERE " +
           "(r.appliesToAllCards = true OR :cardId MEMBER OF r.specificCardIds) AND " +
           "r.isActive = true AND r.deletedAt IS NULL ORDER BY r.priority DESC")
    List<CardFraudRule> findRulesApplicableToCard(@Param("cardId") UUID cardId);

    @Query("SELECT r FROM CardFraudRule r WHERE r.appliesToProductId = :productId AND r.isActive = true AND r.deletedAt IS NULL")
    List<CardFraudRule> findRulesByProductId(@Param("productId") String productId);

    @Query("SELECT r FROM CardFraudRule r WHERE r.effectivenessScore < :threshold AND r.isActive = true AND r.deletedAt IS NULL")
    List<CardFraudRule> findLowEffectivenessRules(@Param("threshold") java.math.BigDecimal threshold);

    long countByIsActive(Boolean isActive);
}
