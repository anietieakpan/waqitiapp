package com.waqiti.gamification.service;

import com.waqiti.gamification.domain.PointTransaction;
import com.waqiti.gamification.domain.UserPoints;
import com.waqiti.gamification.repository.PointTransactionRepository;
import com.waqiti.gamification.repository.UserPointsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.annotation.Lazy;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserPointsService {
    
    @Lazy
    private final UserPointsService self;
    private final UserPointsRepository userPointsRepository;
    private final PointTransactionRepository pointTransactionRepository;
    private final GamificationEventPublisher eventPublisher;
    
    @Transactional(readOnly = true)
    @Cacheable(value = "userPoints", key = "#userId")
    public Optional<UserPoints> findByUserId(String userId) {
        return userPointsRepository.findByUserId(userId);
    }
    
    @Transactional
    @CacheEvict(value = "userPoints", key = "#userId")
    public UserPoints createUserPoints(String userId) {
        if (userPointsRepository.findByUserId(userId).isPresent()) {
            throw new IllegalStateException("User points already exist for user: " + userId);
        }
        
        UserPoints userPoints = UserPoints.builder()
                .userId(userId)
                .totalPoints(0L)
                .availablePoints(0L)
                .redeemedPoints(0L)
                .currentLevel(UserPoints.Level.BRONZE)
                .levelProgressPoints(0L)
                .nextLevelThreshold(UserPoints.Level.SILVER.getMinPoints())
                .cashbackRate(UserPoints.Level.BRONZE.getCashbackRate())
                .build();
        
        userPoints = userPointsRepository.save(userPoints);
        log.info("Created user points for user: {}", userId);
        
        return userPoints;
    }
    
    @Transactional
    @CacheEvict(value = "userPoints", key = "#userId")
    public UserPoints addPoints(String userId, Long points, String eventType, String description, String referenceId) {
        UserPoints userPoints = getOrCreateUserPoints(userId);
        
        Long balanceBefore = userPoints.getTotalPoints();
        UserPoints.Level levelBefore = userPoints.getCurrentLevel();
        
        // Apply multiplier if active
        Long finalPoints = points;
        if (userPoints.getMultiplierActive() && 
            userPoints.getMultiplierExpiresAt() != null && 
            userPoints.getMultiplierExpiresAt().isAfter(LocalDateTime.now())) {
            finalPoints = (long) (points * userPoints.getCurrentMultiplier().doubleValue());
        }
        
        // Update points
        userPoints.setTotalPoints(userPoints.getTotalPoints() + finalPoints);
        userPoints.setAvailablePoints(userPoints.getAvailablePoints() + finalPoints);
        userPoints.setDailyPoints(userPoints.getDailyPoints() + finalPoints);
        userPoints.setWeeklyPoints(userPoints.getWeeklyPoints() + finalPoints);
        userPoints.setMonthlyPoints(userPoints.getMonthlyPoints() + finalPoints);
        userPoints.setLastActivityDate(LocalDateTime.now());
        
        // Check for level up
        UserPoints.Level newLevel = UserPoints.Level.fromPoints(userPoints.getTotalPoints());
        if (newLevel != userPoints.getCurrentLevel()) {
            userPoints.setCurrentLevel(newLevel);
            userPoints.setCashbackRate(newLevel.getCashbackRate());
            updateLevelProgress(userPoints);
            
            // Publish level up event
            eventPublisher.publishLevelUpEvent(userId, levelBefore, newLevel, userPoints.getTotalPoints());
        } else {
            updateLevelProgress(userPoints);
        }
        
        userPoints = userPointsRepository.save(userPoints);
        
        // Record transaction
        PointTransaction transaction = PointTransaction.builder()
                .userId(userId)
                .userPoints(userPoints)
                .transactionType(PointTransaction.TransactionType.EARNED)
                .eventType(eventType)
                .pointsAmount(finalPoints)
                .basePoints(points)
                .multiplier(userPoints.getMultiplierActive() ? userPoints.getCurrentMultiplier() : BigDecimal.ONE)
                .description(description)
                .referenceId(referenceId)
                .levelBefore(levelBefore)
                .levelAfter(userPoints.getCurrentLevel())
                .balanceBefore(balanceBefore)
                .balanceAfter(userPoints.getTotalPoints())
                .build();
        
        pointTransactionRepository.save(transaction);
        
        // Publish points earned event
        eventPublisher.publishPointsEarnedEvent(userId, finalPoints, eventType, userPoints.getTotalPoints());
        
        log.info("Added {} points to user {} for event: {}", finalPoints, userId, eventType);
        
        return userPoints;
    }
    
    @Transactional
    @CacheEvict(value = "userPoints", key = "#userId")
    public UserPoints redeemPoints(String userId, Long points, String description, String referenceId) {
        UserPoints userPoints = getOrCreateUserPoints(userId);
        
        if (userPoints.getAvailablePoints() < points) {
            throw new IllegalStateException("Insufficient points for redemption. Available: " + 
                userPoints.getAvailablePoints() + ", Required: " + points);
        }
        
        Long balanceBefore = userPoints.getTotalPoints();
        
        userPoints.setAvailablePoints(userPoints.getAvailablePoints() - points);
        userPoints.setRedeemedPoints(userPoints.getRedeemedPoints() + points);
        userPoints.setLastActivityDate(LocalDateTime.now());
        
        userPoints = userPointsRepository.save(userPoints);
        
        // Record transaction
        PointTransaction transaction = PointTransaction.builder()
                .userId(userId)
                .userPoints(userPoints)
                .transactionType(PointTransaction.TransactionType.REDEEMED)
                .eventType("REDEMPTION")
                .pointsAmount(-points)
                .basePoints(points)
                .description(description)
                .referenceId(referenceId)
                .balanceBefore(balanceBefore)
                .balanceAfter(userPoints.getTotalPoints())
                .build();
        
        pointTransactionRepository.save(transaction);
        
        log.info("Redeemed {} points for user {}: {}", points, userId, description);
        
        return userPoints;
    }
    
    @Transactional
    @CacheEvict(value = "userPoints", key = "#userId")
    public UserPoints activateMultiplier(String userId, BigDecimal multiplier, LocalDateTime expiresAt) {
        UserPoints userPoints = getOrCreateUserPoints(userId);
        
        userPoints.setMultiplierActive(true);
        userPoints.setCurrentMultiplier(multiplier);
        userPoints.setMultiplierExpiresAt(expiresAt);
        userPoints.setLastActivityDate(LocalDateTime.now());
        
        userPoints = userPointsRepository.save(userPoints);
        
        log.info("Activated {}x multiplier for user {} until {}", multiplier, userId, expiresAt);
        
        return userPoints;
    }
    
    @Transactional
    public void updateStreak(String userId, Integer streakDays) {
        UserPoints userPoints = getOrCreateUserPoints(userId);
        userPoints.setStreakDays(streakDays);
        userPoints.setLastActivityDate(LocalDateTime.now());
        userPointsRepository.save(userPoints);
    }
    
    @Transactional(readOnly = true)
    public Long getUserRank(String userId) {
        Long rank = userPointsRepository.findRankByUserId(userId);
        return rank != null ? rank + 1 : null; // Convert 0-based to 1-based ranking
    }
    
    @Transactional(readOnly = true)
    public Page<UserPoints> getLeaderboard(Pageable pageable) {
        return userPointsRepository.findAllOrderByTotalPointsDesc(pageable);
    }
    
    @Transactional(readOnly = true)
    public Page<UserPoints> getActiveUsersLeaderboard(LocalDateTime since, Pageable pageable) {
        return userPointsRepository.findActiveUsersOrderByTotalPointsDesc(since, pageable);
    }
    
    @Transactional
    public void resetPeriodicalPoints() {
        LocalDateTime now = LocalDateTime.now();
        
        // Reset daily points (yesterday)
        LocalDateTime yesterdayStart = now.minusDays(1).withHour(0).withMinute(0).withSecond(0);
        int dailyReset = userPointsRepository.resetDailyPoints(yesterdayStart);
        
        // Reset weekly points (last week)
        LocalDateTime weekAgo = now.minusWeeks(1);
        int weeklyReset = userPointsRepository.resetWeeklyPoints(weekAgo);
        
        // Reset monthly points (last month)
        LocalDateTime monthAgo = now.minusMonths(1);
        int monthlyReset = userPointsRepository.resetMonthlyPoints(monthAgo);
        
        log.info("Reset periodical points - Daily: {}, Weekly: {}, Monthly: {}", 
            dailyReset, weeklyReset, monthlyReset);
    }
    
    @Transactional
    public void deactivateExpiredMultipliers() {
        int deactivated = userPointsRepository.deactivateExpiredMultipliers(LocalDateTime.now());
        log.info("Deactivated {} expired multipliers", deactivated);
    }
    
    private UserPoints getOrCreateUserPoints(String userId) {
        return userPointsRepository.findByUserId(userId)
                .orElseGet(() -> self.createUserPoints(userId));
    }
    
    private void updateLevelProgress(UserPoints userPoints) {
        UserPoints.Level currentLevel = userPoints.getCurrentLevel();
        UserPoints.Level nextLevel = getNextLevel(currentLevel);
        
        if (nextLevel != null) {
            userPoints.setNextLevelThreshold(nextLevel.getMinPoints());
            userPoints.setLevelProgressPoints(
                userPoints.getTotalPoints() - currentLevel.getMinPoints());
        } else {
            userPoints.setNextLevelThreshold(null);
            userPoints.setLevelProgressPoints(0L);
        }
    }
    
    private UserPoints.Level getNextLevel(UserPoints.Level currentLevel) {
        UserPoints.Level[] levels = UserPoints.Level.values();
        int currentIndex = currentLevel.ordinal();
        return (currentIndex < levels.length - 1) ? levels[currentIndex + 1] : null;
    }
}