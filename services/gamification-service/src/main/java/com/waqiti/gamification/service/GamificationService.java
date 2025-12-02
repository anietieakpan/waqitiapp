package com.waqiti.gamification.service;

import com.waqiti.gamification.domain.*;
import com.waqiti.gamification.repository.*;
import com.waqiti.gamification.event.GamificationEventPublisher;
import com.waqiti.gamification.rules.RuleEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;

import java.time.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class GamificationService {
    
    private final UserProgressRepository progressRepository;
    private final AchievementRepository achievementRepository;
    private final BadgeRepository badgeRepository;
    private final ChallengeRepository challengeRepository;
    private final StreakRepository streakRepository;
    private final LeaderboardRepository leaderboardRepository;
    private final RewardRepository rewardRepository;
    private final RuleEngine ruleEngine;
    private final GamificationEventPublisher eventPublisher;
    private final NotificationService notificationService;
    
    // Points configuration
    private static final int POINTS_PER_TRANSACTION = 10;
    private static final int POINTS_PER_REFERRAL = 100;
    private static final int POINTS_PER_STREAK_DAY = 5;
    private static final int BONUS_MULTIPLIER_WEEKEND = 2;
    private static final int BONUS_MULTIPLIER_HOLIDAY = 3;
    
    public CompletableFuture<GamificationResult> processTransaction(TransactionEvent event) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                UserProgress progress = getOrCreateUserProgress(event.getUserId());
                GamificationResult result = new GamificationResult();
                
                // Award base points
                int basePoints = calculateBasePoints(event);
                progress.addPoints(basePoints);
                result.setPointsEarned(basePoints);
                
                // Check and update streaks
                StreakResult streakResult = updateStreak(event.getUserId(), event.getTransactionType());
                result.setStreakUpdate(streakResult);
                
                // Process achievements
                List<Achievement> newAchievements = checkAchievements(progress, event);
                result.setAchievementsUnlocked(newAchievements);
                
                // Process badges
                List<Badge> newBadges = checkBadges(progress, event);
                result.setBadgesEarned(newBadges);
                
                // Check challenges
                List<ChallengeProgress> challengeUpdates = updateChallenges(event.getUserId(), event);
                result.setChallengeProgress(challengeUpdates);
                
                // Update leaderboards
                updateLeaderboards(event.getUserId(), progress);
                
                // Calculate level progression
                LevelProgress levelProgress = calculateLevelProgress(progress);
                result.setLevelProgress(levelProgress);
                
                // Save progress
                progressRepository.save(progress);
                
                // Publish events
                publishGamificationEvents(event.getUserId(), result);
                
                // Send notifications
                sendNotifications(event.getUserId(), result);
                
                return result;
                
            } catch (Exception e) {
                log.error("Error processing gamification for transaction: {}", event, e);
                throw new GamificationException("Failed to process gamification", e);
            }
        });
    }
    
    private int calculateBasePoints(TransactionEvent event) {
        int points = POINTS_PER_TRANSACTION;
        
        // Apply multipliers
        LocalDateTime now = LocalDateTime.now();
        
        // Weekend bonus
        if (now.getDayOfWeek() == DayOfWeek.SATURDAY || now.getDayOfWeek() == DayOfWeek.SUNDAY) {
            points *= BONUS_MULTIPLIER_WEEKEND;
        }
        
        // Holiday bonus
        if (isHoliday(now.toLocalDate())) {
            points *= BONUS_MULTIPLIER_HOLIDAY;
        }
        
        // Transaction amount bonus (1 point per $10)
        BigDecimal amountBonus = event.getAmount().divide(BigDecimal.TEN, 0, RoundingMode.DOWN);
        points += amountBonus.intValue();
        
        // First transaction of the day bonus
        if (isFirstTransactionOfDay(event.getUserId())) {
            points += 20;
        }
        
        return points;
    }
    
    private StreakResult updateStreak(String userId, TransactionType type) {
        Streak streak = streakRepository.findByUserIdAndType(userId, type)
            .orElse(new Streak(userId, type));
        
        LocalDate today = LocalDate.now();
        LocalDate lastActivity = streak.getLastActivityDate();
        
        if (lastActivity == null) {
            // First activity
            streak.setCurrentStreak(1);
            streak.setLastActivityDate(today);
        } else if (lastActivity.equals(today)) {
            // Already processed today
            return new StreakResult(streak.getCurrentStreak(), false, 0);
        } else if (lastActivity.equals(today.minusDays(1))) {
            // Continuing streak
            streak.incrementStreak();
            streak.setLastActivityDate(today);
            
            // Check milestones
            checkStreakMilestones(userId, streak);
        } else {
            // Streak broken
            streak.resetStreak();
            streak.setLastActivityDate(today);
        }
        
        streak.updateLongestStreak();
        streakRepository.save(streak);
        
        int bonusPoints = streak.getCurrentStreak() * POINTS_PER_STREAK_DAY;
        return new StreakResult(streak.getCurrentStreak(), true, bonusPoints);
    }
    
    private List<Achievement> checkAchievements(UserProgress progress, TransactionEvent event) {
        List<Achievement> allAchievements = achievementRepository.findAllActive();
        List<Achievement> newAchievements = new ArrayList<>();
        
        for (Achievement achievement : allAchievements) {
            if (!progress.hasAchievement(achievement.getId())) {
                boolean earned = ruleEngine.evaluateAchievement(achievement, progress, event);
                
                if (earned) {
                    progress.addAchievement(achievement);
                    newAchievements.add(achievement);
                    
                    // Award achievement points
                    progress.addPoints(achievement.getPoints());
                    
                    log.info("User {} earned achievement: {}", progress.getUserId(), achievement.getName());
                }
            }
        }
        
        return newAchievements;
    }
    
    private List<Badge> checkBadges(UserProgress progress, TransactionEvent event) {
        List<Badge> allBadges = badgeRepository.findAllActive();
        List<Badge> newBadges = new ArrayList<>();
        
        for (Badge badge : allBadges) {
            if (!progress.hasBadge(badge.getId())) {
                boolean earned = ruleEngine.evaluateBadge(badge, progress, event);
                
                if (earned) {
                    progress.addBadge(badge);
                    newBadges.add(badge);
                    
                    // Award badge points
                    progress.addPoints(badge.getPoints());
                    
                    // Check for badge collection achievements
                    checkBadgeCollectionAchievements(progress);
                    
                    log.info("User {} earned badge: {}", progress.getUserId(), badge.getName());
                }
            }
        }
        
        return newBadges;
    }
    
    private List<ChallengeProgress> updateChallenges(String userId, TransactionEvent event) {
        List<Challenge> activeChallenges = challengeRepository.findActiveByUserId(userId);
        List<ChallengeProgress> updates = new ArrayList<>();
        
        for (Challenge challenge : activeChallenges) {
            ChallengeProgress progress = challenge.getProgressForUser(userId);
            
            if (progress != null && !progress.isCompleted()) {
                boolean updated = updateChallengeProgress(challenge, progress, event);
                
                if (updated) {
                    updates.add(progress);
                    
                    if (progress.isCompleted()) {
                        // Award completion rewards
                        awardChallengeRewards(userId, challenge);
                        
                        log.info("User {} completed challenge: {}", userId, challenge.getName());
                    }
                }
            }
        }
        
        challengeRepository.saveAll(activeChallenges);
        return updates;
    }
    
    private boolean updateChallengeProgress(Challenge challenge, ChallengeProgress progress, TransactionEvent event) {
        switch (challenge.getType()) {
            case TRANSACTION_COUNT:
                if (matchesCriteria(challenge, event)) {
                    progress.incrementProgress(1);
                    return true;
                }
                break;
                
            case TRANSACTION_AMOUNT:
                if (matchesCriteria(challenge, event)) {
                    progress.incrementProgress(event.getAmount().doubleValue());
                    return true;
                }
                break;
                
            case UNIQUE_RECIPIENTS:
                if (matchesCriteria(challenge, event) && !progress.hasRecipient(event.getRecipientId())) {
                    progress.addRecipient(event.getRecipientId());
                    progress.incrementProgress(1);
                    return true;
                }
                break;
                
            case CONSECUTIVE_DAYS:
                // Handled by streak system
                break;
                
            case SOCIAL_SHARING:
                // Handled by social events
                break;
        }
        
        return false;
    }
    
    @Cacheable(value = "leaderboards", key = "#period + '-' + #limit")
    public Leaderboard getLeaderboard(LeaderboardPeriod period, int limit) {
        LocalDateTime startDate = getLeaderboardStartDate(period);
        
        List<LeaderboardEntry> entries = leaderboardRepository
            .findTopUsersByPeriod(startDate, limit)
            .stream()
            .map(this::createLeaderboardEntry)
            .collect(Collectors.toList());
        
        return Leaderboard.builder()
            .period(period)
            .entries(entries)
            .lastUpdated(LocalDateTime.now())
            .build();
    }
    
    @CacheEvict(value = "leaderboards", allEntries = true)
    private void updateLeaderboards(String userId, UserProgress progress) {
        // Update daily leaderboard
        leaderboardRepository.updateDailyScore(userId, progress.getTotalPoints());
        
        // Update weekly leaderboard
        leaderboardRepository.updateWeeklyScore(userId, progress.getWeeklyPoints());
        
        // Update monthly leaderboard
        leaderboardRepository.updateMonthlyScore(userId, progress.getMonthlyPoints());
        
        // Update all-time leaderboard
        leaderboardRepository.updateAllTimeScore(userId, progress.getTotalPoints());
        
        // Check for leaderboard achievements
        checkLeaderboardAchievements(userId);
    }
    
    public PaymentChallenge createPaymentChallenge(PaymentChallengeRequest request) {
        validateChallengeRequest(request);
        
        PaymentChallenge challenge = PaymentChallenge.builder()
            .id(UUID.randomUUID().toString())
            .challengerId(request.getChallengerId())
            .challengedId(request.getChallengedId())
            .type(request.getType())
            .amount(request.getAmount())
            .currency(request.getCurrency())
            .title(request.getTitle())
            .description(request.getDescription())
            .rules(request.getRules())
            .wagerAmount(request.getWagerAmount())
            .deadline(request.getDeadline())
            .status(ChallengeStatus.PENDING)
            .createdAt(LocalDateTime.now())
            .build();
        
        challengeRepository.save(challenge);
        
        // Notify challenged user
        notificationService.sendChallengeInvite(challenge);
        
        // Publish event
        eventPublisher.publishChallengeCreated(challenge);
        
        return challenge;
    }
    
    public PaymentChallenge acceptChallenge(String challengeId, String userId) {
        PaymentChallenge challenge = challengeRepository.findById(challengeId)
            .orElseThrow(() -> new ChallengeNotFoundException(challengeId));
        
        if (!challenge.getChallengedId().equals(userId)) {
            throw new UnauthorizedException("User not authorized to accept this challenge");
        }
        
        if (challenge.getStatus() != ChallengeStatus.PENDING) {
            throw new InvalidChallengeStateException("Challenge is not in pending state");
        }
        
        challenge.setStatus(ChallengeStatus.ACTIVE);
        challenge.setAcceptedAt(LocalDateTime.now());
        
        // Initialize challenge progress for both users
        initializeChallengeProgress(challenge);
        
        challengeRepository.save(challenge);
        
        // Notify challenger
        notificationService.sendChallengeAccepted(challenge);
        
        // Publish event
        eventPublisher.publishChallengeAccepted(challenge);
        
        return challenge;
    }
    
    public List<SocialAchievement> getSocialAchievements(String userId) {
        UserProgress progress = getUserProgress(userId);
        
        return progress.getAchievements().stream()
            .filter(a -> a.getCategory() == AchievementCategory.SOCIAL)
            .map(this::toSocialAchievement)
            .collect(Collectors.toList());
    }
    
    public PaymentStreak getPaymentStreak(String userId) {
        Streak streak = streakRepository.findByUserIdAndType(userId, TransactionType.PAYMENT)
            .orElse(new Streak(userId, TransactionType.PAYMENT));
        
        return PaymentStreak.builder()
            .userId(userId)
            .currentStreak(streak.getCurrentStreak())
            .longestStreak(streak.getLongestStreak())
            .lastActivityDate(streak.getLastActivityDate())
            .nextMilestone(getNextStreakMilestone(streak.getCurrentStreak()))
            .daysUntilNextReward(calculateDaysUntilNextReward(streak))
            .build();
    }
    
    @Transactional
    public RedemptionResult redeemReward(String userId, String rewardId) {
        UserProgress progress = getUserProgress(userId);
        Reward reward = rewardRepository.findById(rewardId)
            .orElseThrow(() -> new RewardNotFoundException(rewardId));
        
        if (progress.getTotalPoints() < reward.getPointsCost()) {
            throw new InsufficientPointsException(
                String.format("Need %d points, have %d", 
                    reward.getPointsCost(), progress.getTotalPoints())
            );
        }
        
        if (!reward.isAvailable()) {
            throw new RewardNotAvailableException("Reward is no longer available");
        }
        
        // Deduct points
        progress.deductPoints(reward.getPointsCost());
        
        // Create redemption record
        RewardRedemption redemption = RewardRedemption.builder()
            .id(UUID.randomUUID().toString())
            .userId(userId)
            .rewardId(rewardId)
            .pointsSpent(reward.getPointsCost())
            .status(RedemptionStatus.PROCESSING)
            .redeemedAt(LocalDateTime.now())
            .build();
        
        // Process reward based on type
        processRewardRedemption(redemption, reward);
        
        progressRepository.save(progress);
        rewardRepository.save(reward);
        
        return RedemptionResult.builder()
            .redemptionId(redemption.getId())
            .reward(reward)
            .newPointsBalance(progress.getTotalPoints())
            .estimatedDelivery(calculateDeliveryTime(reward))
            .build();
    }
    
    private void processRewardRedemption(RewardRedemption redemption, Reward reward) {
        switch (reward.getType()) {
            case CASHBACK:
                processCashbackReward(redemption, reward);
                break;
            case DISCOUNT:
                processDiscountReward(redemption, reward);
                break;
            case GIFT_CARD:
                processGiftCardReward(redemption, reward);
                break;
            case FEATURE_UNLOCK:
                processFeatureUnlock(redemption, reward);
                break;
            case PHYSICAL_ITEM:
                processPhysicalReward(redemption, reward);
                break;
        }
    }
    
    private UserProgress getOrCreateUserProgress(String userId) {
        return progressRepository.findByUserId(userId)
            .orElseGet(() -> {
                UserProgress progress = new UserProgress();
                progress.setUserId(userId);
                progress.setLevel(1);
                progress.setTotalPoints(0);
                progress.setCreatedAt(LocalDateTime.now());
                return progressRepository.save(progress);
            });
    }
    
    @Cacheable(value = "userProgress", key = "#userId")
    public UserProgress getUserProgress(String userId) {
        return progressRepository.findByUserId(userId)
            .orElseThrow(() -> new UserNotFoundException(userId));
    }
    
    private LevelProgress calculateLevelProgress(UserProgress progress) {
        int currentLevel = progress.getLevel();
        int currentLevelPoints = getPointsRequiredForLevel(currentLevel);
        int nextLevelPoints = getPointsRequiredForLevel(currentLevel + 1);
        int progressPoints = progress.getTotalPoints() - currentLevelPoints;
        int pointsNeeded = nextLevelPoints - currentLevelPoints;
        
        double progressPercentage = (double) progressPoints / pointsNeeded * 100;
        
        // Check for level up
        if (progress.getTotalPoints() >= nextLevelPoints) {
            progress.setLevel(currentLevel + 1);
            
            // Award level up rewards
            awardLevelUpRewards(progress.getUserId(), currentLevel + 1);
            
            // Recalculate progress for new level
            return calculateLevelProgress(progress);
        }
        
        return LevelProgress.builder()
            .currentLevel(currentLevel)
            .nextLevel(currentLevel + 1)
            .currentPoints(progress.getTotalPoints())
            .pointsToNextLevel(nextLevelPoints - progress.getTotalPoints())
            .progressPercentage(progressPercentage)
            .build();
    }
    
    private int getPointsRequiredForLevel(int level) {
        // Exponential growth formula: 100 * (level^1.5)
        return (int) (100 * Math.pow(level, 1.5));
    }
    
    private void publishGamificationEvents(String userId, GamificationResult result) {
        if (!result.getAchievementsUnlocked().isEmpty()) {
            eventPublisher.publishAchievementsUnlocked(userId, result.getAchievementsUnlocked());
        }
        
        if (!result.getBadgesEarned().isEmpty()) {
            eventPublisher.publishBadgesEarned(userId, result.getBadgesEarned());
        }
        
        if (result.getLevelProgress() != null && result.getLevelProgress().isLeveledUp()) {
            eventPublisher.publishLevelUp(userId, result.getLevelProgress());
        }
        
        if (result.getStreakUpdate() != null && result.getStreakUpdate().isUpdated()) {
            eventPublisher.publishStreakUpdate(userId, result.getStreakUpdate());
        }
    }
    
    private void sendNotifications(String userId, GamificationResult result) {
        // Achievement notifications
        for (Achievement achievement : result.getAchievementsUnlocked()) {
            notificationService.sendAchievementUnlocked(userId, achievement);
        }
        
        // Badge notifications
        for (Badge badge : result.getBadgesEarned()) {
            notificationService.sendBadgeEarned(userId, badge);
        }
        
        // Level up notification
        if (result.getLevelProgress() != null && result.getLevelProgress().isLeveledUp()) {
            notificationService.sendLevelUp(userId, result.getLevelProgress());
        }
        
        // Streak milestone notifications
        if (result.getStreakUpdate() != null) {
            checkStreakNotifications(userId, result.getStreakUpdate());
        }
    }
    
    private boolean isHoliday(LocalDate date) {
        // Simple holiday check - expand based on requirements
        return date.getMonthValue() == 12 && date.getDayOfMonth() == 25 || // Christmas
               date.getMonthValue() == 1 && date.getDayOfMonth() == 1 ||   // New Year
               date.getMonthValue() == 7 && date.getDayOfMonth() == 4;     // Independence Day
    }
    
    private boolean isFirstTransactionOfDay(String userId) {
        LocalDate today = LocalDate.now();
        return !progressRepository.hasTransactionToday(userId, today);
    }
}