package com.waqiti.virtualcard.repository;

import com.waqiti.virtualcard.domain.CardStatus;
import com.waqiti.virtualcard.domain.CardType;
import com.waqiti.virtualcard.domain.VirtualCard;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface for VirtualCard entity
 */
@Repository
public interface VirtualCardRepository extends JpaRepository<VirtualCard, String> {
    
    /**
     * Find virtual cards by user ID
     */
    List<VirtualCard> findByUserId(String userId);
    
    /**
     * Find virtual cards by user ID and status
     */
    List<VirtualCard> findByUserIdAndStatus(String userId, CardStatus status);
    
    /**
     * Find virtual card by ID and user ID
     */
    Optional<VirtualCard> findByIdAndUserId(String id, String userId);
    
    /**
     * Count cards by user ID and status
     */
    int countByUserIdAndStatus(String userId, CardStatus status);
    
    /**
     * Count total cards by user ID
     */
    int countByUserId(String userId);
    
    /**
     * Find cards by card type
     */
    List<VirtualCard> findByCardType(CardType cardType);
    
    /**
     * Find cards by user ID and card type
     */
    List<VirtualCard> findByUserIdAndCardType(String userId, CardType cardType);
    
    /**
     * Find cards by masked card number
     */
    Optional<VirtualCard> findByMaskedCardNumber(String maskedCardNumber);
    
    /**
     * Find cards by network token
     */
    Optional<VirtualCard> findByNetworkToken(String networkToken);
    
    /**
     * Find cards created within date range
     */
    @Query("SELECT v FROM VirtualCard v WHERE v.createdAt BETWEEN :startDate AND :endDate")
    List<VirtualCard> findByCreatedAtBetween(@Param("startDate") LocalDateTime startDate, 
                                           @Param("endDate") LocalDateTime endDate);
    
    /**
     * Find cards by user ID with pagination
     */
    Page<VirtualCard> findByUserId(String userId, Pageable pageable);
    
    /**
     * Find cards by user ID and status with pagination
     */
    Page<VirtualCard> findByUserIdAndStatus(String userId, CardStatus status, Pageable pageable);
    
    /**
     * Find active cards that expire within days
     */
    @Query("SELECT v FROM VirtualCard v WHERE v.status = :status AND " +
           "FUNCTION('YEAR', CURRENT_DATE) * 12 + FUNCTION('MONTH', CURRENT_DATE) <= " +
           "v.expiryYear * 12 + v.expiryMonth AND " +
           "v.expiryYear * 12 + v.expiryMonth <= " +
           "FUNCTION('YEAR', CURRENT_DATE) * 12 + FUNCTION('MONTH', CURRENT_DATE) + :months")
    List<VirtualCard> findCardsExpiringWithinMonths(@Param("status") CardStatus status, 
                                                   @Param("months") int months);
    
    /**
     * Find expired cards
     */
    @Query("SELECT v FROM VirtualCard v WHERE v.status = :status AND " +
           "v.expiryYear * 12 + v.expiryMonth < " +
           "FUNCTION('YEAR', CURRENT_DATE) * 12 + FUNCTION('MONTH', CURRENT_DATE)")
    List<VirtualCard> findExpiredCards(@Param("status") CardStatus status);
    
    /**
     * Find unused cards (never used)
     */
    @Query("SELECT v FROM VirtualCard v WHERE v.usageCount = 0 AND v.status = :status")
    List<VirtualCard> findUnusedCards(@Param("status") CardStatus status);
    
    /**
     * Find cards not used since date
     */
    @Query("SELECT v FROM VirtualCard v WHERE v.lastUsedAt < :date AND v.status = :status")
    List<VirtualCard> findCardsNotUsedSince(@Param("date") LocalDateTime date, 
                                           @Param("status") CardStatus status);
    
    /**
     * Find most used cards by user
     */
    @Query("SELECT v FROM VirtualCard v WHERE v.userId = :userId ORDER BY v.usageCount DESC")
    List<VirtualCard> findMostUsedCardsByUser(@Param("userId") String userId, Pageable pageable);
    
    /**
     * Find cards with highest spending by user
     */
    @Query("SELECT v FROM VirtualCard v WHERE v.userId = :userId ORDER BY v.totalSpent DESC")
    List<VirtualCard> findHighestSpendingCardsByUser(@Param("userId") String userId, Pageable pageable);
    
    /**
     * Find locked cards
     */
    List<VirtualCard> findByIsLockedTrue();
    
    /**
     * Find locked cards by user
     */
    List<VirtualCard> findByUserIdAndIsLockedTrue(String userId);
    
    /**
     * Find cards by nickname
     */
    List<VirtualCard> findByNicknameContainingIgnoreCase(String nickname);
    
    /**
     * Find cards by user and nickname
     */
    List<VirtualCard> findByUserIdAndNicknameContainingIgnoreCase(String userId, String nickname);
    
    /**
     * Update card status
     */
    @Modifying
    @Query("UPDATE VirtualCard v SET v.status = :status, v.updatedAt = CURRENT_TIMESTAMP WHERE v.id = :id")
    int updateCardStatus(@Param("id") String id, @Param("status") CardStatus status);
    
    /**
     * Update card lock status
     */
    @Modifying
    @Query("UPDATE VirtualCard v SET v.isLocked = :locked, v.lockedAt = :lockedAt, " +
           "v.lockReason = :reason, v.updatedAt = CURRENT_TIMESTAMP WHERE v.id = :id")
    int updateCardLockStatus(@Param("id") String id, @Param("locked") boolean locked, 
                           @Param("lockedAt") LocalDateTime lockedAt, @Param("reason") String reason);
    
    /**
     * Update card usage statistics
     */
    @Modifying
    @Query("UPDATE VirtualCard v SET v.usageCount = v.usageCount + 1, " +
           "v.lastUsedAt = CURRENT_TIMESTAMP, v.updatedAt = CURRENT_TIMESTAMP WHERE v.id = :id")
    int incrementUsageCount(@Param("id") String id);
    
    /**
     * Update total spent amount
     */
    @Modifying
    @Query("UPDATE VirtualCard v SET v.totalSpent = v.totalSpent + :amount, " +
           "v.updatedAt = CURRENT_TIMESTAMP WHERE v.id = :id")
    int addToTotalSpent(@Param("id") String id, @Param("amount") java.math.BigDecimal amount);
    
    /**
     * Find cards by multiple statuses
     */
    List<VirtualCard> findByStatusIn(List<CardStatus> statuses);
    
    /**
     * Find cards by user and multiple statuses
     */
    List<VirtualCard> findByUserIdAndStatusIn(String userId, List<CardStatus> statuses);
    
    /**
     * Count cards by status
     */
    @Query("SELECT v.status, COUNT(v) FROM VirtualCard v GROUP BY v.status")
    List<Object[]> countCardsByStatus();
    
    /**
     * Count cards by user and status
     */
    @Query("SELECT v.status, COUNT(v) FROM VirtualCard v WHERE v.userId = :userId GROUP BY v.status")
    List<Object[]> countCardsByUserAndStatus(@Param("userId") String userId);
    
    /**
     * Count cards by card type
     */
    @Query("SELECT v.cardType, COUNT(v) FROM VirtualCard v GROUP BY v.cardType")
    List<Object[]> countCardsByType();
    
    /**
     * Find cards requiring CVV rotation
     */
    @Query("SELECT v FROM VirtualCard v WHERE v.status = :status AND " +
           "(v.cvvRotatedAt IS NULL OR v.cvvRotatedAt < :rotationDate)")
    List<VirtualCard> findCardsRequiringCvvRotation(@Param("status") CardStatus status, 
                                                   @Param("rotationDate") LocalDateTime rotationDate);
    
    /**
     * Bulk update card statuses
     */
    @Modifying
    @Query("UPDATE VirtualCard v SET v.status = :newStatus, v.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE v.id IN :cardIds")
    int bulkUpdateCardStatus(@Param("cardIds") List<String> cardIds, @Param("newStatus") CardStatus newStatus);
}