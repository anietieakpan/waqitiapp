package com.waqiti.social.service;

import com.waqiti.social.dto.*;
import com.waqiti.social.entity.*;
import com.waqiti.social.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Implementation for Social Game Service DTO converters and helper methods
 */
@Service
public class SocialGameServiceImpl {
    
    private static final Logger logger = LoggerFactory.getLogger(SocialGameServiceImpl.class);
    
    private final AchievementRepository achievementRepository;
    private final SocialChallengeRepository challengeRepository;
    private final CompetitionRepository competitionRepository;
    private final PaymentDareRepository dareRepository;
    private final NotificationService notificationService;
    private final TransactionRepository transactionRepository;
    
    public SocialGameServiceImpl(AchievementRepository achievementRepository,
                                  SocialChallengeRepository challengeRepository,
                                  CompetitionRepository competitionRepository,
                                  PaymentDareRepository dareRepository,
                                  NotificationService notificationService,
                                  TransactionRepository transactionRepository) {
        this.achievementRepository = achievementRepository;
        this.challengeRepository = challengeRepository;
        this.competitionRepository = competitionRepository;
        this.dareRepository = dareRepository;
        this.notificationService = notificationService;
        this.transactionRepository = transactionRepository;
    }
    
    /**
     * Convert Achievement entity to DTO
     */
    public AchievementDto convertAchievementToDto(Achievement achievement) {
        if (achievement == null) {
            return null;
        }
        
        return AchievementDto.builder()
                .id(achievement.getId())
                .userId(achievement.getUserId())
                .name(achievement.getName())
                .description(achievement.getDescription())
                .type(achievement.getType())
                .icon(achievement.getIcon())
                .badge(achievement.getBadge())
                .points(achievement.getPoints())
                .unlockedAt(achievement.getUnlockedAt())
                .progress(achievement.getProgress())
                .maxProgress(achievement.getMaxProgress())
                .tier(achievement.getTier())
                .category(achievement.getCategory())
                .rarity(achievement.getRarity())
                .metadata(achievement.getMetadata())
                .build();
    }
    
    /**
     * Convert SocialChallenge entity to DTO
     */
    public SocialChallengeDto convertChallengeToDto(SocialChallenge challenge) {
        if (challenge == null) {
            return null;
        }
        
        return SocialChallengeDto.builder()
                .id(challenge.getId())
                .creatorId(challenge.getCreatorId())
                .creatorName(challenge.getCreatorName())
                .title(challenge.getTitle())
                .description(challenge.getDescription())
                .targetAmount(challenge.getTargetAmount())
                .currentAmount(challenge.getCurrentAmount())
                .participantCount(challenge.getParticipants() != null ? challenge.getParticipants().size() : 0)
                .participantIds(challenge.getParticipants())
                .startDate(challenge.getStartDate())
                .endDate(challenge.getEndDate())
                .status(challenge.getStatus())
                .visibility(challenge.getVisibility())
                .rewardType(challenge.getRewardType())
                .rewardValue(challenge.getRewardValue())
                .rules(challenge.getRules())
                .tags(challenge.getTags())
                .winnerId(challenge.getWinnerId())
                .winnerName(challenge.getWinnerName())
                .completedAt(challenge.getCompletedAt())
                .createdAt(challenge.getCreatedAt())
                .updatedAt(challenge.getUpdatedAt())
                .build();
    }
    
    /**
     * Convert Competition entity to DTO
     */
    public CompetitionDto convertCompetitionToDto(Competition competition) {
        if (competition == null) {
            return null;
        }
        
        // Build leaderboard entries
        List<LeaderboardEntry> leaderboard = new ArrayList<>();
        if (competition.getLeaderboard() != null) {
            competition.getLeaderboard().forEach((userId, score) -> {
                leaderboard.add(LeaderboardEntry.builder()
                        .userId(userId)
                        .score(score)
                        .rank(calculateRank(competition.getLeaderboard(), score))
                        .build());
            });
        }
        
        return CompetitionDto.builder()
                .id(competition.getId())
                .name(competition.getName())
                .description(competition.getDescription())
                .type(competition.getType())
                .startDate(competition.getStartDate())
                .endDate(competition.getEndDate())
                .status(competition.getStatus())
                .rules(competition.getRules())
                .prizes(competition.getPrizes())
                .participantCount(competition.getParticipants() != null ? competition.getParticipants().size() : 0)
                .participantIds(competition.getParticipants())
                .leaderboard(leaderboard)
                .totalPrizePool(competition.getTotalPrizePool())
                .entryFee(competition.getEntryFee())
                .maxParticipants(competition.getMaxParticipants())
                .minParticipants(competition.getMinParticipants())
                .visibility(competition.getVisibility())
                .category(competition.getCategory())
                .sponsorId(competition.getSponsorId())
                .sponsorName(competition.getSponsorName())
                .metadata(competition.getMetadata())
                .createdAt(competition.getCreatedAt())
                .updatedAt(competition.getUpdatedAt())
                .build();
    }
    
    /**
     * Convert SocialChallenge to TrendingChallengeDto
     */
    public TrendingChallengeDto convertTrendingChallengeToDto(SocialChallenge challenge) {
        if (challenge == null) {
            return null;
        }
        
        // Calculate trending score based on participants and recency
        double trendingScore = calculateTrendingScore(challenge);
        
        return TrendingChallengeDto.builder()
                .id(challenge.getId())
                .title(challenge.getTitle())
                .description(challenge.getDescription())
                .participantCount(challenge.getParticipants() != null ? challenge.getParticipants().size() : 0)
                .totalAmount(challenge.getCurrentAmount())
                .trendingScore(trendingScore)
                .category(challenge.getCategory())
                .thumbnailUrl(challenge.getThumbnailUrl())
                .creatorName(challenge.getCreatorName())
                .creatorAvatar(challenge.getCreatorAvatar())
                .tags(challenge.getTags())
                .hoursRemaining(calculateHoursRemaining(challenge.getEndDate()))
                .completionPercentage(calculateCompletionPercentage(challenge))
                .topParticipants(getTopParticipants(challenge))
                .isHot(trendingScore > 100)
                .isNew(challenge.getCreatedAt().isAfter(LocalDateTime.now().minusDays(1)))
                .build();
    }
    
    /**
     * Convert PaymentDare entity to DTO
     */
    public PaymentDareDto convertDareToDto(PaymentDare dare) {
        if (dare == null) {
            return null;
        }
        
        return PaymentDareDto.builder()
                .id(dare.getId())
                .challengerId(dare.getChallengerId())
                .challengerName(dare.getChallengerName())
                .targetUserId(dare.getTargetUserId())
                .targetUserName(dare.getTargetUserName())
                .dareDescription(dare.getDareDescription())
                .amount(dare.getAmount())
                .deadline(dare.getDeadline())
                .status(dare.getStatus())
                .visibility(dare.getVisibility())
                .proofRequired(dare.isProofRequired())
                .proofUrl(dare.getProofUrl())
                .proofDescription(dare.getProofDescription())
                .votingEnabled(dare.isVotingEnabled())
                .upvotes(dare.getUpvotes())
                .downvotes(dare.getDownvotes())
                .viewCount(dare.getViewCount())
                .shareCount(dare.getShareCount())
                .category(dare.getCategory())
                .tags(dare.getTags())
                .completedAt(dare.getCompletedAt())
                .rejectedAt(dare.getRejectedAt())
                .rejectionReason(dare.getRejectionReason())
                .paymentId(dare.getPaymentId())
                .createdAt(dare.getCreatedAt())
                .updatedAt(dare.getUpdatedAt())
                .build();
    }
    
    /**
     * Calculate total amount sent by user in a time period
     */
    @Transactional(readOnly = true)
    public BigDecimal calculateTotalSent(UUID userId, LocalDateTime startDate) {
        try {
            BigDecimal total = transactionRepository.sumAmountByUserIdAndDateRange(
                userId, 
                startDate, 
                LocalDateTime.now(),
                "SENT"
            );
            
            return total != null ? total : BigDecimal.ZERO;
        } catch (Exception e) {
            logger.error("Error calculating total sent for user {}", userId, e);
            return BigDecimal.ZERO;
        }
    }
    
    /**
     * Check for streak-based achievements
     */
    @Transactional(readOnly = true)
    public List<Achievement> checkStreakAchievements(UUID userId) {
        List<Achievement> achievements = new ArrayList<>();
        
        try {
            // Check daily payment streak
            int dailyStreak = calculateDailyPaymentStreak(userId);
            
            if (dailyStreak >= 7) {
                achievements.add(createStreakAchievement(userId, "WEEKLY_STREAK", "7-Day Streak", 7, 50));
            }
            if (dailyStreak >= 30) {
                achievements.add(createStreakAchievement(userId, "MONTHLY_STREAK", "30-Day Streak", 30, 200));
            }
            if (dailyStreak >= 100) {
                achievements.add(createStreakAchievement(userId, "CENTURY_STREAK", "100-Day Streak", 100, 1000));
            }
            
            // Check weekly active streak
            int weeklyStreak = calculateWeeklyActiveStreak(userId);
            if (weeklyStreak >= 4) {
                achievements.add(createStreakAchievement(userId, "MONTHLY_ACTIVE", "Active Every Week", weeklyStreak, 100));
            }
            
        } catch (Exception e) {
            logger.error("Error checking streak achievements for user {}", userId, e);
        }
        
        return achievements;
    }
    
    /**
     * Check for special event achievements
     */
    @Transactional(readOnly = true)
    public List<Achievement> checkSpecialEventAchievements(UUID userId) {
        List<Achievement> achievements = new ArrayList<>();
        
        try {
            LocalDateTime now = LocalDateTime.now();
            
            // Holiday achievements
            if (isHolidaySeason(now)) {
                BigDecimal holidaySpending = calculateHolidaySpending(userId);
                if (holidaySpending.compareTo(new BigDecimal("100")) > 0) {
                    achievements.add(createSpecialEventAchievement(userId, "HOLIDAY_SPENDER", "Holiday Spirit", 100));
                }
            }
            
            // Birthday achievement
            if (isUserBirthday(userId, now)) {
                achievements.add(createSpecialEventAchievement(userId, "BIRTHDAY_PAYMENT", "Birthday Celebration", 50));
            }
            
            // Anniversary achievement
            LocalDateTime joinDate = getUserJoinDate(userId);
            if (isAnniversary(joinDate, now)) {
                int years = now.getYear() - joinDate.getYear();
                achievements.add(createSpecialEventAchievement(userId, 
                    "ANNIVERSARY_" + years, 
                    years + " Year Anniversary", 
                    years * 100));
            }
            
            // First of the month achievement
            if (now.getDayOfMonth() == 1) {
                achievements.add(createSpecialEventAchievement(userId, "FIRST_OF_MONTH", "Early Bird", 25));
            }
            
        } catch (Exception e) {
            logger.error("Error checking special event achievements for user {}", userId, e);
        }
        
        return achievements;
    }
    
    /**
     * Notify participants of a challenge update
     */
    @Transactional
    public void notifyParticipants(SocialChallenge challenge) {
        if (challenge == null || challenge.getParticipants() == null) {
            return;
        }
        
        try {
            String message = String.format("Challenge '%s' has been updated. Current progress: %s/%s",
                challenge.getTitle(),
                challenge.getCurrentAmount(),
                challenge.getTargetAmount());
            
            for (UUID participantId : challenge.getParticipants()) {
                notificationService.sendNotification(
                    participantId,
                    "CHALLENGE_UPDATE",
                    challenge.getTitle(),
                    message,
                    challenge.getId().toString()
                );
            }
            
            logger.info("Notified {} participants of challenge {} update", 
                challenge.getParticipants().size(), challenge.getId());
                
        } catch (Exception e) {
            logger.error("Error notifying challenge participants", e);
        }
    }
    
    /**
     * Notify competition participants
     */
    @Transactional
    public void notifyCompetitionParticipants(Competition competition) {
        if (competition == null || competition.getParticipants() == null) {
            return;
        }
        
        try {
            String message = String.format("Competition '%s' update: %s",
                competition.getName(),
                competition.getStatus());
            
            for (UUID participantId : competition.getParticipants()) {
                notificationService.sendNotification(
                    participantId,
                    "COMPETITION_UPDATE",
                    competition.getName(),
                    message,
                    competition.getId().toString()
                );
            }
            
            logger.info("Notified {} participants of competition {} update", 
                competition.getParticipants().size(), competition.getId());
                
        } catch (Exception e) {
            logger.error("Error notifying competition participants", e);
        }
    }
    
    /**
     * Notify dare target
     */
    @Transactional
    public void notifyDareTarget(PaymentDare dare) {
        if (dare == null || dare.getTargetUserId() == null) {
            return;
        }
        
        try {
            String message = String.format("%s has dared you: %s for %s",
                dare.getChallengerName(),
                dare.getDareDescription(),
                dare.getAmount());
            
            notificationService.sendNotification(
                dare.getTargetUserId(),
                "PAYMENT_DARE",
                "New Payment Dare!",
                message,
                dare.getId().toString()
            );
            
            // Also notify challenger of dare creation
            notificationService.sendNotification(
                dare.getChallengerId(),
                "DARE_CREATED",
                "Dare Sent!",
                String.format("Your dare to %s has been sent", dare.getTargetUserName()),
                dare.getId().toString()
            );
            
            logger.info("Notified users about payment dare {}", dare.getId());
                
        } catch (Exception e) {
            logger.error("Error notifying dare participants", e);
        }
    }
    
    // Helper methods
    
    private int calculateRank(java.util.Map<UUID, BigDecimal> leaderboard, BigDecimal score) {
        if (leaderboard == null || score == null) {
            return 0;
        }
        
        long higherScores = leaderboard.values().stream()
            .filter(s -> s.compareTo(score) > 0)
            .count();
            
        return (int) (higherScores + 1);
    }
    
    private double calculateTrendingScore(SocialChallenge challenge) {
        double score = 0;
        
        // Participant count weight
        int participants = challenge.getParticipants() != null ? challenge.getParticipants().size() : 0;
        score += participants * 10;
        
        // Recency weight
        long hoursOld = java.time.Duration.between(challenge.getCreatedAt(), LocalDateTime.now()).toHours();
        if (hoursOld < 24) {
            score += (24 - hoursOld) * 5;
        }
        
        // Progress weight
        if (challenge.getTargetAmount() != null && challenge.getCurrentAmount() != null) {
            double progress = challenge.getCurrentAmount().doubleValue() / challenge.getTargetAmount().doubleValue();
            score += progress * 50;
        }
        
        return score;
    }
    
    private long calculateHoursRemaining(LocalDateTime endDate) {
        if (endDate == null) {
            return 0;
        }
        
        long hours = java.time.Duration.between(LocalDateTime.now(), endDate).toHours();
        return Math.max(0, hours);
    }
    
    private double calculateCompletionPercentage(SocialChallenge challenge) {
        if (challenge.getTargetAmount() == null || challenge.getCurrentAmount() == null ||
            challenge.getTargetAmount().compareTo(BigDecimal.ZERO) == 0) {
            return 0;
        }
        
        double percentage = challenge.getCurrentAmount().doubleValue() / 
                           challenge.getTargetAmount().doubleValue() * 100;
        return Math.min(100, percentage);
    }
    
    private List<ParticipantInfo> getTopParticipants(SocialChallenge challenge) {
        // This would fetch actual participant info from database
        return new ArrayList<>();
    }
    
    private int calculateDailyPaymentStreak(UUID userId) {
        // Query transaction history to calculate consecutive days with payments
        return transactionRepository.calculateDailyStreak(userId);
    }
    
    private int calculateWeeklyActiveStreak(UUID userId) {
        // Query activity history to calculate consecutive weeks with activity
        return transactionRepository.calculateWeeklyStreak(userId);
    }
    
    private Achievement createStreakAchievement(UUID userId, String type, String name, int days, int points) {
        Achievement achievement = new Achievement();
        achievement.setUserId(userId);
        achievement.setType(type);
        achievement.setName(name);
        achievement.setDescription("Achieved " + days + " day streak!");
        achievement.setPoints(points);
        achievement.setUnlockedAt(LocalDateTime.now());
        achievement.setCategory("STREAK");
        achievement.setRarity("RARE");
        return achievement;
    }
    
    private boolean isHolidaySeason(LocalDateTime date) {
        int month = date.getMonthValue();
        int day = date.getDayOfMonth();
        
        // December holidays
        if (month == 12 && day >= 15) return true;
        
        // November (Thanksgiving period)
        if (month == 11 && day >= 20) return true;
        
        // Other holidays can be added
        return false;
    }
    
    private BigDecimal calculateHolidaySpending(UUID userId) {
        LocalDateTime startOfSeason = LocalDateTime.now().minusDays(30);
        return calculateTotalSent(userId, startOfSeason);
    }
    
    private boolean isUserBirthday(UUID userId, LocalDateTime date) {
        // This would check user profile for birthday
        return false; // Placeholder
    }
    
    private LocalDateTime getUserJoinDate(UUID userId) {
        // This would fetch from user service
        return LocalDateTime.now().minusYears(1); // Placeholder
    }
    
    private boolean isAnniversary(LocalDateTime joinDate, LocalDateTime currentDate) {
        return joinDate.getMonth() == currentDate.getMonth() &&
               joinDate.getDayOfMonth() == currentDate.getDayOfMonth();
    }
    
    private Achievement createSpecialEventAchievement(UUID userId, String type, String name, int points) {
        Achievement achievement = new Achievement();
        achievement.setUserId(userId);
        achievement.setType(type);
        achievement.setName(name);
        achievement.setDescription("Special event achievement!");
        achievement.setPoints(points);
        achievement.setUnlockedAt(LocalDateTime.now());
        achievement.setCategory("SPECIAL");
        achievement.setRarity("EPIC");
        return achievement;
    }
}