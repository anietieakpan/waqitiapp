/**
 * Crypto Fraud Event Repository
 * JPA repository for cryptocurrency fraud detection event operations
 */
package com.waqiti.crypto.repository;

import com.waqiti.crypto.entity.CryptoFraudEvent;
import com.waqiti.crypto.entity.CryptoCurrency;
import com.waqiti.crypto.entity.RiskLevel;
import com.waqiti.crypto.entity.RecommendedAction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.net.InetAddress;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface CryptoFraudEventRepository extends JpaRepository<CryptoFraudEvent, UUID> {

    /**
     * Find fraud events by transaction ID
     */
    List<CryptoFraudEvent> findByTransactionId(UUID transactionId);

    /**
     * Find fraud events by user ID
     */
    Page<CryptoFraudEvent> findByUserId(UUID userId, Pageable pageable);

    /**
     * Find fraud events by risk level
     */
    List<CryptoFraudEvent> findByRiskLevel(RiskLevel riskLevel);

    /**
     * Find fraud events by recommended action
     */
    List<CryptoFraudEvent> findByRecommendedAction(RecommendedAction recommendedAction);

    /**
     * Find fraud events by investigation status
     */
    List<CryptoFraudEvent> findByInvestigationStatus(CryptoFraudEvent.InvestigationStatus investigationStatus);

    /**
     * Find high-risk fraud events
     */
    @Query("SELECT f FROM CryptoFraudEvent f WHERE f.riskLevel IN ('HIGH', 'CRITICAL') ORDER BY f.createdAt DESC")
    List<CryptoFraudEvent> findHighRiskEvents();

    /**
     * Find fraud events requiring blocking
     */
    @Query("SELECT f FROM CryptoFraudEvent f WHERE f.recommendedAction = 'BLOCK' AND f.investigationStatus = 'OPEN'")
    List<CryptoFraudEvent> findEventsRequiringBlocking();

    /**
     * Find fraud events requiring manual review
     */
    @Query("SELECT f FROM CryptoFraudEvent f WHERE f.recommendedAction = 'MANUAL_REVIEW' AND f.investigationStatus = 'OPEN'")
    List<CryptoFraudEvent> findEventsRequiringManualReview();

    /**
     * Find fraud events by risk score range
     */
    @Query("SELECT f FROM CryptoFraudEvent f WHERE f.riskScore >= :minScore AND f.riskScore <= :maxScore ORDER BY f.riskScore DESC")
    List<CryptoFraudEvent> findByRiskScoreRange(@Param("minScore") BigDecimal minScore, @Param("maxScore") BigDecimal maxScore);

    /**
     * Find fraud events by IP address
     */
    List<CryptoFraudEvent> findByIpAddress(InetAddress ipAddress);

    /**
     * Find fraud events by device fingerprint
     */
    List<CryptoFraudEvent> findByDeviceFingerprint(String deviceFingerprint);

    /**
     * Find fraud events by user and time period
     */
    @Query("SELECT f FROM CryptoFraudEvent f WHERE f.userId = :userId " +
           "AND f.createdAt >= :startTime AND f.createdAt <= :endTime ORDER BY f.createdAt DESC")
    List<CryptoFraudEvent> findByUserIdAndTimePeriod(
        @Param("userId") UUID userId,
        @Param("startTime") LocalDateTime startTime,
        @Param("endTime") LocalDateTime endTime
    );

    /**
     * Find fraud events by currency and time period
     */
    @Query("SELECT f FROM CryptoFraudEvent f WHERE f.currency = :currency " +
           "AND f.createdAt >= :startTime AND f.createdAt <= :endTime ORDER BY f.createdAt DESC")
    List<CryptoFraudEvent> findByCurrencyAndTimePeriod(
        @Param("currency") CryptoCurrency currency,
        @Param("startTime") LocalDateTime startTime,
        @Param("endTime") LocalDateTime endTime
    );

    /**
     * Find recent fraud events for user
     */
    @Query("SELECT f FROM CryptoFraudEvent f WHERE f.userId = :userId " +
           "AND f.createdAt >= :sinceTime ORDER BY f.createdAt DESC")
    List<CryptoFraudEvent> findRecentEventsByUserId(@Param("userId") UUID userId, @Param("sinceTime") LocalDateTime sinceTime);

    /**
     * Find fraud events for specific destination address
     */
    @Query("SELECT f FROM CryptoFraudEvent f WHERE f.toAddress = :toAddress ORDER BY f.createdAt DESC")
    List<CryptoFraudEvent> findByToAddress(@Param("toAddress") String toAddress);

    /**
     * Count fraud events by user in time period
     */
    @Query("SELECT COUNT(f) FROM CryptoFraudEvent f WHERE f.userId = :userId " +
           "AND f.createdAt >= :startTime AND f.createdAt <= :endTime")
    long countByUserIdInTimePeriod(
        @Param("userId") UUID userId,
        @Param("startTime") LocalDateTime startTime,
        @Param("endTime") LocalDateTime endTime
    );

    /**
     * Count high-risk events by user
     */
    @Query("SELECT COUNT(f) FROM CryptoFraudEvent f WHERE f.userId = :userId AND f.riskLevel IN ('HIGH', 'CRITICAL')")
    long countHighRiskEventsByUserId(@Param("userId") UUID userId);

    /**
     * Get average risk score for user
     */
    @Query("SELECT COALESCE(AVG(f.riskScore), 0) FROM CryptoFraudEvent f WHERE f.userId = :userId")
    BigDecimal getAverageRiskScoreByUserId(@Param("userId") UUID userId);

    /**
     * Find open investigations older than specific time
     */
    @Query("SELECT f FROM CryptoFraudEvent f WHERE f.investigationStatus = 'OPEN' AND f.createdAt < :cutoffTime")
    List<CryptoFraudEvent> findStaleOpenInvestigations(@Param("cutoffTime") LocalDateTime cutoffTime);

    /**
     * Find fraud patterns by device fingerprint
     */
    @Query("SELECT f.deviceFingerprint, COUNT(f), AVG(f.riskScore), MAX(f.riskLevel) " +
           "FROM CryptoFraudEvent f WHERE f.deviceFingerprint IS NOT NULL " +
           "GROUP BY f.deviceFingerprint HAVING COUNT(f) > :minEvents ORDER BY COUNT(f) DESC")
    List<Object[]> findFraudPatternsByDeviceFingerprint(@Param("minEvents") long minEvents);

    /**
     * Find fraud patterns by IP address
     */
    @Query("SELECT f.ipAddress, COUNT(f), AVG(f.riskScore), MAX(f.riskLevel) " +
           "FROM CryptoFraudEvent f WHERE f.ipAddress IS NOT NULL " +
           "GROUP BY f.ipAddress HAVING COUNT(f) > :minEvents ORDER BY COUNT(f) DESC")
    List<Object[]> findFraudPatternsByIpAddress(@Param("minEvents") long minEvents);

    /**
     * Get fraud statistics by currency
     */
    @Query("SELECT f.currency, COUNT(f), AVG(f.riskScore), " +
           "SUM(CASE WHEN f.riskLevel = 'HIGH' OR f.riskLevel = 'CRITICAL' THEN 1 ELSE 0 END) " +
           "FROM CryptoFraudEvent f GROUP BY f.currency")
    List<Object[]> getFraudStatisticsByCurrency();

    /**
     * Get fraud statistics by risk level
     */
    @Query("SELECT f.riskLevel, COUNT(f), AVG(f.riskScore), AVG(f.amount) " +
           "FROM CryptoFraudEvent f GROUP BY f.riskLevel ORDER BY f.riskLevel")
    List<Object[]> getFraudStatisticsByRiskLevel();

    /**
     * Update investigation status
     */
    @Modifying
    @Query("UPDATE CryptoFraudEvent f SET f.investigationStatus = :status WHERE f.id = :eventId")
    int updateInvestigationStatus(@Param("eventId") UUID eventId, @Param("status") CryptoFraudEvent.InvestigationStatus status);

    /**
     * Resolve fraud event
     */
    @Modifying
    @Query("UPDATE CryptoFraudEvent f SET f.investigationStatus = :status, f.resolvedAt = :resolvedAt, " +
           "f.resolutionNotes = :notes WHERE f.id = :eventId")
    int resolveFraudEvent(
        @Param("eventId") UUID eventId,
        @Param("status") CryptoFraudEvent.InvestigationStatus status,
        @Param("resolvedAt") LocalDateTime resolvedAt,
        @Param("notes") String notes
    );

    /**
     * Find unresolved high-priority events
     */
    @Query("SELECT f FROM CryptoFraudEvent f WHERE f.investigationStatus IN ('OPEN', 'INVESTIGATING') " +
           "AND (f.riskLevel IN ('HIGH', 'CRITICAL') OR f.recommendedAction = 'BLOCK') " +
           "ORDER BY f.riskScore DESC, f.createdAt ASC")
    List<CryptoFraudEvent> findUnresolvedHighPriorityEvents();

    /**
     * Get fraud trend data
     */
    @Query("SELECT DATE(f.createdAt), COUNT(f), AVG(f.riskScore), " +
           "SUM(CASE WHEN f.riskLevel IN ('HIGH', 'CRITICAL') THEN 1 ELSE 0 END) " +
           "FROM CryptoFraudEvent f WHERE f.createdAt >= :startDate " +
           "GROUP BY DATE(f.createdAt) ORDER BY DATE(f.createdAt)")
    List<Object[]> getFraudTrendData(@Param("startDate") LocalDateTime startDate);

    /**
     * Clean up old resolved fraud events
     */
    @Modifying
    @Query("DELETE FROM CryptoFraudEvent f WHERE f.investigationStatus IN ('RESOLVED', 'FALSE_POSITIVE') " +
           "AND f.resolvedAt < :cutoffDate")
    int deleteOldResolvedEvents(@Param("cutoffDate") LocalDateTime cutoffDate);
}