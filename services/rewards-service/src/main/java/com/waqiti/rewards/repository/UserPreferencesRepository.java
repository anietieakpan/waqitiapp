package com.waqiti.rewards.repository;

import com.waqiti.rewards.domain.UserRewardsPreferences;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserPreferencesRepository extends JpaRepository<UserRewardsPreferences, UUID> {
    
    /**
     * Find preferences by user ID
     */
    Optional<UserRewardsPreferences> findByUserId(String userId);
    
    /**
     * Check if user has preferences configured
     */
    boolean existsByUserId(String userId);
    
    /**
     * Find users with auto-redeem enabled
     */
    @Query("SELECT p FROM UserRewardsPreferences p WHERE p.autoRedeemCashback = true")
    List<UserRewardsPreferences> findUsersWithAutoRedeem();
    
    /**
     * Find users with notifications enabled
     */
    @Query("SELECT p FROM UserRewardsPreferences p WHERE p.notificationsEnabled = true")
    List<UserRewardsPreferences> findUsersWithNotificationsEnabled();
    
    /**
     * Find users who opted in for marketing
     */
    @Query("SELECT p FROM UserRewardsPreferences p WHERE p.marketingOptIn = true")
    List<UserRewardsPreferences> findUsersOptedInForMarketing();
    
    /**
     * Find users with specific redemption method preference
     */
    @Query("SELECT p FROM UserRewardsPreferences p WHERE p.preferredRedemptionMethod = :method")
    List<UserRewardsPreferences> findByPreferredRedemptionMethod(@Param("method") String method);
    
    /**
     * Find users with cashback enabled
     */
    @Query("SELECT p FROM UserRewardsPreferences p WHERE p.cashbackEnabled = true")
    List<UserRewardsPreferences> findUsersWithCashbackEnabled();
    
    /**
     * Find users with points enabled
     */
    @Query("SELECT p FROM UserRewardsPreferences p WHERE p.pointsEnabled = true")
    List<UserRewardsPreferences> findUsersWithPointsEnabled();
    
    /**
     * Update notification preferences
     */
    @Modifying
    @Query("UPDATE UserRewardsPreferences p SET p.notificationsEnabled = :enabled, " +
           "p.updatedAt = :now WHERE p.userId = :userId")
    int updateNotificationPreference(
        @Param("userId") String userId,
        @Param("enabled") boolean enabled,
        @Param("now") Instant now
    );
    
    /**
     * Update auto-redeem preference
     */
    @Modifying
    @Query("UPDATE UserRewardsPreferences p SET p.autoRedeemCashback = :enabled, " +
           "p.updatedAt = :now WHERE p.userId = :userId")
    int updateAutoRedeemPreference(
        @Param("userId") String userId,
        @Param("enabled") boolean enabled,
        @Param("now") Instant now
    );
    
    /**
     * Update marketing opt-in
     */
    @Modifying
    @Query("UPDATE UserRewardsPreferences p SET p.marketingOptIn = :optIn, " +
           "p.updatedAt = :now WHERE p.userId = :userId")
    int updateMarketingOptIn(
        @Param("userId") String userId,
        @Param("optIn") boolean optIn,
        @Param("now") Instant now
    );
    
    /**
     * Bulk update preferences for users
     */
    @Modifying
    @Query("UPDATE UserRewardsPreferences p SET p.notificationsEnabled = :enabled, " +
           "p.updatedAt = :now WHERE p.userId IN :userIds")
    int bulkUpdateNotificationPreferences(
        @Param("userIds") List<String> userIds,
        @Param("enabled") boolean enabled,
        @Param("now") Instant now
    );
    
    /**
     * Get users for targeted campaigns based on preferences
     */
    @Query("SELECT p.userId FROM UserRewardsPreferences p WHERE " +
           "p.marketingOptIn = true AND p.notificationsEnabled = true AND " +
           "(:cashbackEnabled IS NULL OR p.cashbackEnabled = :cashbackEnabled) AND " +
           "(:pointsEnabled IS NULL OR p.pointsEnabled = :pointsEnabled)")
    List<String> findUsersForTargetedCampaigns(
        @Param("cashbackEnabled") Boolean cashbackEnabled,
        @Param("pointsEnabled") Boolean pointsEnabled
    );
    
    /**
     * Count users by preference type
     */
    @Query("SELECT " +
           "SUM(CASE WHEN p.cashbackEnabled = true THEN 1 ELSE 0 END) as cashbackUsers, " +
           "SUM(CASE WHEN p.pointsEnabled = true THEN 1 ELSE 0 END) as pointsUsers, " +
           "SUM(CASE WHEN p.autoRedeemCashback = true THEN 1 ELSE 0 END) as autoRedeemUsers, " +
           "SUM(CASE WHEN p.notificationsEnabled = true THEN 1 ELSE 0 END) as notificationUsers " +
           "FROM UserRewardsPreferences p")
    Object[] getPreferenceStatistics();
    
    /**
     * Find inactive preferences (not updated recently)
     */
    @Query("SELECT p FROM UserRewardsPreferences p WHERE p.updatedAt < :threshold")
    List<UserRewardsPreferences> findInactivePreferences(@Param("threshold") Instant threshold);
    
    /**
     * Delete preferences by user ID
     */
    @Modifying
    @Query("DELETE FROM UserRewardsPreferences p WHERE p.userId = :userId")
    int deleteByUserId(@Param("userId") String userId);
}