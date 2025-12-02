package com.waqiti.card.repository;

import com.waqiti.card.entity.Card;
import com.waqiti.card.enums.CardStatus;
import com.waqiti.card.enums.CardType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * CardRepository - Spring Data JPA repository for Card entity
 *
 * Provides data access methods for card management including:
 * - CRUD operations
 * - Card lookup by various identifiers
 * - Card status and expiry queries
 * - User and account card queries
 * - Product-based queries
 * - Financial queries
 *
 * @author Waqiti Engineering Team
 * @version 2.0 (Consolidated)
 * @since 2025-11-09
 */
@Repository
public interface CardRepository extends JpaRepository<Card, UUID>, JpaSpecificationExecutor<Card> {

    // ========================================================================
    // BASIC LOOKUPS
    // ========================================================================

    /**
     * Find card by card ID
     */
    Optional<Card> findByCardId(String cardId);

    /**
     * Find card by PAN token
     */
    Optional<Card> findByPanToken(String panToken);

    /**
     * Find card by card ID and not deleted
     */
    @Query("SELECT c FROM Card c WHERE c.cardId = :cardId AND c.deletedAt IS NULL")
    Optional<Card> findByCardIdAndNotDeleted(@Param("cardId") String cardId);

    /**
     * Find card by last four digits (returns list as multiple cards can have same last 4)
     */
    List<Card> findByCardNumberLastFour(String lastFour);

    /**
     * Check if card exists by card ID
     */
    boolean existsByCardId(String cardId);

    /**
     * Check if card exists by PAN token
     */
    boolean existsByPanToken(String panToken);

    // ========================================================================
    // USER & ACCOUNT QUERIES
    // ========================================================================

    /**
     * Find all cards for a user
     */
    List<Card> findByUserId(UUID userId);

    /**
     * Find all active cards for a user
     */
    @Query("SELECT c FROM Card c WHERE c.userId = :userId AND c.cardStatus = 'ACTIVE' AND c.deletedAt IS NULL")
    List<Card> findActiveCardsByUserId(@Param("userId") UUID userId);

    /**
     * Find all cards for an account
     */
    List<Card> findByAccountId(UUID accountId);

    /**
     * Find all active cards for an account
     */
    @Query("SELECT c FROM Card c WHERE c.accountId = :accountId AND c.cardStatus = 'ACTIVE' AND c.deletedAt IS NULL")
    List<Card> findActiveCardsByAccountId(@Param("accountId") UUID accountId);

    /**
     * Find cards by user ID and card status
     */
    List<Card> findByUserIdAndCardStatus(UUID userId, CardStatus cardStatus);

    /**
     * Find cards by user ID and card type
     */
    List<Card> findByUserIdAndCardType(UUID userId, CardType cardType);

    /**
     * Count cards by user ID
     */
    long countByUserId(UUID userId);

    /**
     * Count active cards by user ID
     */
    @Query("SELECT COUNT(c) FROM Card c WHERE c.userId = :userId AND c.cardStatus = 'ACTIVE' AND c.deletedAt IS NULL")
    long countActiveCardsByUserId(@Param("userId") UUID userId);

    // ========================================================================
    // PRODUCT QUERIES
    // ========================================================================

    /**
     * Find cards by product ID
     */
    List<Card> findByProductId(String productId);

    /**
     * Find active cards by product ID
     */
    @Query("SELECT c FROM Card c WHERE c.productId = :productId AND c.cardStatus = 'ACTIVE' AND c.deletedAt IS NULL")
    List<Card> findActiveCardsByProductId(@Param("productId") String productId);

    /**
     * Count cards by product ID
     */
    long countByProductId(String productId);

    // ========================================================================
    // STATUS QUERIES
    // ========================================================================

    /**
     * Find cards by status
     */
    List<Card> findByCardStatus(CardStatus cardStatus);

    /**
     * Find cards by status and not deleted
     */
    @Query("SELECT c FROM Card c WHERE c.cardStatus = :status AND c.deletedAt IS NULL")
    List<Card> findByCardStatusAndNotDeleted(@Param("status") CardStatus status);

    /**
     * Find all active and usable cards
     */
    @Query("SELECT c FROM Card c WHERE c.cardStatus = 'ACTIVE' AND c.expiryDate > :currentDate AND c.deletedAt IS NULL")
    List<Card> findAllUsableCards(@Param("currentDate") LocalDate currentDate);

    /**
     * Find all blocked cards
     */
    @Query("SELECT c FROM Card c WHERE c.cardStatus IN ('BLOCKED', 'FRAUD_BLOCKED', 'LOST_STOLEN', 'SUSPENDED') AND c.deletedAt IS NULL")
    List<Card> findAllBlockedCards();

    /**
     * Find cards pending activation
     */
    @Query("SELECT c FROM Card c WHERE c.cardStatus IN ('PENDING_ACTIVATION', 'PENDING_USER_ACTIVATION') AND c.deletedAt IS NULL")
    List<Card> findCardsPendingActivation();

    // ========================================================================
    // EXPIRY QUERIES
    // ========================================================================

    /**
     * Find expired cards
     */
    @Query("SELECT c FROM Card c WHERE c.expiryDate < :currentDate AND c.cardStatus != 'EXPIRED' AND c.deletedAt IS NULL")
    List<Card> findExpiredCards(@Param("currentDate") LocalDate currentDate);

    /**
     * Find cards expiring soon (within specified days)
     */
    @Query("SELECT c FROM Card c WHERE c.expiryDate BETWEEN :currentDate AND :expiryDate AND c.cardStatus = 'ACTIVE' AND c.deletedAt IS NULL")
    List<Card> findCardsExpiringSoon(@Param("currentDate") LocalDate currentDate, @Param("expiryDate") LocalDate expiryDate);

    /**
     * Find cards expiring in specific month
     */
    @Query("SELECT c FROM Card c WHERE YEAR(c.expiryDate) = :year AND MONTH(c.expiryDate) = :month AND c.deletedAt IS NULL")
    List<Card> findCardsExpiringInMonth(@Param("year") int year, @Param("month") int month);

    // ========================================================================
    // FINANCIAL QUERIES
    // ========================================================================

    /**
     * Find cards with available credit below threshold
     */
    @Query("SELECT c FROM Card c WHERE c.availableCredit < :threshold AND c.cardStatus = 'ACTIVE' AND c.deletedAt IS NULL")
    List<Card> findCardsWithLowCredit(@Param("threshold") BigDecimal threshold);

    /**
     * Find cards with outstanding balance above threshold
     */
    @Query("SELECT c FROM Card c WHERE c.outstandingBalance > :threshold AND c.deletedAt IS NULL")
    List<Card> findCardsWithHighOutstandingBalance(@Param("threshold") BigDecimal threshold);

    /**
     * Find cards with overdue payments
     */
    @Query("SELECT c FROM Card c WHERE c.paymentDueDate < :currentDate AND c.outstandingBalance > 0 AND c.deletedAt IS NULL")
    List<Card> findCardsWithOverduePayments(@Param("currentDate") LocalDate currentDate);

    /**
     * Calculate total outstanding balance for user
     */
    @Query("SELECT COALESCE(SUM(c.outstandingBalance), 0) FROM Card c WHERE c.userId = :userId AND c.deletedAt IS NULL")
    BigDecimal calculateTotalOutstandingBalanceByUserId(@Param("userId") UUID userId);

    /**
     * Calculate total available credit for user
     */
    @Query("SELECT COALESCE(SUM(c.availableCredit), 0) FROM Card c WHERE c.userId = :userId AND c.cardStatus = 'ACTIVE' AND c.deletedAt IS NULL")
    BigDecimal calculateTotalAvailableCreditByUserId(@Param("userId") UUID userId);

    // ========================================================================
    // PIN QUERIES
    // ========================================================================

    /**
     * Find cards with locked PINs
     */
    @Query("SELECT c FROM Card c WHERE c.pinLockedUntil > :currentDateTime AND c.deletedAt IS NULL")
    List<Card> findCardsWithLockedPins(@Param("currentDateTime") LocalDateTime currentDateTime);

    /**
     * Find cards without PIN set
     */
    @Query("SELECT c FROM Card c WHERE c.pinSet = false AND c.cardStatus = 'ACTIVE' AND c.deletedAt IS NULL")
    List<Card> findCardsWithoutPinSet();

    // ========================================================================
    // TRANSACTION ACTIVITY QUERIES
    // ========================================================================

    /**
     * Find cards with recent activity (transactions in last N days)
     */
    @Query("SELECT c FROM Card c WHERE c.lastTransactionDate > :sinceDate AND c.deletedAt IS NULL")
    List<Card> findCardsWithRecentActivity(@Param("sinceDate") LocalDateTime sinceDate);

    /**
     * Find inactive cards (no transactions in last N days)
     */
    @Query("SELECT c FROM Card c WHERE (c.lastTransactionDate IS NULL OR c.lastTransactionDate < :sinceDate) AND c.cardStatus = 'ACTIVE' AND c.deletedAt IS NULL")
    List<Card> findInactiveCards(@Param("sinceDate") LocalDateTime sinceDate);

    // ========================================================================
    // REPLACEMENT QUERIES
    // ========================================================================

    /**
     * Find replacement card by original card ID
     */
    Optional<Card> findByReplacedCardId(String replacedCardId);

    /**
     * Find cards that have been replaced
     */
    @Query("SELECT c FROM Card c WHERE c.replacementCardId IS NOT NULL AND c.deletedAt IS NULL")
    List<Card> findReplacedCards();

    // ========================================================================
    // TYPE & BRAND QUERIES
    // ========================================================================

    /**
     * Find cards by type
     */
    List<Card> findByCardType(CardType cardType);

    /**
     * Find cards by type and status
     */
    List<Card> findByCardTypeAndCardStatus(CardType cardType, CardStatus cardStatus);

    /**
     * Find virtual cards
     */
    @Query("SELECT c FROM Card c WHERE c.isVirtual = true AND c.deletedAt IS NULL")
    List<Card> findVirtualCards();

    /**
     * Find physical cards
     */
    @Query("SELECT c FROM Card c WHERE c.isVirtual = false AND c.deletedAt IS NULL")
    List<Card> findPhysicalCards();

    // ========================================================================
    // DELIVERY QUERIES
    // ========================================================================

    /**
     * Find cards by delivery status
     */
    List<Card> findByDeliveryStatus(String deliveryStatus);

    /**
     * Find cards in transit
     */
    @Query("SELECT c FROM Card c WHERE c.deliveryStatus = 'IN_TRANSIT' AND c.deliveredAt IS NULL AND c.deletedAt IS NULL")
    List<Card> findCardsInTransit();

    // ========================================================================
    // STATISTICAL QUERIES
    // ========================================================================

    /**
     * Count cards by status
     */
    long countByCardStatus(CardStatus cardStatus);

    /**
     * Count cards by type
     */
    long countByCardType(CardType cardType);

    /**
     * Get card statistics for user
     */
    @Query("SELECT c.cardStatus, COUNT(c) FROM Card c WHERE c.userId = :userId AND c.deletedAt IS NULL GROUP BY c.cardStatus")
    List<Object[]> getCardStatisticsByUserId(@Param("userId") UUID userId);

    /**
     * Get card statistics by product
     */
    @Query("SELECT c.productId, COUNT(c) FROM Card c WHERE c.deletedAt IS NULL GROUP BY c.productId")
    List<Object[]> getCardStatisticsByProduct();

    // ========================================================================
    // SOFT DELETE QUERIES
    // ========================================================================

    /**
     * Find deleted cards
     */
    @Query("SELECT c FROM Card c WHERE c.deletedAt IS NOT NULL")
    List<Card> findDeletedCards();

    /**
     * Find cards deleted after specific date
     */
    @Query("SELECT c FROM Card c WHERE c.deletedAt > :deletedAfter")
    List<Card> findCardsDeletedAfter(@Param("deletedAfter") LocalDateTime deletedAfter);

    // ========================================================================
    // BULK OPERATIONS
    // ========================================================================

    /**
     * Find cards by multiple card IDs
     */
    @Query("SELECT c FROM Card c WHERE c.cardId IN :cardIds AND c.deletedAt IS NULL")
    List<Card> findByCardIdIn(@Param("cardIds") List<String> cardIds);

    /**
     * Find cards by multiple user IDs
     */
    @Query("SELECT c FROM Card c WHERE c.userId IN :userIds AND c.deletedAt IS NULL")
    List<Card> findByUserIdIn(@Param("userIds") List<UUID> userIds);
}
