package com.waqiti.virtualcard.repository;

import com.waqiti.virtualcard.domain.PhysicalCard;
import com.waqiti.virtualcard.domain.enums.CardStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository for Physical Card operations
 */
@Repository
public interface PhysicalCardRepository extends JpaRepository<PhysicalCard, String> {
    
    /**
     * Find physical card by ID and user ID
     */
    Optional<PhysicalCard> findByIdAndUserId(String id, String userId);
    
    /**
     * Find all physical cards for a user
     */
    List<PhysicalCard> findByUserIdOrderByCreatedAtDesc(String userId);
    
    /**
     * Find active physical cards for a user
     */
    List<PhysicalCard> findByUserIdAndStatusInOrderByCreatedAtDesc(String userId, List<CardStatus> statuses);
    
    /**
     * Count active physical cards for a user
     */
    long countByUserIdAndStatusIn(String userId, List<CardStatus> statuses);
    
    /**
     * Find cards by status
     */
    List<PhysicalCard> findByStatus(CardStatus status);
    
    /**
     * Find cards by multiple statuses
     */
    List<PhysicalCard> findByStatusIn(List<CardStatus> statuses);
    
    /**
     * Find card by provider ID
     */
    Optional<PhysicalCard> findByProviderId(String providerId);
    
    /**
     * Find cards by order ID
     */
    List<PhysicalCard> findByOrderId(String orderId);
    
    /**
     * Find replacement cards for an original card
     */
    List<PhysicalCard> findByOriginalCardId(String originalCardId);
    
    /**
     * Find cards that need activation reminder
     */
    @Query("SELECT c FROM PhysicalCard c WHERE c.status = 'DELIVERED' AND c.activatedAt IS NULL " +
           "AND c.deliveredAt < :reminderThreshold")
    List<PhysicalCard> findCardsNeedingActivationReminder(@Param("reminderThreshold") Instant reminderThreshold);
    
    /**
     * Find expired cards that haven't been activated
     */
    @Query("SELECT c FROM PhysicalCard c WHERE c.status = 'DELIVERED' AND c.activatedAt IS NULL " +
           "AND c.deliveredAt < :expiryThreshold")
    List<PhysicalCard> findExpiredUnactivatedCards(@Param("expiryThreshold") Instant expiryThreshold);
    
    /**
     * Find cards expiring soon
     */
    @Query("SELECT c FROM PhysicalCard c WHERE c.status = 'ACTIVE' " +
           "AND CONCAT(c.expiryYear, '-', LPAD(CAST(c.expiryMonth AS string), 2, '0'), '-01') < :expiryThreshold")
    List<PhysicalCard> findCardsExpiringSoon(@Param("expiryThreshold") Instant expiryThreshold);
    
    /**
     * Find cards for user with pagination
     */
    Page<PhysicalCard> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);
    
    /**
     * Find cards by status with pagination
     */
    Page<PhysicalCard> findByStatusOrderByCreatedAtDesc(CardStatus status, Pageable pageable);
    
    /**
     * Check if user has any active orders
     */
    @Query("SELECT COUNT(c) > 0 FROM PhysicalCard c WHERE c.userId = :userId " +
           "AND c.status IN ('ORDERED', 'IN_PRODUCTION', 'SHIPPED')")
    boolean hasActiveOrders(@Param("userId") String userId);
    
    /**
     * Find cards created within date range
     */
    @Query("SELECT c FROM PhysicalCard c WHERE c.createdAt BETWEEN :startDate AND :endDate " +
           "ORDER BY c.createdAt DESC")
    List<PhysicalCard> findByCreatedAtBetween(@Param("startDate") Instant startDate, 
                                              @Param("endDate") Instant endDate);
    
    /**
     * Find cards by last four digits (for support purposes)
     */
    @Query("SELECT c FROM PhysicalCard c WHERE c.lastFourDigits = :lastFour " +
           "AND c.userId = :userId ORDER BY c.createdAt DESC")
    List<PhysicalCard> findByUserIdAndLastFourDigits(@Param("userId") String userId, 
                                                      @Param("lastFour") String lastFour);
}