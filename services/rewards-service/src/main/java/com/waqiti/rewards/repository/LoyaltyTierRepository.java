package com.waqiti.rewards.repository;

import com.waqiti.rewards.entity.RewardsTier;
import com.waqiti.rewards.enums.LoyaltyTier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LoyaltyTierRepository extends JpaRepository<RewardsTier, UUID> {
    
    /**
     * Find tier by tier level
     */
    Optional<RewardsTier> findByTierLevel(int tierLevel);
    
    /**
     * Find tier by name
     */
    Optional<RewardsTier> findByTierNameIgnoreCase(String tierName);
    
    /**
     * Find all active tiers ordered by level
     */
    List<RewardsTier> findByIsActiveTrueOrderByTierLevel();
    
    /**
     * Find tier suitable for spending amount
     */
    @Query("SELECT t FROM RewardsTier t WHERE t.pointsRequired <= :points AND t.isActive = true ORDER BY t.tierLevel DESC")
    List<RewardsTier> findTiersForPoints(@Param("points") long points);
    
    /**
     * Get next tier for current tier level
     */
    @Query("SELECT t FROM RewardsTier t WHERE t.tierLevel > :currentLevel AND t.isActive = true ORDER BY t.tierLevel ASC")
    Optional<RewardsTier> findNextTier(@Param("currentLevel") int currentLevel);
    
    /**
     * Get tier requirements for specific spending amount
     */
    @Query("SELECT t FROM RewardsTier t WHERE t.pointsRequired <= :spending AND t.isActive = true ORDER BY t.pointsRequired DESC")
    Optional<RewardsTier> findTierForSpending(@Param("spending") BigDecimal spending);
    
    /**
     * Check if tier exists by level
     */
    boolean existsByTierLevel(int tierLevel);
    
    /**
     * Get tier multiplier for specific tier
     */
    @Query("SELECT t.pointsMultiplier FROM RewardsTier t WHERE t.tierLevel = :tierLevel AND t.isActive = true")
    Optional<BigDecimal> getTierMultiplier(@Param("tierLevel") int tierLevel);
    
    /**
     * Get cashback rate for specific tier
     */
    @Query("SELECT t.cashbackRate FROM RewardsTier t WHERE t.tierLevel = :tierLevel AND t.isActive = true")
    Optional<BigDecimal> getTierCashbackRate(@Param("tierLevel") int tierLevel);
}