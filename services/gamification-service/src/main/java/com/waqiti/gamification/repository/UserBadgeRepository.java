package com.waqiti.gamification.repository;

import com.waqiti.gamification.domain.Badge;
import com.waqiti.gamification.domain.UserBadge;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserBadgeRepository extends JpaRepository<UserBadge, Long> {
    
    List<UserBadge> findByUserId(String userId);
    
    List<UserBadge> findByUserIdOrderByEarnedAtDesc(String userId);
    
    Optional<UserBadge> findByUserIdAndBadgeId(String userId, Long badgeId);
    
    boolean existsByUserIdAndBadgeId(String userId, Long badgeId);
    
    @Query("SELECT ub FROM UserBadge ub WHERE ub.userId = :userId AND ub.isDisplayed = true ORDER BY ub.displayPosition ASC")
    List<UserBadge> findDisplayedBadgesByUserId(@Param("userId") String userId);
    
    @Query("SELECT ub FROM UserBadge ub WHERE ub.userId = :userId AND ub.badge.category = :category ORDER BY ub.earnedAt DESC")
    List<UserBadge> findByUserIdAndBadgeCategory(@Param("userId") String userId, @Param("category") Badge.BadgeCategory category);
    
    @Query("SELECT ub FROM UserBadge ub WHERE ub.userId = :userId AND ub.badge.tier = :tier ORDER BY ub.earnedAt DESC")
    List<UserBadge> findByUserIdAndBadgeTier(@Param("userId") String userId, @Param("tier") Badge.BadgeTier tier);
    
    @Query("SELECT ub FROM UserBadge ub WHERE ub.earnedAt BETWEEN :startDate AND :endDate ORDER BY ub.earnedAt DESC")
    List<UserBadge> findBadgesEarnedBetween(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);
    
    @Query("SELECT ub FROM UserBadge ub WHERE ub.notificationSent = false")
    List<UserBadge> findUnnotifiedBadges();
    
    @Query("SELECT COUNT(ub) FROM UserBadge ub WHERE ub.userId = :userId")
    Long countBadgesByUserId(@Param("userId") String userId);
    
    @Query("SELECT ub.badge.category, COUNT(ub) FROM UserBadge ub WHERE ub.userId = :userId GROUP BY ub.badge.category")
    List<Object[]> findBadgeCountByCategoryForUser(@Param("userId") String userId);
    
    @Query("SELECT ub.badge.tier, COUNT(ub) FROM UserBadge ub WHERE ub.userId = :userId GROUP BY ub.badge.tier")
    List<Object[]> findBadgeCountByTierForUser(@Param("userId") String userId);
    
    @Query("SELECT ub FROM UserBadge ub WHERE ub.badge.id = :badgeId ORDER BY ub.earnedAt DESC")
    Page<UserBadge> findUsersByBadgeId(@Param("badgeId") Long badgeId, Pageable pageable);
    
    @Query("SELECT ub FROM UserBadge ub WHERE ub.userId = :userId AND ub.earnedAt >= :since ORDER BY ub.earnedAt DESC")
    List<UserBadge> findRecentBadgesByUserId(@Param("userId") String userId, @Param("since") LocalDateTime since);
    
    @Query("SELECT ub.badge, COUNT(ub) as badgeCount FROM UserBadge ub GROUP BY ub.badge ORDER BY badgeCount DESC")
    Page<Object[]> findMostEarnedBadges(Pageable pageable);
    
    @Query("SELECT ub.badge, COUNT(ub) as badgeCount FROM UserBadge ub WHERE ub.earnedAt >= :since GROUP BY ub.badge ORDER BY badgeCount DESC")
    Page<Object[]> findMostEarnedBadgesSince(@Param("since") LocalDateTime since, Pageable pageable);
    
    @Query("SELECT ub FROM UserBadge ub WHERE ub.sharedOnSocial = false AND ub.earnedAt >= :since")
    List<UserBadge> findUnsharedBadgesSince(@Param("since") LocalDateTime since);
    
    @Query("SELECT COUNT(ub) FROM UserBadge ub WHERE ub.earnedAt BETWEEN :startDate AND :endDate")
    Long countBadgesEarnedBetween(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);
    
    @Query("SELECT ub FROM UserBadge ub WHERE ub.userId = :userId AND ub.badge.isSecret = true")
    List<UserBadge> findSecretBadgesByUserId(@Param("userId") String userId);
}