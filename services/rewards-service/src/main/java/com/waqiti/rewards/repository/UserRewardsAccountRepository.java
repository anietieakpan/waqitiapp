package com.waqiti.rewards.repository;

import com.waqiti.rewards.entity.UserRewardsAccount;
import com.waqiti.rewards.enums.AccountStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for UserRewardsAccount with optimized fetch strategies
 * Implements performance-optimized queries to prevent N+1 problems
 */
@Repository
public interface UserRewardsAccountRepository extends JpaRepository<UserRewardsAccount, UUID> {
    
    /**
     * Find user rewards account by user ID - basic info only (no tier fetching)
     */
    Optional<UserRewardsAccount> findByUserId(String userId);
    
    /**
     * Find user rewards account with tier information - explicit JOIN FETCH
     */
    @Query("SELECT u FROM UserRewardsAccount u JOIN FETCH u.currentTier WHERE u.userId = :userId")
    Optional<UserRewardsAccount> findByUserIdWithTier(@Param("userId") String userId);
    
    /**
     * Find active accounts with tier information for dashboard display
     */
    @Query("SELECT u FROM UserRewardsAccount u JOIN FETCH u.currentTier WHERE u.status = :status")
    List<UserRewardsAccount> findByStatusWithTier(@Param("status") AccountStatus status);
    
    /**
     * Find accounts by tier ID - optimized for bulk operations
     */
    @Query("SELECT u FROM UserRewardsAccount u WHERE u.tierId = :tierId")
    List<UserRewardsAccount> findByTierId(@Param("tierId") UUID tierId);
    
    /**
     * Find top performers with tier details for leaderboard
     */
    @Query("SELECT u FROM UserRewardsAccount u JOIN FETCH u.currentTier " +
           "WHERE u.status = 'ACTIVE' ORDER BY u.totalPoints DESC")
    List<UserRewardsAccount> findTopPerformersWithTier(Pageable pageable);
    
    /**
     * Find users eligible for tier upgrade - performance optimized
     */
    @Query("SELECT u FROM UserRewardsAccount u WHERE u.tierProgressPoints >= " +
           "(SELECT t.pointsRequired FROM RewardsTier t WHERE t.id = u.tierId)")
    List<UserRewardsAccount> findUsersEligibleForTierUpgrade();
    
    /**
     * Find users with expiring tiers - notification system
     */
    @Query("SELECT u FROM UserRewardsAccount u WHERE u.tierExpiryDate <= :expiryThreshold AND u.status = 'ACTIVE'")
    List<UserRewardsAccount> findUsersWithExpiringTiers(@Param("expiryThreshold") LocalDateTime expiryThreshold);
    
    /**
     * Get user counts by tier - analytics dashboard
     */
    @Query("SELECT u.tierId, COUNT(u) FROM UserRewardsAccount u WHERE u.status = 'ACTIVE' GROUP BY u.tierId")
    List<Object[]> getUserCountsByTier();
    
    /**
     * Find high-value users with detailed info - VIP management
     */
    @Query("SELECT u FROM UserRewardsAccount u JOIN FETCH u.currentTier " +
           "WHERE u.lifetimeCashbackEarned >= :threshold AND u.status = 'ACTIVE' " +
           "ORDER BY u.lifetimeCashbackEarned DESC")
    List<UserRewardsAccount> findHighValueUsersWithTier(@Param("threshold") BigDecimal threshold);
    
    /**
     * Find inactive users for re-engagement campaigns
     */
    @Query("SELECT u FROM UserRewardsAccount u WHERE u.lastActivityDate < :cutoffDate AND u.status = 'ACTIVE'")
    List<UserRewardsAccount> findInactiveUsers(@Param("cutoffDate") LocalDateTime cutoffDate);
    
    /**
     * Update tier for multiple users - batch operation
     */
    @Modifying
    @Query("UPDATE UserRewardsAccount u SET u.tierId = :newTierId, u.tierProgressPoints = 0, " +
           "u.updatedAt = CURRENT_TIMESTAMP WHERE u.id IN :userIds")
    int updateTierForUsers(@Param("newTierId") UUID newTierId, @Param("userIds") List<UUID> userIds);
    
    /**
     * Update points balance efficiently
     */
    @Modifying
    @Query("UPDATE UserRewardsAccount u SET u.availablePoints = u.availablePoints + :points, " +
           "u.totalPoints = u.totalPoints + :points, u.lastActivityDate = CURRENT_TIMESTAMP, " +
           "u.updatedAt = CURRENT_TIMESTAMP WHERE u.userId = :userId")
    int addPoints(@Param("userId") String userId, @Param("points") Long points);
    
    /**
     * Update cashback balance efficiently
     */
    @Modifying
    @Query("UPDATE UserRewardsAccount u SET u.cashbackBalance = u.cashbackBalance + :amount, " +
           "u.lifetimeCashbackEarned = u.lifetimeCashbackEarned + :amount, " +
           "u.lastActivityDate = CURRENT_TIMESTAMP, u.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE u.userId = :userId")
    int addCashback(@Param("userId") String userId, @Param("amount") BigDecimal amount);
    
    /**
     * Get paginated accounts with tier info for admin dashboard
     */
    @Query("SELECT u FROM UserRewardsAccount u JOIN FETCH u.currentTier " +
           "WHERE (:status IS NULL OR u.status = :status) " +
           "ORDER BY u.createdAt DESC")
    Page<UserRewardsAccount> findAllWithTierInfo(@Param("status") AccountStatus status, Pageable pageable);
    
    /**
     * Find accounts by cashback range - targeted promotions
     */
    @Query("SELECT u FROM UserRewardsAccount u WHERE u.cashbackBalance BETWEEN :minAmount AND :maxAmount " +
           "AND u.status = 'ACTIVE'")
    List<UserRewardsAccount> findByCashbackRange(@Param("minAmount") BigDecimal minAmount, 
                                                  @Param("maxAmount") BigDecimal maxAmount);
    
    /**
     * Get tier progression analytics
     */
    @Query("SELECT u.tierId, AVG(u.tierProgressPoints), MIN(u.tierProgressPoints), MAX(u.tierProgressPoints) " +
           "FROM UserRewardsAccount u WHERE u.status = 'ACTIVE' GROUP BY u.tierId")
    List<Object[]> getTierProgressionAnalytics();
    
    /**
     * Find users who joined in date range - cohort analysis
     */
    @Query("SELECT u FROM UserRewardsAccount u WHERE u.enrollmentDate BETWEEN :startDate AND :endDate " +
           "ORDER BY u.enrollmentDate DESC")
    List<UserRewardsAccount> findByEnrollmentDateRange(@Param("startDate") LocalDateTime startDate,
                                                        @Param("endDate") LocalDateTime endDate);
    
    /**
     * Update activity streak efficiently
     */
    @Modifying
    @Query("UPDATE UserRewardsAccount u SET u.streakDays = u.streakDays + 1, " +
           "u.lastActivityDate = CURRENT_TIMESTAMP, u.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE u.userId = :userId")
    int incrementStreak(@Param("userId") String userId);
    
    /**
     * Reset streak for inactive users
     */
    @Modifying
    @Query("UPDATE UserRewardsAccount u SET u.streakDays = 0, u.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE u.lastActivityDate < :cutoffDate")
    int resetStreaksForInactiveUsers(@Param("cutoffDate") LocalDateTime cutoffDate);
    
    /**
     * Check if user exists and is active - lightweight query
     */
    @Query("SELECT COUNT(u) > 0 FROM UserRewardsAccount u WHERE u.userId = :userId AND u.status = 'ACTIVE'")
    boolean existsByUserIdAndActive(@Param("userId") String userId);
    
    /**
     * Get summary statistics for dashboard
     */
    @Query("SELECT COUNT(u), SUM(u.totalPoints), SUM(u.cashbackBalance), AVG(u.streakDays) " +
           "FROM UserRewardsAccount u WHERE u.status = 'ACTIVE'")
    Object[] getSummaryStatistics();
}