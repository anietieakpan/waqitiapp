package com.waqiti.rewards.repository;

import com.waqiti.rewards.domain.RewardsAccount;
import com.waqiti.rewards.enums.AccountStatus;
import com.waqiti.rewards.enums.LoyaltyTier;
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
public interface RewardsAccountRepository extends JpaRepository<RewardsAccount, UUID> {
    
    Optional<RewardsAccount> findByUserId(String userId);
    
    boolean existsByUserId(String userId);
    
    @Query("SELECT ra FROM RewardsAccount ra WHERE ra.status = :status")
    List<RewardsAccount> findByStatus(@Param("status") AccountStatus status);
    
    @Query("SELECT ra FROM RewardsAccount ra WHERE ra.currentTier = :tier AND ra.status = 'ACTIVE'")
    Page<RewardsAccount> findByTier(@Param("tier") LoyaltyTier tier, Pageable pageable);
    
    @Query("SELECT ra FROM RewardsAccount ra WHERE ra.lastActivity < :date AND ra.status = 'ACTIVE'")
    List<RewardsAccount> findInactiveAccounts(@Param("date") Instant date);
    
    @Query("SELECT ra FROM RewardsAccount ra WHERE ra.cashbackBalance >= :minBalance")
    List<RewardsAccount> findAccountsWithMinimumCashback(@Param("minBalance") BigDecimal minBalance);
    
    @Query("SELECT ra FROM RewardsAccount ra WHERE ra.preferences.autoRedeemCashback = true " +
           "AND ra.cashbackBalance >= CAST(ra.preferences.autoRedeemThreshold AS decimal)")
    List<RewardsAccount> findAccountsForAutoRedemption();
    
    @Query("SELECT COUNT(ra) FROM RewardsAccount ra WHERE ra.status = :status")
    long countByStatus(@Param("status") AccountStatus status);
    
    @Query("SELECT COUNT(ra) FROM RewardsAccount ra WHERE ra.currentTier = :tier")
    long countByTier(@Param("tier") LoyaltyTier tier);
    
    @Query("SELECT SUM(ra.cashbackBalance) FROM RewardsAccount ra WHERE ra.status = 'ACTIVE'")
    BigDecimal getTotalCashbackBalance();
    
    @Query("SELECT SUM(ra.pointsBalance) FROM RewardsAccount ra WHERE ra.status = 'ACTIVE'")
    Long getTotalPointsBalance();
    
    @Modifying
    @Query("UPDATE RewardsAccount ra SET ra.lastActivity = :now WHERE ra.userId = :userId")
    void updateLastActivity(@Param("userId") String userId, @Param("now") Instant now);
    
    @Query("SELECT ra FROM RewardsAccount ra WHERE ra.tierProgress >= ra.tierProgressTarget " +
           "AND ra.currentTier != 'PLATINUM' AND ra.status = 'ACTIVE'")
    List<RewardsAccount> findAccountsEligibleForTierUpgrade();
    
    @Query(value = "SELECT * FROM rewards_accounts WHERE " +
           "enrollment_date >= :startDate AND enrollment_date <= :endDate " +
           "ORDER BY enrollment_date DESC", nativeQuery = true)
    Page<RewardsAccount> findNewEnrollments(
        @Param("startDate") Instant startDate, 
        @Param("endDate") Instant endDate, 
        Pageable pageable
    );
    
    /**
     * Get tier distribution for system metrics
     */
    @Query("SELECT ra.currentTier, COUNT(ra) FROM RewardsAccount ra " +
           "WHERE ra.status = 'ACTIVE' GROUP BY ra.currentTier")
    java.util.List<Object[]> getTierDistributionRaw();
    
    /**
     * Get tier distribution as map (helper method)
     */
    default java.util.Map<LoyaltyTier, Long> getTierDistribution() {
        java.util.List<Object[]> results = getTierDistributionRaw();
        java.util.Map<LoyaltyTier, Long> distribution = new java.util.HashMap<>();
        
        // Initialize all tiers with 0
        for (LoyaltyTier tier : LoyaltyTier.values()) {
            distribution.put(tier, 0L);
        }
        
        // Fill in actual counts
        for (Object[] result : results) {
            LoyaltyTier tier = (LoyaltyTier) result[0];
            Long count = (Long) result[1];
            distribution.put(tier, count);
        }
        
        return distribution;
    }
}