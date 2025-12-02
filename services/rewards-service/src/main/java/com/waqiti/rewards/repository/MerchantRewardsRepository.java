package com.waqiti.rewards.repository;

import com.waqiti.rewards.domain.MerchantRewards;
import com.waqiti.rewards.enums.MerchantStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MerchantRewardsRepository extends JpaRepository<MerchantRewards, UUID> {
    
    /**
     * Find merchant rewards by merchant ID and status
     */
    Optional<MerchantRewards> findByMerchantIdAndStatus(String merchantId, MerchantStatus status);
    
    /**
     * Find all active merchant rewards
     */
    List<MerchantRewards> findByStatusOrderByCashbackRateDesc(MerchantStatus status);
    
    /**
     * Find merchant rewards by category
     */
    List<MerchantRewards> findByCategoryAndStatusOrderByCashbackRateDesc(
        String category, MerchantStatus status);
    
    /**
     * Find featured merchant rewards
     */
    List<MerchantRewards> findByIsFeaturedTrueAndStatusOrderByCashbackRateDesc(MerchantStatus status);
    
    /**
     * Search merchants by name
     */
    @Query("SELECT m FROM MerchantRewards m WHERE m.status = :status AND " +
           "LOWER(m.merchantName) LIKE LOWER(CONCAT('%', :name, '%'))")
    Page<MerchantRewards> findByMerchantNameContainingIgnoreCase(
        @Param("name") String name,
        @Param("status") MerchantStatus status,
        Pageable pageable
    );
    
    /**
     * Find merchants with cashback rate above threshold
     */
    @Query("SELECT m FROM MerchantRewards m WHERE m.status = :status AND " +
           "m.cashbackRate >= :minRate ORDER BY m.cashbackRate DESC")
    List<MerchantRewards> findByMinimumCashbackRate(
        @Param("minRate") BigDecimal minRate,
        @Param("status") MerchantStatus status
    );
    
    /**
     * Find merchants with active promotions
     */
    @Query("SELECT m FROM MerchantRewards m WHERE m.status = :status AND " +
           "m.hasActivePromotion = true AND " +
           "(m.promotionEndDate IS NULL OR m.promotionEndDate >= :now)")
    List<MerchantRewards> findMerchantsWithActivePromotions(
        @Param("status") MerchantStatus status,
        @Param("now") Instant now
    );
    
    /**
     * Get top merchants by cashback rate in category
     */
    @Query("SELECT m FROM MerchantRewards m WHERE m.category = :category AND m.status = :status " +
           "ORDER BY m.cashbackRate DESC")
    Page<MerchantRewards> findTopMerchantsByCategory(
        @Param("category") String category,
        @Param("status") MerchantStatus status,
        Pageable pageable
    );
    
    /**
     * Find merchants by multiple categories
     */
    @Query("SELECT m FROM MerchantRewards m WHERE m.category IN :categories AND m.status = :status " +
           "ORDER BY m.cashbackRate DESC")
    List<MerchantRewards> findByCategories(
        @Param("categories") List<String> categories,
        @Param("status") MerchantStatus status
    );
    
    /**
     * Find recently added merchants
     */
    @Query("SELECT m FROM MerchantRewards m WHERE m.status = :status AND " +
           "m.createdAt >= :since ORDER BY m.createdAt DESC")
    List<MerchantRewards> findRecentlyAdded(
        @Param("status") MerchantStatus status,
        @Param("since") Instant since
    );
    
    /**
     * Get merchant statistics by category
     */
    @Query("SELECT m.category, COUNT(m), AVG(m.cashbackRate), MAX(m.cashbackRate)" +
           "FROM MerchantRewards m WHERE m.status = :status GROUP BY m.category")
    List<Object[]> getMerchantStatsByCategory(@Param("status") MerchantStatus status);
    
    /**
     * Find merchants with expiring promotions
     */
    @Query("SELECT m FROM MerchantRewards m WHERE m.status = :status AND " +
           "m.hasActivePromotion = true AND " +
           "m.promotionEndDate BETWEEN :now AND :expiringBefore")
    List<MerchantRewards> findMerchantsWithExpiringPromotions(
        @Param("status") MerchantStatus status,
        @Param("now") Instant now,
        @Param("expiringBefore") Instant expiringBefore
    );
    
    /**
     * Check if merchant exists by external ID
     */
    boolean existsByMerchantId(String merchantId);
    
    /**
     * Get cashback rate for merchant
     */
    @Query("SELECT m.cashbackRate FROM MerchantRewards m WHERE m.merchantId = :merchantId AND m.status = :status")
    Optional<BigDecimal> getCashbackRateByMerchant(
        @Param("merchantId") String merchantId,
        @Param("status") MerchantStatus status
    );
    
    /**
     * Update merchant status
     */
    @Modifying
    @Query("UPDATE MerchantRewards m SET m.status = :newStatus, m.updatedAt = :now " +
           "WHERE m.merchantId = :merchantId")
    int updateMerchantStatus(
        @Param("merchantId") String merchantId,
        @Param("newStatus") MerchantStatus newStatus,
        @Param("now") Instant now
    );
    
    /**
     * Find by status and merchant name containing (for search)
     */
    Page<MerchantRewards> findByStatusAndMerchantNameContainingIgnoreCase(
        MerchantStatus status, String name, Pageable pageable);
    
    /**
     * Find by status and category
     */
    Page<MerchantRewards> findByStatusAndCategory(
        MerchantStatus status, String category, Pageable pageable);
    
    /**
     * Find by status (pageable)
     */
    Page<MerchantRewards> findByStatus(MerchantStatus status, Pageable pageable);
}