package com.waqiti.virtualcard.repository;

import com.waqiti.virtualcard.domain.CardOrder;
import com.waqiti.virtualcard.domain.enums.OrderStatus;
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
 * Repository for Card Order operations
 */
@Repository
public interface CardOrderRepository extends JpaRepository<CardOrder, String> {
    
    /**
     * Find order by ID and user ID
     */
    Optional<CardOrder> findByIdAndUserId(String id, String userId);
    
    /**
     * Find all orders for a user
     */
    List<CardOrder> findByUserIdOrderByOrderedAtDesc(String userId);
    
    /**
     * Find orders by user ID with pagination
     */
    Page<CardOrder> findByUserIdOrderByOrderedAtDesc(String userId, Pageable pageable);
    
    /**
     * Find first order by user ID ordered by creation date
     */
    Optional<CardOrder> findFirstByUserIdOrderByOrderedAtDesc(String userId);
    
    /**
     * Count orders by user ID and status
     */
    long countByUserIdAndStatusIn(String userId, List<OrderStatus> statuses);
    
    /**
     * Find orders by status
     */
    List<CardOrder> findByStatus(OrderStatus status);
    
    /**
     * Find orders by multiple statuses
     */
    List<CardOrder> findByStatusIn(List<OrderStatus> statuses);
    
    /**
     * Find order by provider order ID
     */
    Optional<CardOrder> findByProviderOrderId(String providerOrderId);
    
    /**
     * Find replacement orders for an original card
     */
    List<CardOrder> findByOriginalCardId(String originalCardId);
    
    /**
     * Find replacement orders
     */
    List<CardOrder> findByIsReplacementTrue();
    
    /**
     * Find orders that are overdue for completion
     */
    @Query("SELECT o FROM CardOrder o WHERE o.status IN ('PENDING', 'SUBMITTED', 'IN_PRODUCTION') " +
           "AND o.estimatedDelivery < :overdueThreshold")
    List<CardOrder> findOverdueOrders(@Param("overdueThreshold") Instant overdueThreshold);
    
    /**
     * Find orders created within date range
     */
    @Query("SELECT o FROM CardOrder o WHERE o.orderedAt BETWEEN :startDate AND :endDate " +
           "ORDER BY o.orderedAt DESC")
    List<CardOrder> findByOrderedAtBetween(@Param("startDate") Instant startDate, 
                                           @Param("endDate") Instant endDate);
    
    /**
     * Find active orders for user
     */
    @Query("SELECT o FROM CardOrder o WHERE o.userId = :userId " +
           "AND o.status IN ('PENDING', 'SUBMITTED', 'IN_PRODUCTION', 'READY_TO_SHIP', 'SHIPPED') " +
           "ORDER BY o.orderedAt DESC")
    List<CardOrder> findActiveOrdersByUserId(@Param("userId") String userId);
    
    /**
     * Find orders that need status update
     */
    @Query("SELECT o FROM CardOrder o WHERE o.status IN ('SUBMITTED', 'IN_PRODUCTION') " +
           "AND o.submittedAt < :updateThreshold")
    List<CardOrder> findOrdersNeedingStatusUpdate(@Param("updateThreshold") Instant updateThreshold);
    
    /**
     * Find orders by country for analytics
     */
    @Query("SELECT o FROM CardOrder o WHERE o.shippingAddress.country = :country " +
           "AND o.orderedAt BETWEEN :startDate AND :endDate")
    List<CardOrder> findOrdersByCountryAndDateRange(@Param("country") String country,
                                                     @Param("startDate") Instant startDate,
                                                     @Param("endDate") Instant endDate);
    
    /**
     * Count orders by status for dashboard
     */
    @Query("SELECT o.status, COUNT(o) FROM CardOrder o GROUP BY o.status")
    List<Object[]> countOrdersByStatus();
    
    /**
     * Find orders with high fees (for audit purposes)
     */
    @Query("SELECT o FROM CardOrder o WHERE o.totalFee > :feeThreshold " +
           "ORDER BY o.totalFee DESC")
    List<CardOrder> findHighFeeOrders(@Param("feeThreshold") java.math.BigDecimal feeThreshold);
}