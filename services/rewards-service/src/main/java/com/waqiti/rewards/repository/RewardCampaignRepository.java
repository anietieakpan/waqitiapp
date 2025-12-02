package com.waqiti.rewards.repository;

import com.waqiti.rewards.domain.RewardCampaign;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RewardCampaignRepository extends JpaRepository<RewardCampaign, UUID> {
    
    /**
     * Find active campaigns at a specific time
     */
    @Query("SELECT c FROM RewardCampaign c WHERE c.isActive = true AND c.startDate <= :now AND c.endDate >= :now")
    List<RewardCampaign> findActiveCampaigns(@Param("now") Instant now);
    
    /**
     * Find active campaign for merchant and category
     */
    @Query("SELECT c FROM RewardCampaign c WHERE c.isActive = true AND " +
           "c.startDate <= :now AND c.endDate >= :now AND " +
           "(c.merchantId = :merchantId OR c.merchantCategory = :category OR c.targetType = 'ALL') " +
           "ORDER BY c.priority DESC")
    Optional<RewardCampaign> findActiveCampaign(
        @Param("merchantId") String merchantId,
        @Param("category") String category,
        @Param("now") Instant now
    );
    
    /**
     * Find campaigns by merchant
     */
    List<RewardCampaign> findByMerchantIdAndIsActiveTrueOrderByStartDateDesc(String merchantId);
    
    /**
     * Find campaigns by category
     */
    List<RewardCampaign> findByMerchantCategoryAndIsActiveTrueOrderByStartDateDesc(String category);
    
    /**
     * Find featured campaigns
     */
    @Query("SELECT c FROM RewardCampaign c WHERE c.isActive = true AND c.isFeatured = true AND " +
           "c.startDate <= :now AND c.endDate >= :now ORDER BY c.priority DESC")
    List<RewardCampaign> findFeaturedCampaigns(@Param("now") Instant now);
    
    /**
     * Find campaigns ending soon
     */
    @Query("SELECT c FROM RewardCampaign c WHERE c.isActive = true AND " +
           "c.startDate <= :now AND c.endDate BETWEEN :now AND :endingSoon " +
           "ORDER BY c.endDate ASC")
    List<RewardCampaign> findCampaignsEndingSoon(
        @Param("now") Instant now, 
        @Param("endingSoon") Instant endingSoon
    );
    
    /**
     * Find campaigns by type
     */
    List<RewardCampaign> findByTargetTypeAndIsActiveTrueOrderByStartDateDesc(String targetType);
    
    /**
     * Search campaigns by name or description
     */
    @Query("SELECT c FROM RewardCampaign c WHERE c.isActive = true AND " +
           "(LOWER(c.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(c.description) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<RewardCampaign> searchCampaigns(@Param("search") String search, Pageable pageable);
    
    /**
     * Count active campaigns
     */
    @Query("SELECT COUNT(c) FROM RewardCampaign c WHERE c.isActive = true AND " +
           "c.startDate <= :now AND c.endDate >= :now")
    long countActiveCampaigns(@Param("now") Instant now);
    
    /**
     * Find campaigns by user eligibility (can be enhanced with user-specific criteria)
     */
    @Query("SELECT c FROM RewardCampaign c WHERE c.isActive = true AND " +
           "c.startDate <= :now AND c.endDate >= :now AND " +
           "(c.minTierLevel <= :userTierLevel)")
    List<RewardCampaign> findEligibleCampaigns(
        @Param("now") Instant now,
        @Param("userTierLevel") int userTierLevel
    );
    
    /**
     * Find campaigns that need processing (starting or ending)
     */
    @Query("SELECT c FROM RewardCampaign c WHERE " +
           "(c.startDate BETWEEN :start AND :end) OR " +
           "(c.endDate BETWEEN :start AND :end)")
    List<RewardCampaign> findCampaignsToProcess(
        @Param("start") Instant start,
        @Param("end") Instant end
    );
}