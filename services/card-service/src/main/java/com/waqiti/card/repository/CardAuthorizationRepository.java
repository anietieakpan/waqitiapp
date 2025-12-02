package com.waqiti.card.repository;

import com.waqiti.card.entity.CardAuthorization;
import com.waqiti.card.enums.AuthorizationStatus;
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
 * CardAuthorizationRepository - Spring Data JPA repository for CardAuthorization entity
 *
 * Provides data access methods for authorization management including:
 * - Authorization lookup and queries
 * - Capture and reversal queries
 * - Expiry queries
 * - Risk and fraud queries
 *
 * @author Waqiti Engineering Team
 * @version 2.0 (Consolidated)
 * @since 2025-11-09
 */
@Repository
public interface CardAuthorizationRepository extends JpaRepository<CardAuthorization, UUID>, JpaSpecificationExecutor<CardAuthorization> {

    // ========================================================================
    // BASIC LOOKUPS
    // ========================================================================

    /**
     * Find authorization by authorization ID
     */
    Optional<CardAuthorization> findByAuthorizationId(String authorizationId);

    /**
     * Find authorization by authorization code
     */
    Optional<CardAuthorization> findByAuthorizationCode(String authorizationCode);

    /**
     * Find authorization by external authorization ID
     */
    Optional<CardAuthorization> findByExternalAuthorizationId(String externalAuthorizationId);

    /**
     * Check if authorization exists by authorization ID
     */
    boolean existsByAuthorizationId(String authorizationId);

    // ========================================================================
    // TRANSACTION & CARD QUERIES
    // ========================================================================

    /**
     * Find authorizations by transaction ID
     */
    List<CardAuthorization> findByTransactionId(UUID transactionId);

    /**
     * Find authorizations by card ID
     */
    List<CardAuthorization> findByCardId(UUID cardId);

    /**
     * Find authorizations by user ID
     */
    List<CardAuthorization> findByUserId(UUID userId);

    /**
     * Find recent authorizations by card ID
     */
    @Query("SELECT a FROM CardAuthorization a WHERE a.cardId = :cardId AND " +
           "a.authorizationDate > :sinceDate AND a.deletedAt IS NULL " +
           "ORDER BY a.authorizationDate DESC")
    List<CardAuthorization> findRecentAuthorizationsByCardId(
        @Param("cardId") UUID cardId,
        @Param("sinceDate") LocalDateTime sinceDate
    );

    // ========================================================================
    // STATUS QUERIES
    // ========================================================================

    /**
     * Find authorizations by status
     */
    List<CardAuthorization> findByAuthorizationStatus(AuthorizationStatus status);

    /**
     * Find authorizations by card ID and status
     */
    List<CardAuthorization> findByCardIdAndAuthorizationStatus(UUID cardId, AuthorizationStatus status);

    /**
     * Find approved authorizations
     */
    @Query("SELECT a FROM CardAuthorization a WHERE a.authorizationStatus IN ('APPROVED', 'PARTIAL_APPROVAL') AND a.deletedAt IS NULL")
    List<CardAuthorization> findApprovedAuthorizations();

    /**
     * Find declined authorizations
     */
    @Query("SELECT a FROM CardAuthorization a WHERE a.authorizationStatus IN ('DECLINED', 'FRAUD_BLOCKED') AND a.deletedAt IS NULL")
    List<CardAuthorization> findDeclinedAuthorizations();

    /**
     * Find pending authorizations
     */
    @Query("SELECT a FROM CardAuthorization a WHERE a.authorizationStatus = 'PENDING' AND a.deletedAt IS NULL")
    List<CardAuthorization> findPendingAuthorizations();

    // ========================================================================
    // ACTIVE AUTHORIZATION QUERIES
    // ========================================================================

    /**
     * Find active authorizations (approved and not captured/expired)
     */
    @Query("SELECT a FROM CardAuthorization a WHERE " +
           "a.authorizationStatus IN ('APPROVED', 'PARTIAL_APPROVAL') AND " +
           "a.isCaptured = false AND a.isReversed = false AND " +
           "(a.expiryDate IS NULL OR a.expiryDate > :currentDateTime) AND " +
           "a.deletedAt IS NULL")
    List<CardAuthorization> findActiveAuthorizations(@Param("currentDateTime") LocalDateTime currentDateTime);

    /**
     * Find active authorizations by card ID
     */
    @Query("SELECT a FROM CardAuthorization a WHERE a.cardId = :cardId AND " +
           "a.authorizationStatus IN ('APPROVED', 'PARTIAL_APPROVAL') AND " +
           "a.isCaptured = false AND a.isReversed = false AND " +
           "(a.expiryDate IS NULL OR a.expiryDate > :currentDateTime) AND " +
           "a.deletedAt IS NULL")
    List<CardAuthorization> findActiveAuthorizationsByCardId(
        @Param("cardId") UUID cardId,
        @Param("currentDateTime") LocalDateTime currentDateTime
    );

    /**
     * Calculate total authorized amount by card ID
     */
    @Query("SELECT COALESCE(SUM(a.approvedAmount), 0) FROM CardAuthorization a WHERE " +
           "a.cardId = :cardId AND " +
           "a.authorizationStatus IN ('APPROVED', 'PARTIAL_APPROVAL') AND " +
           "a.isCaptured = false AND a.isReversed = false AND " +
           "a.deletedAt IS NULL")
    BigDecimal calculateTotalAuthorizedAmountByCardId(@Param("cardId") UUID cardId);

    // ========================================================================
    // CAPTURE QUERIES
    // ========================================================================

    /**
     * Find captured authorizations
     */
    @Query("SELECT a FROM CardAuthorization a WHERE a.isCaptured = true AND a.deletedAt IS NULL")
    List<CardAuthorization> findCapturedAuthorizations();

    /**
     * Find partially captured authorizations
     */
    @Query("SELECT a FROM CardAuthorization a WHERE a.isPartialCapture = true AND a.deletedAt IS NULL")
    List<CardAuthorization> findPartiallyCapturedAuthorizations();

    /**
     * Find uncaptured authorizations
     */
    @Query("SELECT a FROM CardAuthorization a WHERE " +
           "a.isCaptured = false AND " +
           "a.authorizationStatus IN ('APPROVED', 'PARTIAL_APPROVAL') AND " +
           "a.isReversed = false AND " +
           "a.deletedAt IS NULL")
    List<CardAuthorization> findUncapturedAuthorizations();

    /**
     * Find uncaptured authorizations by card ID
     */
    @Query("SELECT a FROM CardAuthorization a WHERE a.cardId = :cardId AND " +
           "a.isCaptured = false AND " +
           "a.authorizationStatus IN ('APPROVED', 'PARTIAL_APPROVAL') AND " +
           "a.isReversed = false AND " +
           "a.deletedAt IS NULL")
    List<CardAuthorization> findUncapturedAuthorizationsByCardId(@Param("cardId") UUID cardId);

    // ========================================================================
    // REVERSAL QUERIES
    // ========================================================================

    /**
     * Find reversed authorizations
     */
    @Query("SELECT a FROM CardAuthorization a WHERE a.isReversed = true AND a.deletedAt IS NULL")
    List<CardAuthorization> findReversedAuthorizations();

    /**
     * Find reversed authorizations by card ID
     */
    @Query("SELECT a FROM CardAuthorization a WHERE a.cardId = :cardId AND a.isReversed = true AND a.deletedAt IS NULL")
    List<CardAuthorization> findReversedAuthorizationsByCardId(@Param("cardId") UUID cardId);

    // ========================================================================
    // EXPIRY QUERIES
    // ========================================================================

    /**
     * Find expired authorizations
     */
    @Query("SELECT a FROM CardAuthorization a WHERE " +
           "a.expiryDate < :currentDateTime AND " +
           "a.isExpired = false AND " +
           "a.isCaptured = false AND " +
           "a.deletedAt IS NULL")
    List<CardAuthorization> findExpiredAuthorizations(@Param("currentDateTime") LocalDateTime currentDateTime);

    /**
     * Find authorizations expiring soon
     */
    @Query("SELECT a FROM CardAuthorization a WHERE " +
           "a.expiryDate BETWEEN :currentDateTime AND :futureDateTime AND " +
           "a.isCaptured = false AND a.isReversed = false AND " +
           "a.authorizationStatus IN ('APPROVED', 'PARTIAL_APPROVAL') AND " +
           "a.deletedAt IS NULL")
    List<CardAuthorization> findAuthorizationsExpiringSoon(
        @Param("currentDateTime") LocalDateTime currentDateTime,
        @Param("futureDateTime") LocalDateTime futureDateTime
    );

    /**
     * Find all expired authorizations marked as expired
     */
    @Query("SELECT a FROM CardAuthorization a WHERE a.isExpired = true AND a.deletedAt IS NULL")
    List<CardAuthorization> findMarkedExpiredAuthorizations();

    // ========================================================================
    // AMOUNT QUERIES
    // ========================================================================

    /**
     * Find authorizations by amount range
     */
    @Query("SELECT a FROM CardAuthorization a WHERE " +
           "a.authorizationAmount BETWEEN :minAmount AND :maxAmount AND " +
           "a.deletedAt IS NULL")
    List<CardAuthorization> findByAmountRange(
        @Param("minAmount") BigDecimal minAmount,
        @Param("maxAmount") BigDecimal maxAmount
    );

    /**
     * Find high-value authorizations
     */
    @Query("SELECT a FROM CardAuthorization a WHERE " +
           "a.authorizationAmount > :threshold AND " +
           "a.deletedAt IS NULL " +
           "ORDER BY a.authorizationAmount DESC")
    List<CardAuthorization> findHighValueAuthorizations(@Param("threshold") BigDecimal threshold);

    /**
     * Find partial approvals
     */
    @Query("SELECT a FROM CardAuthorization a WHERE " +
           "a.authorizationStatus = 'PARTIAL_APPROVAL' AND " +
           "a.deletedAt IS NULL")
    List<CardAuthorization> findPartialApprovals();

    // ========================================================================
    // RISK & FRAUD QUERIES
    // ========================================================================

    /**
     * Find authorizations with high risk score
     */
    @Query("SELECT a FROM CardAuthorization a WHERE " +
           "a.riskScore > :threshold AND " +
           "a.deletedAt IS NULL " +
           "ORDER BY a.riskScore DESC")
    List<CardAuthorization> findHighRiskAuthorizations(@Param("threshold") BigDecimal threshold);

    /**
     * Find authorizations that failed fraud check
     */
    @Query("SELECT a FROM CardAuthorization a WHERE a.fraudCheckPassed = false AND a.deletedAt IS NULL")
    List<CardAuthorization> findAuthorizationsFailedFraudCheck();

    /**
     * Find authorizations that failed velocity check
     */
    @Query("SELECT a FROM CardAuthorization a WHERE a.velocityCheckPassed = false AND a.deletedAt IS NULL")
    List<CardAuthorization> findAuthorizationsFailedVelocityCheck();

    /**
     * Find authorizations that failed limit check
     */
    @Query("SELECT a FROM CardAuthorization a WHERE a.limitCheckPassed = false AND a.deletedAt IS NULL")
    List<CardAuthorization> findAuthorizationsFailedLimitCheck();

    /**
     * Find fraud blocked authorizations
     */
    @Query("SELECT a FROM CardAuthorization a WHERE a.authorizationStatus = 'FRAUD_BLOCKED' AND a.deletedAt IS NULL")
    List<CardAuthorization> findFraudBlockedAuthorizations();

    // ========================================================================
    // MERCHANT QUERIES
    // ========================================================================

    /**
     * Find authorizations by merchant ID
     */
    List<CardAuthorization> findByMerchantId(String merchantId);

    /**
     * Find authorizations by merchant category code
     */
    List<CardAuthorization> findByMerchantCategoryCode(String mcc);

    /**
     * Find authorizations by merchant country
     */
    List<CardAuthorization> findByMerchantCountry(String countryCode);

    // ========================================================================
    // CHANNEL QUERIES
    // ========================================================================

    /**
     * Find online authorizations
     */
    @Query("SELECT a FROM CardAuthorization a WHERE a.isOnline = true AND a.deletedAt IS NULL")
    List<CardAuthorization> findOnlineAuthorizations();

    /**
     * Find contactless authorizations
     */
    @Query("SELECT a FROM CardAuthorization a WHERE a.isContactless = true AND a.deletedAt IS NULL")
    List<CardAuthorization> findContactlessAuthorizations();

    /**
     * Find card present authorizations
     */
    @Query("SELECT a FROM CardAuthorization a WHERE a.isCardPresent = true AND a.deletedAt IS NULL")
    List<CardAuthorization> findCardPresentAuthorizations();

    /**
     * Find international authorizations
     */
    @Query("SELECT a FROM CardAuthorization a WHERE a.isInternational = true AND a.deletedAt IS NULL")
    List<CardAuthorization> findInternationalAuthorizations();

    // ========================================================================
    // DATE RANGE QUERIES
    // ========================================================================

    /**
     * Find authorizations by date range
     */
    @Query("SELECT a FROM CardAuthorization a WHERE " +
           "a.authorizationDate BETWEEN :startDate AND :endDate AND " +
           "a.deletedAt IS NULL " +
           "ORDER BY a.authorizationDate DESC")
    List<CardAuthorization> findByDateRange(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );

    /**
     * Find authorizations by card ID and date range
     */
    @Query("SELECT a FROM CardAuthorization a WHERE " +
           "a.cardId = :cardId AND " +
           "a.authorizationDate BETWEEN :startDate AND :endDate AND " +
           "a.deletedAt IS NULL " +
           "ORDER BY a.authorizationDate DESC")
    List<CardAuthorization> findByCardIdAndDateRange(
        @Param("cardId") UUID cardId,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );

    // ========================================================================
    // STATISTICAL QUERIES
    // ========================================================================

    /**
     * Count authorizations by card ID
     */
    long countByCardId(UUID cardId);

    /**
     * Count authorizations by status
     */
    long countByAuthorizationStatus(AuthorizationStatus status);

    /**
     * Calculate approval rate by card ID
     */
    @Query("SELECT " +
           "CAST(COUNT(CASE WHEN a.authorizationStatus IN ('APPROVED', 'PARTIAL_APPROVAL') THEN 1 END) AS double) / " +
           "CAST(COUNT(*) AS double) " +
           "FROM CardAuthorization a WHERE a.cardId = :cardId AND a.deletedAt IS NULL")
    Double calculateApprovalRateByCardId(@Param("cardId") UUID cardId);

    /**
     * Get authorization statistics by status
     */
    @Query("SELECT a.authorizationStatus, COUNT(a) FROM CardAuthorization a WHERE a.deletedAt IS NULL GROUP BY a.authorizationStatus")
    List<Object[]> getAuthorizationStatisticsByStatus();

    /**
     * Get daily authorization volume
     */
    @Query("SELECT DATE(a.authorizationDate), COUNT(a), SUM(a.authorizationAmount) FROM CardAuthorization a WHERE " +
           "a.authorizationDate BETWEEN :startDate AND :endDate AND " +
           "a.deletedAt IS NULL " +
           "GROUP BY DATE(a.authorizationDate) ORDER BY DATE(a.authorizationDate)")
    List<Object[]> getDailyAuthorizationVolume(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );

    // ========================================================================
    // SOFT DELETE QUERIES
    // ========================================================================

    /**
     * Find deleted authorizations
     */
    @Query("SELECT a FROM CardAuthorization a WHERE a.deletedAt IS NOT NULL")
    List<CardAuthorization> findDeletedAuthorizations();
}
