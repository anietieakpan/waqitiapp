package com.waqiti.payment.repository;

import com.waqiti.payment.entity.Merchant;
import com.waqiti.payment.entity.MerchantStatus;
import com.waqiti.payment.entity.MerchantCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for merchant management with comprehensive business features
 */
@Repository
public interface MerchantRepository extends JpaRepository<Merchant, UUID>, 
                                   JpaSpecificationExecutor<Merchant> {

    /**
     * Find merchant by merchant ID
     */
    Optional<Merchant> findByMerchantId(String merchantId);
    
    /**
     * Find merchant by business registration number
     */
    Optional<Merchant> findByBusinessRegistrationNumber(String registrationNumber);
    
    /**
     * Find merchant by tax ID
     */
    Optional<Merchant> findByTaxId(String taxId);
    
    /**
     * Check if merchant exists
     */
    boolean existsByMerchantId(String merchantId);
    
    /**
     * Find merchants by status
     */
    Page<Merchant> findByStatusOrderByCreatedAtDesc(MerchantStatus status, Pageable pageable);
    
    /**
     * Find merchants by category
     */
    Page<Merchant> findByCategoryOrderByBusinessNameAsc(MerchantCategory category, Pageable pageable);
    
    /**
     * Find active merchants
     */
    List<Merchant> findByStatus(MerchantStatus status);
    
    /**
     * Search merchants by name
     */
    @Query("SELECT m FROM Merchant m WHERE " +
           "LOWER(m.businessName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
           "OR LOWER(m.displayName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
           "ORDER BY m.businessName")
    Page<Merchant> searchByName(@Param("searchTerm") String searchTerm, Pageable pageable);
    
    /**
     * Find merchants by location proximity
     */
    @Query("SELECT m FROM Merchant m WHERE " +
           "m.latitude IS NOT NULL AND m.longitude IS NOT NULL " +
           "AND m.status = 'ACTIVE' " +
           "AND 6371 * acos(cos(radians(:lat)) * cos(radians(m.latitude)) * " +
           "cos(radians(m.longitude) - radians(:lng)) + " +
           "sin(radians(:lat)) * sin(radians(m.latitude))) <= :radiusKm " +
           "ORDER BY m.businessName")
    List<Merchant> findNearbyMerchants(@Param("lat") Double latitude,
                                      @Param("lng") Double longitude,
                                      @Param("radiusKm") Double radiusKm);
    
    /**
     * Find merchants requiring KYB verification
     */
    @Query("SELECT m FROM Merchant m WHERE " +
           "m.kybStatus IN ('PENDING', 'INCOMPLETE') " +
           "AND m.createdAt < :cutoffTime")
    List<Merchant> findMerchantsRequiringKYB(@Param("cutoffTime") LocalDateTime cutoffTime);
    
    /**
     * Find merchants with expired documents
     */
    @Query("SELECT m FROM Merchant m WHERE " +
           "m.documentsExpiryDate < :now " +
           "AND m.status = 'ACTIVE'")
    List<Merchant> findMerchantsWithExpiredDocuments(@Param("now") LocalDateTime now);
    
    /**
     * Get merchant transaction statistics
     */
    @Query("SELECT " +
           "COUNT(t) as transactionCount, " +
           "SUM(t.amount) as totalVolume, " +
           "AVG(t.amount) as averageTransactionAmount, " +
           "SUM(t.processingFee) as totalFees " +
           "FROM PaymentTransaction t " +
           "WHERE t.payeeId = :merchantId " +
           "AND t.status = 'COMPLETED' " +
           "AND t.createdAt BETWEEN :startDate AND :endDate")
    Object getMerchantStatistics(@Param("merchantId") UUID merchantId,
                                @Param("startDate") LocalDateTime startDate,
                                @Param("endDate") LocalDateTime endDate);
    
    /**
     * Find high-volume merchants
     */
    @Query("SELECT m FROM Merchant m WHERE " +
           "m.monthlyTransactionVolume > :volumeThreshold " +
           "ORDER BY m.monthlyTransactionVolume DESC")
    List<Merchant> findHighVolumeMerchants(@Param("volumeThreshold") BigDecimal volumeThreshold);
    
    /**
     * Update merchant status
     */
    @Modifying
    @Transactional
    @Query("UPDATE Merchant m SET " +
           "m.status = :status, " +
           "m.statusUpdatedAt = CURRENT_TIMESTAMP " +
           "WHERE m.id = :merchantId")
    int updateMerchantStatus(@Param("merchantId") UUID merchantId,
                            @Param("status") MerchantStatus status);
    
    /**
     * Update merchant risk level
     */
    @Modifying
    @Transactional
    @Query("UPDATE Merchant m SET " +
           "m.riskLevel = :riskLevel, " +
           "m.riskAssessmentDate = CURRENT_TIMESTAMP " +
           "WHERE m.id = :merchantId")
    int updateRiskLevel(@Param("merchantId") UUID merchantId,
                       @Param("riskLevel") String riskLevel);
    
    /**
     * Update settlement details
     */
    @Modifying
    @Transactional
    @Query("UPDATE Merchant m SET " +
           "m.settlementAccountNumber = :accountNumber, " +
           "m.settlementRoutingNumber = :routingNumber, " +
           "m.settlementFrequency = :frequency " +
           "WHERE m.id = :merchantId")
    int updateSettlementDetails(@Param("merchantId") UUID merchantId,
                               @Param("accountNumber") String accountNumber,
                               @Param("routingNumber") String routingNumber,
                               @Param("frequency") String frequency);
    
    /**
     * Find merchants by owner ID
     */
    List<Merchant> findByOwnerIdOrderByCreatedAtDesc(UUID ownerId);
    
    /**
     * Count active merchants by category
     */
    @Query("SELECT m.category, COUNT(m) FROM Merchant m " +
           "WHERE m.status = 'ACTIVE' " +
           "GROUP BY m.category")
    List<Object[]> countActiveMerchantsByCategory();
    
    /**
     * Find merchants with pending settlements
     */
    @Query("SELECT m FROM Merchant m WHERE " +
           "m.pendingSettlementAmount > 0 " +
           "AND m.nextSettlementDate <= :now " +
           "ORDER BY m.pendingSettlementAmount DESC")
    List<Merchant> findMerchantsWithPendingSettlements(@Param("now") LocalDateTime now);
    
    /**
     * Get top merchants by revenue
     */
    @Query("SELECT m FROM Merchant m " +
           "ORDER BY m.totalRevenue DESC")
    List<Merchant> findTopMerchantsByRevenue(Pageable pageable);
    
    /**
     * Find suspended merchants for review
     */
    @Query("SELECT m FROM Merchant m WHERE " +
           "m.status = 'SUSPENDED' " +
           "AND m.suspensionReviewDate <= :now")
    List<Merchant> findSuspendedMerchantsForReview(@Param("now") LocalDateTime now);
    
    /**
     * Update transaction metrics
     */
    @Modifying
    @Transactional
    @Query("UPDATE Merchant m SET " +
           "m.totalTransactionCount = m.totalTransactionCount + 1, " +
           "m.monthlyTransactionVolume = m.monthlyTransactionVolume + :amount, " +
           "m.lastTransactionDate = CURRENT_TIMESTAMP " +
           "WHERE m.id = :merchantId")
    int updateTransactionMetrics(@Param("merchantId") UUID merchantId,
                                @Param("amount") BigDecimal amount);
}