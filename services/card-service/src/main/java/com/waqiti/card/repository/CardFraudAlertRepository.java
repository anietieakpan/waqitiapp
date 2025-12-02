package com.waqiti.card.repository;

import com.waqiti.card.entity.CardFraudAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * CardFraudAlertRepository - Spring Data JPA repository for CardFraudAlert entity
 *
 * @author Waqiti Engineering Team
 * @version 2.0 (Consolidated)
 * @since 2025-11-09
 */
@Repository
public interface CardFraudAlertRepository extends JpaRepository<CardFraudAlert, UUID>, JpaSpecificationExecutor<CardFraudAlert> {

    Optional<CardFraudAlert> findByAlertId(String alertId);

    List<CardFraudAlert> findByCardId(UUID cardId);

    List<CardFraudAlert> findByUserId(UUID userId);

    List<CardFraudAlert> findByTransactionId(UUID transactionId);

    List<CardFraudAlert> findByAlertStatus(String alertStatus);

    List<CardFraudAlert> findBySeverity(String severity);

    @Query("SELECT a FROM CardFraudAlert a WHERE a.alertStatus IN ('OPEN', 'INVESTIGATING', 'UNDER_REVIEW') AND a.deletedAt IS NULL")
    List<CardFraudAlert> findActiveAlerts();

    @Query("SELECT a FROM CardFraudAlert a WHERE a.severity IN ('CRITICAL', 'HIGH') AND a.alertStatus IN ('OPEN', 'INVESTIGATING') AND a.deletedAt IS NULL ORDER BY a.alertDate DESC")
    List<CardFraudAlert> findHighSeverityAlerts();

    @Query("SELECT a FROM CardFraudAlert a WHERE a.manualReviewRequired = true AND a.alertStatus = 'OPEN' AND a.deletedAt IS NULL")
    List<CardFraudAlert> findAlertsRequiringManualReview();

    @Query("SELECT a FROM CardFraudAlert a WHERE a.assignedTo = :assignee AND a.alertStatus IN ('OPEN', 'INVESTIGATING') AND a.deletedAt IS NULL")
    List<CardFraudAlert> findAlertsAssignedTo(@Param("assignee") String assignee);

    @Query("SELECT a FROM CardFraudAlert a WHERE a.triggeredRuleId = :ruleId AND a.deletedAt IS NULL")
    List<CardFraudAlert> findAlertsByTriggeredRule(@Param("ruleId") UUID ruleId);

    @Query("SELECT a FROM CardFraudAlert a WHERE a.riskScore > :threshold AND a.deletedAt IS NULL ORDER BY a.riskScore DESC")
    List<CardFraudAlert> findHighRiskAlerts(@Param("threshold") BigDecimal threshold);

    @Query("SELECT a FROM CardFraudAlert a WHERE a.cardId = :cardId AND a.alertDate > :sinceDate AND a.deletedAt IS NULL")
    List<CardFraudAlert> findRecentAlertsByCardId(@Param("cardId") UUID cardId, @Param("sinceDate") LocalDateTime sinceDate);

    @Query("SELECT a FROM CardFraudAlert a WHERE a.transactionBlocked = true AND a.deletedAt IS NULL")
    List<CardFraudAlert> findAlertsWithBlockedTransactions();

    @Query("SELECT a FROM CardFraudAlert a WHERE a.cardBlocked = true AND a.deletedAt IS NULL")
    List<CardFraudAlert> findAlertsWithBlockedCards();

    long countByCardId(UUID cardId);

    long countByAlertStatus(String alertStatus);
}
