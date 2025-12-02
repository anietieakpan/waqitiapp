package com.waqiti.gamification.repository;

import com.waqiti.gamification.domain.UserPoints;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserPointsRepository extends JpaRepository<UserPoints, Long> {
    
    Optional<UserPoints> findByUserId(String userId);
    
    @Query("SELECT up FROM UserPoints up WHERE up.totalPoints >= :minPoints ORDER BY up.totalPoints DESC")
    List<UserPoints> findByTotalPointsGreaterThanEqualOrderByTotalPointsDesc(@Param("minPoints") Long minPoints);
    
    @Query("SELECT up FROM UserPoints up WHERE up.currentLevel = :level ORDER BY up.totalPoints DESC")
    List<UserPoints> findByCurrentLevelOrderByTotalPointsDesc(@Param("level") UserPoints.Level level);
    
    @Query("SELECT up FROM UserPoints up ORDER BY up.totalPoints DESC")
    Page<UserPoints> findAllOrderByTotalPointsDesc(Pageable pageable);
    
    @Query("SELECT up FROM UserPoints up WHERE up.lastActivityDate >= :since ORDER BY up.totalPoints DESC")
    Page<UserPoints> findActiveUsersOrderByTotalPointsDesc(@Param("since") LocalDateTime since, Pageable pageable);
    
    @Query("SELECT COUNT(up) FROM UserPoints up WHERE up.totalPoints > (SELECT up2.totalPoints FROM UserPoints up2 WHERE up2.userId = :userId)")
    Long findRankByUserId(@Param("userId") String userId);
    
    @Query("SELECT up FROM UserPoints up WHERE up.multiplierActive = true AND up.multiplierExpiresAt <= :now")
    List<UserPoints> findExpiredMultipliers(@Param("now") LocalDateTime now);
    
    @Modifying
    @Query("UPDATE UserPoints up SET up.multiplierActive = false, up.currentMultiplier = 1.0, up.multiplierExpiresAt = null WHERE up.multiplierActive = true AND up.multiplierExpiresAt <= :now")
    int deactivateExpiredMultipliers(@Param("now") LocalDateTime now);
    
    @Query("SELECT up FROM UserPoints up WHERE up.streakDays >= :minStreak ORDER BY up.streakDays DESC")
    List<UserPoints> findByStreakDaysGreaterThanEqual(@Param("minStreak") Integer minStreak);
    
    @Query("SELECT AVG(up.totalPoints) FROM UserPoints up")
    Double findAverageTotalPoints();
    
    @Query("SELECT up.currentLevel, COUNT(up) FROM UserPoints up GROUP BY up.currentLevel")
    List<Object[]> findLevelDistribution();
    
    @Query("SELECT up FROM UserPoints up WHERE up.monthlyPoints >= :threshold AND YEAR(up.lastActivityDate) = :year AND MONTH(up.lastActivityDate) = :month")
    List<UserPoints> findTopPerformersForMonth(@Param("threshold") Long threshold, @Param("year") int year, @Param("month") int month);
    
    @Modifying
    @Query("UPDATE UserPoints up SET up.monthlyPoints = 0 WHERE up.lastActivityDate < :cutoffDate")
    int resetMonthlyPoints(@Param("cutoffDate") LocalDateTime cutoffDate);
    
    @Modifying
    @Query("UPDATE UserPoints up SET up.weeklyPoints = 0 WHERE up.lastActivityDate < :cutoffDate")
    int resetWeeklyPoints(@Param("cutoffDate") LocalDateTime cutoffDate);
    
    @Modifying
    @Query("UPDATE UserPoints up SET up.dailyPoints = 0 WHERE up.lastActivityDate < :cutoffDate")
    int resetDailyPoints(@Param("cutoffDate") LocalDateTime cutoffDate);
    
    @Query("SELECT up FROM UserPoints up WHERE up.availablePoints >= :minPoints ORDER BY up.availablePoints DESC")
    List<UserPoints> findEligibleForRedemption(@Param("minPoints") Long minPoints);
    
    @Query("SELECT COALESCE(SUM(up.totalPoints), 0) FROM UserPoints up")
    Long getTotalPointsInSystem();
    
    @Query("SELECT COALESCE(SUM(up.availablePoints), 0) FROM UserPoints up")
    Long getTotalAvailablePoints();
    
    @Query("SELECT up FROM UserPoints up WHERE up.lastActivityDate IS NULL OR up.lastActivityDate < :inactiveThreshold")
    List<UserPoints> findInactiveUsers(@Param("inactiveThreshold") LocalDateTime inactiveThreshold);
}