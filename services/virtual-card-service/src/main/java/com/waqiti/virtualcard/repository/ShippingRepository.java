package com.waqiti.virtualcard.repository;

import com.waqiti.virtualcard.domain.Shipping;
import com.waqiti.virtualcard.domain.enums.ShippingStatus;
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
 * Repository for Shipping operations
 */
@Repository
public interface ShippingRepository extends JpaRepository<Shipping, String> {
    
    /**
     * Find shipping record by card ID
     */
    Optional<Shipping> findByCardId(String cardId);
    
    /**
     * Find shipping record by order ID
     */
    Optional<Shipping> findByOrderId(String orderId);
    
    /**
     * Find shipping record by tracking number
     */
    Optional<Shipping> findByTrackingNumber(String trackingNumber);
    
    /**
     * Find shipments by status
     */
    List<Shipping> findByStatus(ShippingStatus status);
    
    /**
     * Find shipments by multiple statuses
     */
    List<Shipping> findByStatusIn(List<ShippingStatus> statuses);
    
    /**
     * Find active shipments (in transit)
     */
    @Query("SELECT s FROM Shipping s WHERE s.status IN ('SHIPPED', 'IN_TRANSIT', 'OUT_FOR_DELIVERY')")
    List<Shipping> findActiveShipments();
    
    /**
     * Find shipments by carrier
     */
    List<Shipping> findByCarrier(String carrier);
    
    /**
     * Find shipments that need tracking updates
     */
    @Query("SELECT s FROM Shipping s WHERE s.status IN ('SHIPPED', 'IN_TRANSIT', 'OUT_FOR_DELIVERY') " +
           "AND (s.lastUpdate IS NULL OR s.lastUpdate < :updateThreshold)")
    List<Shipping> findShipmentsNeedingUpdate(@Param("updateThreshold") Instant updateThreshold);
    
    /**
     * Find overdue shipments
     */
    @Query("SELECT s FROM Shipping s WHERE s.status NOT IN ('DELIVERED', 'RETURNED_TO_SENDER', 'LOST') " +
           "AND s.estimatedDelivery < :overdueThreshold")
    List<Shipping> findOverdueShipments(@Param("overdueThreshold") Instant overdueThreshold);
    
    /**
     * Find shipments with delivery attempts
     */
    @Query("SELECT s FROM Shipping s WHERE s.deliveryAttempts > 0 AND s.status != 'DELIVERED'")
    List<Shipping> findShipmentsWithDeliveryAttempts();
    
    /**
     * Find shipments by country
     */
    @Query("SELECT s FROM Shipping s WHERE s.address.country = :country")
    List<Shipping> findByCountry(@Param("country") String country);
    
    /**
     * Find shipments created within date range
     */
    @Query("SELECT s FROM Shipping s WHERE s.createdAt BETWEEN :startDate AND :endDate " +
           "ORDER BY s.createdAt DESC")
    List<Shipping> findByCreatedAtBetween(@Param("startDate") Instant startDate, 
                                          @Param("endDate") Instant endDate);
    
    /**
     * Find shipments delivered within date range
     */
    @Query("SELECT s FROM Shipping s WHERE s.actualDelivery BETWEEN :startDate AND :endDate " +
           "ORDER BY s.actualDelivery DESC")
    List<Shipping> findDeliveredBetween(@Param("startDate") Instant startDate, 
                                        @Param("endDate") Instant endDate);
    
    /**
     * Find shipments with exceptions
     */
    @Query("SELECT s FROM Shipping s WHERE s.status IN ('EXCEPTION', 'DELIVERY_ATTEMPTED', 'RETURNED_TO_SENDER')")
    List<Shipping> findShipmentsWithExceptions();
    
    /**
     * Count shipments by status for dashboard
     */
    @Query("SELECT s.status, COUNT(s) FROM Shipping s GROUP BY s.status")
    List<Object[]> countShipmentsByStatus();
    
    /**
     * Find shipments by status with pagination
     */
    Page<Shipping> findByStatusOrderByCreatedAtDesc(ShippingStatus status, Pageable pageable);
    
    /**
     * Calculate average delivery time for completed shipments
     */
    @Query("SELECT AVG(EXTRACT(EPOCH FROM (s.actualDelivery - s.shippedAt))/86400) " +
           "FROM Shipping s WHERE s.status = 'DELIVERED' AND s.shippedAt IS NOT NULL " +
           "AND s.actualDelivery IS NOT NULL AND s.createdAt BETWEEN :startDate AND :endDate")
    Double calculateAverageDeliveryTime(@Param("startDate") Instant startDate, 
                                        @Param("endDate") Instant endDate);
    
    /**
     * Find shipments by postal code (for regional analysis)
     */
    @Query("SELECT s FROM Shipping s WHERE s.address.postalCode = :postalCode")
    List<Shipping> findByPostalCode(@Param("postalCode") String postalCode);
}